import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { newId } from "@/lib/uuid";
import { HttpError } from "@/lib/http";

const DATABASE_URL = process.env.DATABASE_URL;

/**
 * Who may look at somebody's face.
 *
 * R2 credentials are deliberately not needed here. Every case below is
 * decided before a byte is fetched, and the 503 that a permitted read ends
 * in is itself the assertion that the decision came out "yes" -- which is
 * stronger than mocking the storage and checking a mock was called.
 */
describe.skipIf(!DATABASE_URL)("avatar visibility", async () => {
  const { db } = await import("@/server/db/client");
  const { imagePath, ownerOf, readAvatar } = await import("@/server/avatar");

  const owner = newId();
  const witness = newId();
  const stranger = newId();
  const removedWitness = newId();
  /** Someone the owner is a witness *for*: the reverse direction. */
  const watchedByOwner = newId();
  /** An accepted witness who turned progress viewing off. */
  const noProgressWitness = newId();
  const key = `avatars/${owner}/${newId()}.jpg`;

  async function user(id: string, name: string, image: string | null = null) {
    const now = new Date();
    await db
      .insertInto("user")
      .values({
        id,
        name,
        email: `${id}@test.local`,
        emailVerified: false,
        image,
        createdAt: now,
        updatedAt: now,
      })
      .execute();
  }

  beforeAll(async () => {
    await user(owner, "Owner", key);
    await user(witness, "Witness");
    await user(stranger, "Stranger");
    await user(removedWitness, "Removed");
    await user(watchedByOwner, "Watched By Owner");
    await user(noProgressWitness, "No Progress");

    await db
      .insertInto("witness")
      .values([
        {
          id: newId(),
          user_id: owner,
          witness_user_id: witness,
          invite_code: newId(),
          status: "accepted",
          views_progress: true,
        },
        {
          id: newId(),
          user_id: owner,
          witness_user_id: removedWitness,
          invite_code: newId(),
          status: "removed",
          views_progress: true,
        },
        {
          id: newId(),
          user_id: watchedByOwner,
          witness_user_id: owner,
          invite_code: newId(),
          status: "accepted",
          views_progress: true,
        },
        {
          id: newId(),
          user_id: owner,
          witness_user_id: noProgressWitness,
          invite_code: newId(),
          status: "accepted",
          views_progress: false,
        },
      ])
      .execute();
  });

  afterAll(async () => {
    await db
      .deleteFrom("user")
      .where("id", "in", [owner, witness, stranger, removedWitness, watchedByOwner, noProgressWitness])
      .execute();
    await db.destroy();
  });

  const status = async (callerId: string, target: string): Promise<number> => {
    try {
      await readAvatar(callerId, target);
      return 200;
    } catch (error) {
      if (error instanceof HttpError) return error.status;
      throw error;
    }
  };

  it("lets the owner through", async () => {
    // 503 = storage not configured, reached only after the checks passed.
    expect(await status(owner, key)).toBe(503);
  });

  it("lets an accepted witness through", async () => {
    expect(await status(witness, key)).toBe(503);
  });

  it("lets a witness through who turned progress viewing off", async () => {
    // views_progress governs seeing somebody's habits day by day. Turning it
    // off is not a request to stop seeing the face of the person who invited
    // you, and gating the avatar on it was the bug this test exists for.
    expect(await status(noProgressWitness, key)).toBe(503);
  });

  it("lets the owner see the photo of someone they are a witness for", async () => {
    // The reverse direction. My Witnesses shows these faces back to the
    // person who chose them; a one-directional rule left it blank.
    const theirKey = `avatars/${watchedByOwner}/${newId()}.jpg`;
    await db.updateTable("user").set({ image: theirKey }).where("id", "=", watchedByOwner).execute();
    expect(await status(owner, theirKey)).toBe(503);
  });

  it("refuses somebody with no connection to the owner", async () => {
    expect(await status(stranger, key)).toBe(403);
  });

  it("refuses a witness who was removed", async () => {
    // The row survives with status 'removed' precisely so this stays false.
    expect(await status(removedWitness, key)).toBe(403);
  });

  it("refuses a key that is no longer the owner's current photo", async () => {
    const stale = `avatars/${owner}/${newId()}.jpg`;
    // A URL somebody kept, or a cache entry, must stop resolving the moment
    // the photo is replaced.
    expect(await status(owner, stale)).toBe(404);
  });

  it("refuses a key that names no owner", async () => {
    expect(await status(owner, "avatars/../../etc/passwd")).toBe(404);
    expect(await status(owner, "avatars/not-a-uuid/x.jpg")).toBe(404);
    expect(await status(owner, "")).toBe(404);
  });

  it("refuses a key belonging to a user who deleted their account", async () => {
    await db
      .updateTable("user")
      .set({ deleted_at: new Date() })
      .where("id", "=", owner)
      .execute();
    try {
      expect(await status(owner, key)).toBe(404);
      expect(await status(witness, key)).toBe(404);
    } finally {
      await db.updateTable("user").set({ deleted_at: null }).where("id", "=", owner).execute();
    }
  });

  it("reads the owner back out of a key, and refuses shapes that are not keys", () => {
    expect(ownerOf(key)).toBe(owner);
    expect(ownerOf("avatars/x/y.jpg")).toBeNull();
    expect(ownerOf(`avatars/${owner}/${newId()}.png`)).toBeNull();
    expect(ownerOf(`../${owner}/x.jpg`)).toBeNull();
  });

  it("hands the client a relative path, so the domain is never stored", () => {
    expect(imagePath(key)).toBe(`/v1/media/${key}`);
    expect(imagePath(null)).toBeNull();
  });
});
