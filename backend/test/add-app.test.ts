import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { newId } from "@/lib/uuid";
import { dayInZone } from "@/lib/time";

const DATABASE_URL = process.env.DATABASE_URL;
process.env.BETTER_AUTH_SECRET ??= "test-secret-test-secret-test-secret-1234";
process.env.BETTER_AUTH_URL ??= "http://localhost:3001";

/**
 * Adding an app to a challenge that is already running.
 *
 * The rules the founder set: an app can be added and nothing else about the
 * snapshot changes -- no app leaves, no limit moves. The new app counts from
 * the moment it is added, against the whole of today. The witnesses are not
 * told; they see one more app in the summary. And the day it came in is
 * written down, so nothing looks back at the week and judges Tuesday by a
 * limit that only existed on Thursday.
 */
describe.skipIf(!DATABASE_URL)("adding an app to a running challenge", async () => {
  const { db } = await import("@/server/db/client");
  const { auth } = await import("@/server/auth");
  const { registerDevice } = await import("@/server/devices");
  const { addAppToPact, createPact, getCurrentPact } = await import("@/server/pacts");
  const { upsertDailySummary } = await import("@/server/summary");
  const { progressFor } = await import("@/server/progress");
  const { createInvite, acceptInvite } = await import("@/server/witnesses");
  const { listInbox } = await import("@/server/inbox");
  const { recordDeviceEvent } = await import("@/server/events");

  const instagram = { package: "com.instagram.android", label: "Instagram", daily_limit_min: 30 };
  const tiktok = { package: "com.zhiliaoapp.musically", label: "TikTok", daily_limit_min: 20 };
  const zone = "Asia/Dhaka";

  const friend = newId();
  let userId = "";
  let deviceId = "";
  let pactId = "";

  beforeAll(async () => {
    const res = await auth.api.signUpEmail({
      body: { email: `${newId()}@test.local`, password: "correct horse battery", name: "Adder" },
    });
    userId = res.user.id;
    const now = new Date();
    await db
      .insertInto("user")
      .values({ id: friend, name: "Friend", email: `${friend}@test.local`, emailVerified: false, createdAt: now, updatedAt: now })
      .execute();
    deviceId = (await registerDevice(userId, { install_id: "adder-phone", app_version: "1.0.0" })).id;
    pactId = (
      await createPact(userId, {
        device_id: deviceId,
        duration_days: 14,
        timezone: zone,
        snapshot: { apps: [instagram], reset_time: "00:00", activities: {} },
      })
    ).id;
    const invite = await createInvite(userId, { relationship: "friend" });
    await acceptInvite(friend, invite.invite_code);
  });

  afterAll(async () => {
    await db.deleteFrom("user").where("id", "in", [userId, friend]).execute();
    await db.destroy();
  });

  it("appends the app, stamped with today in the pact's zone, and leaves the rest alone", async () => {
    const before = (await listInbox(friend, undefined, 50)).items.length;

    const pact = await addAppToPact(userId, pactId, tiktok);

    expect(pact.snapshot.apps).toEqual([
      instagram,
      { ...tiktok, added_on: dayInZone(new Date(), zone) },
    ]);
    expect(pact.snapshot.reset_time).toBe("00:00");
    // The same answer GET /pacts/current gives, so the phone can adopt it whole.
    expect(pact.today.day).toBe(dayInZone(new Date(), zone));
    expect((await getCurrentPact(userId))?.snapshot.apps).toHaveLength(2);

    // Nobody is told. Tightening the promise is not news to its witnesses.
    expect((await listInbox(friend, undefined, 50)).items.length).toBe(before);
  });

  it("refuses the same app twice", async () => {
    await expect(addAppToPact(userId, pactId, { ...tiktok, daily_limit_min: 5 })).rejects.toMatchObject({
      status: 409,
      code: "app_already_in_pact",
    });
    expect((await getCurrentPact(userId))?.snapshot.apps).toHaveLength(2);
  });

  it("counts the new app from today: the phone may report it and it is one of the day's apps", async () => {
    const today = dayInZone(new Date(), zone);
    // Before the add this was 409 app_not_in_pact (summary.ts). Fifty minutes
    // against a twenty-minute limit: the app locks, and that is all.
    await upsertDailySummary(userId, pactId, {
      day: today,
      apps: [
        { package: instagram.package, minutes_used: 10, limit_min: 30, earned_min: 0 },
        { package: tiktok.package, minutes_used: 50, limit_min: 20, earned_min: 0 },
      ],
    });
    const progress = await progressFor(userId);
    expect(progress.current?.apps.map((a) => a.package)).toEqual([instagram.package, tiktok.package]);
    expect(progress.current?.apps_within_limits_today).toEqual({ within: 1, total: 2 });
    // The limit the ledger keeps is the snapshot's, not the body's.
    const row = await db
      .selectFrom("daily_summary")
      .select("limit_min")
      .where("pact_id", "=", pactId)
      .where("app_package", "=", tiktok.package)
      .executeTakeFirstOrThrow();
    expect(row.limit_min).toBe(20);
  });

  it("is not for somebody else's challenge", async () => {
    await expect(addAppToPact(friend, pactId, tiktok)).rejects.toMatchObject({ status: 404 });
  });

  it("refuses once the challenge is over", async () => {
    await recordDeviceEvent(userId, pactId, {
      id: newId(),
      type: "broken",
      reason: "user_gave_up",
      occurred_at: new Date().toISOString(),
    });
    await expect(
      addAppToPact(userId, pactId, { package: "com.twitter.android", label: "X", daily_limit_min: 25 }),
    ).rejects.toMatchObject({ status: 409, code: "pact_closed" });
  });
});
