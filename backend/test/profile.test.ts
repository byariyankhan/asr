import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { newId } from "@/lib/uuid";

const DATABASE_URL = process.env.DATABASE_URL;

describe.skipIf(!DATABASE_URL)("profile (/me)", async () => {
  const { db } = await import("@/server/db/client");
  const { getMe, updateMe } = await import("@/server/me");
  const { registerDevice } = await import("@/server/devices");

  const userId = newId();

  beforeAll(async () => {
    const now = new Date();
    await db
      .insertInto("user")
      .values({ id: userId, name: "Profile User", email: `${userId}@test.local`, emailVerified: false, createdAt: now, updatedAt: now })
      .execute();
    await registerDevice(userId, { install_id: "install-profile", app_version: "1.0.0" });
  });

  afterAll(async () => {
    await db.deleteFrom("user").where("id", "=", userId).execute();
    await db.destroy();
  });

  it("returns defaults plus the device count", async () => {
    const me = await getMe(userId);
    expect(me).toMatchObject({
      name: "Profile User",
      timezone: "UTC",
      notify_email: true,
      notify_push: true,
      date_of_birth: null,
      country: null,
      gender: null,
      device_count: 1,
    });
  });

  it("stores the About You fields and reads the date back as YYYY-MM-DD", async () => {
    const me = await updateMe(userId, { timezone: "Asia/Dhaka", date_of_birth: "2000-02-29", country: "BD", gender: "male" });
    expect(me.timezone).toBe("Asia/Dhaka");
    expect(me.date_of_birth).toBe("2000-02-29");
    expect(me.country).toBe("BD");
    expect(me.gender).toBe("male");
  });

  it("clears a field with null", async () => {
    const me = await updateMe(userId, { gender: null });
    expect(me.gender).toBeNull();
    expect(me.country).toBe("BD");
  });

  it("is invisible to a user that does not exist", async () => {
    await expect(getMe(newId())).rejects.toMatchObject({ status: 404 });
  });
});
