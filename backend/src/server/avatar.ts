import { HttpError, notFound } from "@/lib/http";
import { ImageRejected, isJpeg, readJpegInfo, stripJpegMetadata } from "@/lib/jpeg";
import { newId } from "@/lib/uuid";
import { db } from "./db/client";
import { deleteObject, getObject, putObject, r2Config, R2Error, type R2Config } from "./r2";


/**
 * Profile photos. Optional, stored in R2 and served through /v1/media to
 * anybody who has the URL.
 *
 * The bucket itself stays private and the object is streamed by the API
 * rather than exposed directly. That is not a privacy measure -- the photo
 * is public -- it is so that nothing stored anywhere is a URL. Only the key
 * is stored, so putting a CDN or a media domain in front of this later
 * changes one function and no data.
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
 * An R2Error is not an HttpError, so route() was turning every storage
 * refusal into a bare 500 "Something went wrong." — which is what a person
 * choosing a photo actually saw, with the real reason left in a log nobody
 * reads. The status R2 gave back is the whole diagnosis: 403 is a token
 * without write permission on the bucket, 404 is a bucket that is not there
 * under that name, 5xx is Cloudflare having a moment.
 *
 * R2's own body is logged and not returned. It names the bucket and the
 * account, which is not something to hand to whoever is holding the phone.
 */
function storageRefused(stage: "upload" | "read", error: R2Error): HttpError {
  console.error(`[r2] ${stage} refused with ${error.status}: ${error.message}`);
  return new HttpError(
    502,
    "storage_refused",
    `Storage refused the ${stage} (${error.status}). This is a server configuration ` +
      "problem, not something you did.",
  );
}

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
  try {
    await putObject(config, key, clean, "image/jpeg");
  } catch (error) {
    if (error instanceof R2Error) throw storageRefused("upload", error);
    throw error;
  }

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
 * Reads an object by key. No caller: a profile photo is public, because the
 * invite preview has to show a face to somebody who has no account yet.
 *
 * The two checks that remain are the ones that make a photo removable. The
 * key must be the owner's current one, so replacing a photo kills the old
 * URL rather than leaving a face somebody took down still being served; and
 * the owner must not be deleted, so an account going away goes dark at once
 * instead of at the next purge.
 */
export async function readAvatar(key: string) {
  const owner = ownerOf(key);
  if (!owner) throw notFound("Image");

  const row = await db
    .selectFrom("user")
    .select("image")
    .where("id", "=", owner)
    .where("deleted_at", "is", null)
    .executeTakeFirst();
  if (!row || row.image !== key) throw notFound("Image");

  const config = r2Config();
  if (!config) throw notConfigured();
  let object;
  try {
    object = await getObject(config, key);
  } catch (error) {
    if (error instanceof R2Error) throw storageRefused("read", error);
    throw error;
  }
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
