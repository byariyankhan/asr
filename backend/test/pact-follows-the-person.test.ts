import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { newId } from "@/lib/uuid";

const DATABASE_URL = process.env.DATABASE_URL;

/**
 * A challenge belongs to the person, not to the handset they made it on.
 *
 * It used to belong to the install: written to the phone's own storage in one
 * place and read back from nowhere, so a reinstall, a replacement phone or a
 * second phone on the same account all looked exactly like "no challenge".
 * The server still had it, the witnesses were still watching, and nothing was
 * enforcing anything.
 */
describe.skipIf(!DATABASE_URL)("a challenge outliving a phone", async () => {
  const { db } = await import("@/server/db/client");
  const { registerDevice } = await import("@/server/devices");
  const { createPact, getCurrentPact, claimPact } = await import("@/server/pacts");
  const { handleDeadDevice } = await import("@/server/watchdog");
  const { createInvite, acceptInvite } = await import("@/server/witnesses");

  const owner = newId();
  const friend = newId();
  const snapshot = {
    apps: [{ package: "com.instagram.android", label: "Instagram", daily_limit_min: 30 }],
    reset_time: "00:00",
    activities: {},
  };
  let oldPhone = "";

  beforeAll(async () => {
    const now = new Date();
    await db
      .insertInto("user")
      .values(
        [
          { id: owner, name: "Ariyan" },
          { id: friend, name: "Sabbir" },
        ].map((u) => ({ ...u, email: `${u.id}@test.local`, emailVerified: false, createdAt: now, updatedAt: now })),
      )
      .execute();
    oldPhone = (await registerDevice(owner, { install_id: "old-phone", model: "Galaxy A54", app_version: "1.0.0", fcm_token: "tok-old" })).id;
    await createPact(owner, { device_id: oldPhone, duration_days: 30, timezone: "Asia/Dhaka", snapshot });
    const invite = await createInvite(owner, { relationship: "friend" });
    await acceptInvite(friend, invite.invite_code);
  });

  afterAll(async () => {
    await db.deleteFrom("user").where("id", "in", [owner, friend]).execute();
  });

  it("hands back everything a new phone needs to rebuild it", async () => {
    const current = (await getCurrentPact(owner))!;
    // Not just an id. Which apps, what each is allowed, when it started and
    // how long it runs -- without these the phone cannot enforce anything
    // and there is nothing to restore.
    expect(current.snapshot.apps).toEqual([
      { package: "com.instagram.android", label: "Instagram", daily_limit_min: 30 },
    ]);
    expect(current.duration_days).toBe(30);
    expect(current.starts_at).toBeInstanceOf(Date);
    expect(current.timezone).toBe("Asia/Dhaka");
    // And the name of the handset running it. One challenge runs on one
    // phone, so a second one has to be able to say which -- "running on your
    // Galaxy A54" rather than on a uuid nobody recognises.
    expect(current.device_model).toBe("Galaxy A54");
  });

  it("a new phone takes it over, and the old one no longer owns it", async () => {
    const newPhone = (await registerDevice(owner, { install_id: "new-phone", model: "Pixel 8", app_version: "1.0.0", fcm_token: "tok-new" })).id;
    const pact = (await getCurrentPact(owner))!;
    expect(pact.device_id).toBe(oldPhone);

    const claimed = await claimPact(owner, pact.id, newPhone);
    expect(claimed.device_id).toBe(newPhone);
    expect((await getCurrentPact(owner))!.id).toBe(pact.id);
    // The name follows the ownership, so the phone that just let go of it is
    // told where it went rather than being told about itself.
    expect((await getCurrentPact(owner))!.device_model).toBe("Pixel 8");

    // And the people watching are told, because this is the one move that
    // could be an escape: a challenge runs on one phone, so parking it on a
    // handset nobody uses would leave the real one unblocked and reporting
    // nothing. Somebody replacing a broken phone has nothing to hide by it.
    const moved = await db
      .selectFrom("notification")
      .select(["kind", "title", "body"])
      .where("recipient_id", "=", friend)
      .where("kind", "=", "pact_moved")
      .execute();
    expect(moved).toHaveLength(1);
    expect(moved[0].title).toContain("Ariyan");

    // Claiming again from the phone that already owns it says nothing: a
    // heartbeat is not news, and a witness told twice stops reading them.
    await claimPact(owner, pact.id, newPhone);
    expect(
      await db
        .selectFrom("notification")
        .select("id")
        .where("recipient_id", "=", friend)
        .where("kind", "=", "pact_moved")
        .execute(),
    ).toHaveLength(1);

    // Which is what makes the old phone's death mean nothing: it is not the
    // one running this challenge any more.
    expect(await handleDeadDevice(oldPhone, new Date())).toBe(false);
    expect((await getCurrentPact(owner))!.status).toBe("active");
  });

  it("closes the challenge when the phone running it is the one that died", async () => {
    const pact = (await getCurrentPact(owner))!;
    // The phone that owns it now. Deleting the app is not a quiet way out.
    expect(await handleDeadDevice(pact.device_id!, new Date())).toBe(true);
    expect(await getCurrentPact(owner)).toBeUndefined();

    const told = await db
      .selectFrom("notification")
      .select(["kind", "recipient_id"])
      .where("recipient_id", "=", friend)
      .where("kind", "=", "uninstalled")
      .execute();
    expect(told).toHaveLength(1);
  });

  it("refuses a claim on somebody else's challenge, or on one that is over", async () => {
    const stranger = newId();
    const now = new Date();
    await db
      .insertInto("user")
      .values({ id: stranger, name: "Nobody", email: `${stranger}@test.local`, emailVerified: false, createdAt: now, updatedAt: now })
      .execute();
    const theirPhone = (await registerDevice(stranger, { install_id: "stranger-phone", app_version: "1.0.0" })).id;

    const ended = await db.selectFrom("pact").select("id").where("user_id", "=", owner).executeTakeFirstOrThrow();
    await expect(claimPact(stranger, ended.id, theirPhone)).rejects.toMatchObject({ status: 404 });
    // And the owner cannot claim a challenge that has finished.
    const ownerPhone = (await registerDevice(owner, { install_id: "another", app_version: "1.0.0" })).id;
    await expect(claimPact(owner, ended.id, ownerPhone)).rejects.toMatchObject({ status: 409 });

    await db.deleteFrom("user").where("id", "=", stranger).execute();
  });
});
