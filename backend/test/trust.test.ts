import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { newId } from "@/lib/uuid";

const DATABASE_URL = process.env.DATABASE_URL;

/**
 * What the ledger refuses to take a phone's word for.
 *
 * A witness is told things about a person by this server, and every one of
 * them has to be true even when the phone is wrong, offline, or lying. These
 * are the three places it used to take the phone's word: the date a
 * challenge finished, whether anything was enforcing it, and what the limit
 * was.
 */
describe.skipIf(!DATABASE_URL)("what the server does not take the phone's word for", async () => {
  const { db } = await import("@/server/db/client");
  const { registerDevice, recordHeartbeat } = await import("@/server/devices");
  const { createPact, getCurrentPact } = await import("@/server/pacts");
  const { recordDeviceEvent, hasRunItsCourse, COMPLETION_GRACE_MS } = await import("@/server/events");
  const { upsertDailySummary } = await import("@/server/summary");
  const { reportUnprotectedHandovers, PROTECTION_GRACE_MS } = await import("@/server/watchdog");
  const { createInvite, acceptInvite } = await import("@/server/witnesses");
  const { dayInZone } = await import("@/lib/time");

  const HOUR = 3_600_000;
  const DAY = 24 * HOUR;
  const owner = newId();
  const friend = newId();
  const snapshot = {
    apps: [{ package: "com.instagram.android", label: "Instagram", daily_limit_min: 30 }],
    reset_time: "00:00",
    activities: { walk_steps: { target: 2500, reward_min: 10, daily_cap_min: 20 } },
  };
  let phone = "";
  let pactId = "";

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
    phone = (await registerDevice(owner, { install_id: "phone", app_version: "1.0.0", fcm_token: "tok" })).id;
    pactId = (await createPact(owner, { device_id: phone, duration_days: 7, timezone: "Asia/Dhaka", snapshot })).id;
    const invite = await createInvite(owner, { relationship: "friend" });
    await acceptInvite(friend, invite.invite_code);
  });

  afterAll(async () => {
    await db.deleteFrom("user").where("id", "in", [owner, friend]).execute();
  });

  it("follows the phone's own calendar rule for when a challenge can be over", () => {
    // Started at 23:00 on 1 September in Dhaka, seven days: the phone
    // completes it at midnight on the 8th, not seven times 24 hours later.
    const pact = { starts_at: new Date("2026-09-01T17:00:00Z"), duration_days: 7, timezone: "Asia/Dhaka" };
    const midnightOnTheEighth = new Date("2026-09-07T18:00:00Z");
    expect(hasRunItsCourse(pact, midnightOnTheEighth)).toBe(true);
    expect(hasRunItsCourse(pact, new Date(midnightOnTheEighth.getTime() - COMPLETION_GRACE_MS))).toBe(true);
    expect(hasRunItsCourse(pact, new Date(midnightOnTheEighth.getTime() - COMPLETION_GRACE_MS - 60_000))).toBe(false);
    expect(hasRunItsCourse(pact, new Date("2026-09-04T12:00:00Z"))).toBe(false);
  });

  it("refuses a 'completed' from a phone whose date has been moved forward", async () => {
    // Day one. The phone says it is done, because its date says so.
    await expect(
      recordDeviceEvent(owner, pactId, { id: newId(), type: "completed", occurred_at: new Date().toISOString() }),
    ).rejects.toMatchObject({ code: "pact_not_elapsed" });
    expect((await getCurrentPact(owner))!.status).toBe("active");
    const told = await db.selectFrom("notification").select("kind").where("recipient_id", "=", friend).where("kind", "=", "pact_completed").execute();
    expect(told).toHaveLength(0);
  });

  it("accepts it once the day after the last day has actually arrived", async () => {
    const startedAt = new Date(Date.now() - 8 * DAY);
    await db
      .updateTable("pact")
      .set({ starts_at: startedAt, ends_at: new Date(startedAt.getTime() + 7 * DAY) })
      .where("id", "=", pactId)
      .execute();
    const { event, created } = await recordDeviceEvent(owner, pactId, { id: newId(), type: "completed", occurred_at: new Date().toISOString() });
    expect(created).toBe(true);
    expect(event.type).toBe("completed");
    expect(await getCurrentPact(owner)).toBeUndefined();
    const told = await db.selectFrom("notification").select("kind").where("recipient_id", "=", friend).where("kind", "=", "pact_completed").execute();
    expect(told).toHaveLength(1);
  });

  describe("a phone that says its protection is off", async () => {
    let second = "";
    beforeAll(async () => {
      // A fresh challenge for the rest; the first one is completed above.
      second = (await createPact(owner, { device_id: phone, duration_days: 7, timezone: "Asia/Dhaka", snapshot })).id;
      const invite = await createInvite(owner, { relationship: "friend" });
      await acceptInvite(friend, invite.invite_code);
    });

    const pendingSince = async () =>
      (await db.selectFrom("pact").select("protection_pending_since").where("id", "=", second).executeTakeFirstOrThrow())
        .protection_pending_since;

    it("starts the two-hour clock, and a phone that says it is on stops it", async () => {
      await recordHeartbeat(owner, phone, { protection_enabled: true, app_version: "1.0.0" });
      expect(await pendingSince()).toBeNull();

      // The permission was taken away on the phone that already holds the
      // challenge. This used to be a healthy heartbeat with `false` in it
      // that nothing read.
      await recordHeartbeat(owner, phone, { protection_enabled: false, app_version: "1.0.0" });
      const started = await pendingSince();
      expect(started).not.toBeNull();

      // Still off ten minutes later: the clock keeps its start, it is not
      // restarted by every heartbeat.
      await recordHeartbeat(owner, phone, { protection_enabled: false, app_version: "1.0.0" });
      expect((await pendingSince())?.getTime()).toBe(started!.getTime());

      await recordHeartbeat(owner, phone, { protection_enabled: true, app_version: "1.0.0" });
      expect(await pendingSince()).toBeNull();
    });

    it("tells the witnesses in as many words when two hours pass", async () => {
      await recordHeartbeat(owner, phone, { protection_enabled: false, app_version: "1.0.0" });
      expect(await reportUnprotectedHandovers(new Date())).toBe(0);
      const later = new Date(Date.now() + PROTECTION_GRACE_MS + 60_000);
      expect(await reportUnprotectedHandovers(later)).toBe(1);
      const told = await db
        .selectFrom("notification")
        .select(["kind", "body"])
        .where("recipient_id", "=", friend)
        .where("kind", "=", "protection_off")
        .execute();
      expect(told).toHaveLength(1);
      // In the witness's own relationship voice now; every voice says how long.
      expect(told[0]?.body).toMatch(/two hours/);
      expect(told[0]?.body).not.toContain("new phone");
      // Not broken by it: nobody used anything they agreed not to.
      expect((await getCurrentPact(owner))!.status).toBe("active");
    });

    it("keeps the limit that was locked, whatever the phone sends", async () => {
      const pact = (await getCurrentPact(owner))!;
      await upsertDailySummary(owner, pact.id, {
        day: dayInZone(new Date(), pact.timezone),
        apps: [{ package: "com.instagram.android", minutes_used: 200, limit_min: 1440, earned_min: 600 }],
      });
      const row = await db
        .selectFrom("daily_summary")
        .select(["limit_min", "earned_min", "minutes_used"])
        .where("pact_id", "=", pact.id)
        .executeTakeFirstOrThrow();
      expect(row).toEqual({ limit_min: 30, earned_min: 20, minutes_used: 200 });
    });
  });
});
