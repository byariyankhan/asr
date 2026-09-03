import { HttpError, forbidden, notFound } from "@/lib/http";
import { ImageRejected, isJpeg, readJpegInfo, stripJpegMetadata } from "@/lib/jpeg";
import { newId } from "@/lib/uuid";
import { db } from "./db/client";
import { deleteObject, getObject, putObject, r2Config, type R2Config } from "./r2";


/**
 * Profile photos. Optional, stored in a private R2 bucket, and served only
 * through /v1/media to a caller who is either the owner or one of the
 * witnesses they invited.
 *
 * The bucket has no public access and no custom domain, so there is no URL
 * that works without a session. That is the point: an avatar is a person's
 * face, and the app's whole promise is that what leaves the phone is only
 * what the promise needs.
 */

/** 1MB. The client sends a 512px JPEG, which lands around 40-60KB; this is
 *  a ceiling for something unexpected, not a target. */
export const MAX_BYTES = 1_000_000;

/** The client downscales before uploading. Anything larger did not come from
 *  our client, and re-encoding server-side is exactly the native-dependency
 *  problem this project avoided. */
export const MAX_EDGE = 1024;

const tooLarge = (message: string) => new HttpError(413, "image_too_large", message);
const unsupported = (code: string, message: string) => new HttpError(400, code, message);
const notConfigured = () =>
  new HttpError(503, "storage_not_configured", "Photo uploads are not switched on yet.");

/**
 * The object key carries the owner's id.
 *
 * That is deliberate: /v1/media can then decide who may read an object from
 * the key alone, with no lookup to find out whose photo it is. The random
 * second half means a replaced photo gets a new key, so a URL someone kept
 * stops working and caches cannot serve the old face.
 */
function keyFor(userId: string): string {
  return `avatars/${userId}/${newId()}.jpg`;
}

export function ownerOf(key: string): string | null {
  const match = /^avatars\/([0-9a-fA-F-]{36})\/[0-9a-fA-F-]{36}\.jpg$/.exec(key);
  return match?.[1] ?? null;
}

/** What GET /me and the witness views put in `image`. The client prefixes
 *  its own base URL; the server never builds an absolute URL, so moving to
 *  another domain changes nothing stored. */
export function imagePath(key: string | null): string | null {
  return key ? `/v1/media/${key}` : null;
}

/**
 * Whether one person may see another's photo.
 *
 * Deliberately not `canViewUser`, which is the rule for seeing somebody's
 * *progress*: that is gated on `views_progress`, and a witness who turned
 * progress off has not asked to stop seeing the face of the person who
 * invited them. A face is far less than a day-by-day record of their habits.
 *
 * The link counts in both directions. If someone invited you as a witness
 * and you accepted, the two of you know each other by name already, and the
 * My Witnesses screen exists precisely to show those faces back to the
 * person who chose them -- a one-directional rule would leave that screen
 * full of blanks.
 */
export async function canSeeAvatarOf(callerId: string, ownerId: string): Promise<boolean> {
  if (callerId === ownerId) return true;
  const row = await db
    .selectFrom("witness")
    .select("id")
    .where("status", "=", "accepted")
    .where((eb) =>
      eb.or([
        // The caller is a witness of the owner.
        eb.and([eb("user_id", "=", ownerId), eb("witness_user_id", "=", callerId)]),
        // Or the owner is a witness of the caller.
        eb.and([eb("user_id", "=", callerId), eb("witness_user_id", "=", ownerId)]),
      ]),
    )
    .executeTakeFirst();
  return row !== undefined;
}

export async function setAvatar(userId: string, bytes: Buffer) {
  const config = r2Config();
  if (!config) throw notConfigured();

  if (bytes.length === 0) throw unsupported("empty_body", "No image was sent.");
  if (bytes.length > MAX_BYTES) {
    throw tooLarge("That photo is too large. Please choose a smaller one.");
  }
  if (!isJpeg(bytes)) {
    throw unsupported("unsupported_image", "Please send a JPEG photo.");
  }

  let clean: Buffer;
  try {
    const { width, height } = readJpegInfo(bytes);
    if (width > MAX_EDGE || height > MAX_EDGE) {
      throw tooLarge(`That photo is larger than ${MAX_EDGE} pixels on a side.`);
    }
    if (width < 1 || height < 1) throw unsupported("corrupt_image", "That photo is unreadable.");
    clean = stripJpegMetadata(bytes);
  } catch (error) {
    if (error instanceof ImageRejected) throw unsupported(error.reason, error.message);
    throw error;
  }

  const previous = await currentKey(userId);
  const key = keyFor(userId);
  await putObject(config, key, clean, "image/jpeg");

  // Stored after the upload succeeded, never before: a row pointing at an
  // object that is not there renders as a broken image with no way to tell
  // whether the upload or the read failed.
  await db
    .updateTable("user")
    .set({ image: key, updatedAt: new Date() })
    .where("id", "=", userId)
    .where("deleted_at", "is", null)
    .execute();

  // Best effort. An orphan in the bucket costs a fraction of a cent; a
  // failed request after the new photo is already live costs the person
  // their upload.
  if (previous && previous !== key) void discard(config, previous);

  return { image: imagePath(key) };
}

export async function clearAvatar(userId: string) {
  const previous = await currentKey(userId);
  await db
    .updateTable("user")
    .set({ image: null, updatedAt: new Date() })
    .where("id", "=", userId)
    .where("deleted_at", "is", null)
    .execute();
  const config = r2Config();
  if (config && previous) void discard(config, previous);
  return { image: null };
}

/**
 * Reads an object for a caller who is allowed to see it.
 *
 * Three separate checks, and the order matters. The key has to name a real
 * owner, the caller has to be allowed to see that owner, and the key has to
 * be that owner's *current* photo — without the last one, a witness who was
 * removed, or anyone who noted a URL down, keeps a working link to a face
 * that was replaced.
 */
export async function readAvatar(callerId: string, key: string) {
  const owner = ownerOf(key);
  if (!owner) throw notFound("Image");
  if (!(await canSeeAvatarOf(callerId, owner))) throw forbidden();

  const row = await db
    .selectFrom("user")
    .select("image")
    .where("id", "=", owner)
    .where("deleted_at", "is", null)
    .executeTakeFirst();
  if (!row || row.image !== key) throw notFound("Image");

  const config = r2Config();
  if (!config) throw notConfigured();
  const object = await getObject(config, key);
  if (!object) throw notFound("Image");
  return object;
}

/**
 * Called when an account is hard-deleted. The row goes whether or not the
 * bucket cooperates — a photo left behind is a cost, a delete that refuses
 * to complete is a promise broken.
 */
export async function discardAvatarsFor(keys: readonly string[]): Promise<void> {
  const config = r2Config();
  if (!config || keys.length === 0) return;
  await Promise.allSettled(keys.map((key) => deleteObject(config, key)));
}

async function currentKey(userId: string): Promise<string | null> {
  const row = await db
    .selectFrom("user")
    .select("image")
    .where("id", "=", userId)
    .executeTakeFirst();
  return row?.image ?? null;
}

async function discard(config: R2Config, key: string): Promise<void> {
  try {
    await deleteObject(config, key);
  } catch (error) {
    console.error(JSON.stringify({ at: "avatar.discard", key, error: String(error) }));
  }
}
