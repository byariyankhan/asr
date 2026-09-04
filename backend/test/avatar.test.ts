import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { generateInviteCode } from "@/lib/invite-code";
import { newId } from "@/lib/uuid";
import { HttpError } from "@/lib/http";

const DATABASE_URL = process.env.DATABASE_URL;

/**
 * A profile photo is public, so there is no "who may see this" to test. What
 * matters instead is that a photo can be taken away: replacing it, or
 * deleting the account, has to stop the URL resolving straight away.
 *
 * R2 credentials are deliberately not needed. Every case below is decided
 * before a byte is fetched, and the 503 a resolvable key ends in is itself
 * the assertion that it got that far.
 */
describe.skipIf(!DATABASE_URL)("avatar keys", async () => {
  const { db } = await import("@/server/db/client");
  const { imagePath, ownerOf, readAvatar } = await import("@/server/avatar");
  const { peekInvite } = await import("@/server/witnesses");
  const { registerDevice } = await import("@/server/devices");
  const { createPact, getCurrentPact } = await import("@/server/pacts");

  const owner = newId();
  const inviteCode = generateInviteCode();
  const key = `avatars/${owner}/${newId()}.jpg`;

  beforeAll(async () => {
    const now = new Date();
    await db
      .insertInto("user")
      .values({
        id: owner,
        name: "Owner",
        email: `${owner}@test.local`,
        emailVerified: false,
        image: key,
        createdAt: now,
        updatedAt: now,
      })
      .execute();
    // An invitation belongs to a challenge, so there has to be one for the
    // preview to answer about at all.
    const device = (await registerDevice(owner, { install_id: "owner-phone", app_version: "1.0.0" })).id;
    await createPact(owner, {
      device_id: device,
      duration_days: 7,
      timezone: "UTC",
      snapshot: { apps: [{ package: "com.instagram.android", label: "Instagram", daily_limit_min: 30 }], reset_time: "04:00", activities: {} },
    });
    await db
      .insertInto("witness")
      .values({
        id: newId(),
        user_id: owner,
        pact_id: (await getCurrentPact(owner))!.id,
        invite_code: inviteCode,
        invite_email: "friend@test.local",
        status: "invited",
      })
      .execute();
  });

  afterAll(async () => {
    await db.deleteFrom("user").where("id", "=", owner).execute();
    await db.destroy();
  });

  const status = async (target: string): Promise<number> => {
    try {
      await readAvatar(target);
      return 200;
    } catch (error) {
      if (error instanceof HttpError) return error.status;
      throw error;
    }
  };

  it("resolves the owner's current photo for anybody", async () => {
    // 503 = storage not configured, reached only once the key checks passed.
    expect(await status(key)).toBe(503);
  });

  it("stops resolving a key that is no longer the current photo", async () => {
    const stale = `avatars/${owner}/${newId()}.jpg`;
    // A URL somebody kept, or a cache entry, must die when a photo is
    // replaced -- otherwise a face that was taken down is still served.
    expect(await status(stale)).toBe(404);
  });

  it("goes dark the moment the account is deleted", async () => {
    await db.updateTable("user").set({ deleted_at: new Date() }).where("id", "=", owner).execute();
    try {
      expect(await status(key)).toBe(404);
    } finally {
      await db.updateTable("user").set({ deleted_at: null }).where("id", "=", owner).execute();
    }
  });

  it("refuses a key that is not shaped like one", async () => {
    expect(await status("avatars/../../etc/passwd")).toBe(404);
    expect(await status("avatars/not-a-uuid/x.jpg")).toBe(404);
    expect(await status("")).toBe(404);
  });

  it("reads the owner back out of a key", () => {
    expect(ownerOf(key)).toBe(owner);
    expect(ownerOf("avatars/x/y.jpg")).toBeNull();
    expect(ownerOf(`avatars/${owner}/${newId()}.png`)).toBeNull();
    expect(ownerOf(`../${owner}/x.jpg`)).toBeNull();
  });

  it("hands out a relative path, so no domain is ever stored", () => {
    expect(imagePath(key)).toBe(`/v1/media/${key}`);
    expect(imagePath(null)).toBeNull();
  });

  it("shows the inviter's photo on the public invite preview", async () => {
    // The reason the photo is public at all: whoever opens the invite link
    // has no account yet and still needs to see who is asking.
    const preview = await peekInvite(inviteCode);
    expect(preview.inviter_name).toBe("Owner");
    expect(preview.inviter_image).toBe(`/v1/media/${key}`);
  });
});
