import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { newId } from "@/lib/uuid";

const DATABASE_URL = process.env.DATABASE_URL;

/**
 * The challenge's calendar follows the phone.
 *
 * The zone it was locked in is written once and judges completion. Every
 * "today" -- which summary rows are today's, the day number a witness sees,
 * the day a summary is accepted for -- is computed in the zone the phone
 * last reported, with its registration, its heartbeat or the summary
 * itself. Dhaka and Honolulu are sixteen hours apart, so at every moment of
 * every day they disagree about the date for some part of it; whichever
 * part this runs in, the phone's own day has to be the one accepted.
 */
describe.skipIf(!DATABASE_URL)("the challenge's calendar follows the phone", async () => {
  const { db } = await import("@/server/db/client");
  const { registerDevice, recordHeartbeat } = await import("@/server/devices");
  const { takeOverOnPhone } = await import("@/server/one-device");
  const { createPact, getCurrentPact } = await import("@/server/pacts");
  const { upsertDailySummary } = await import("@/server/summary");
  const { progressFor } = await import("@/server/progress");
  const { dayInZone, dayNumber } = await import("@/lib/time");

  const dhaka = "Asia/Dhaka";
  const honolulu = "Pacific/Honolulu";
  const instagram = "com.instagram.android";
  const userId = newId();
  let deviceId = "";
  let pactId = "";

  beforeAll(async () => {
    const now = new Date();
    await db
      .insertInto("user")
      .values({ id: userId, name: "Traveller", email: `${userId}@test.local`, emailVerified: false, createdAt: now, updatedAt: now })
      .execute();
    deviceId = (await registerDevice(userId, { install_id: "home-phone", app_version: "1.0.0", timezone: dhaka })).id;
    pactId = (
      await createPact(userId, {
        device_id: deviceId,
        duration_days: 7,
        timezone: dhaka,
        snapshot: { apps: [{ package: instagram, label: "Instagram", daily_limit_min: 30 }], reset_time: "00:00", activities: {} },
      })
    ).id;
  });

  afterAll(async () => {
    await db.deleteFrom("user").where("id", "=", userId).execute();
    await db.destroy();
  });

  it("starts on the zone the challenge was locked in", async () => {
    const pact = await getCurrentPact(userId);
    expect(pact?.phone_timezone).toBeNull();
    expect(pact?.today.day).toBe(dayInZone(new Date(), dhaka));
  });

  it("a heartbeat moves today onto the phone's calendar", async () => {
    await recordHeartbeat(userId, deviceId, { protection_enabled: true, app_version: "1.0.0", timezone: honolulu });
    const pact = await getCurrentPact(userId);
    expect(pact?.phone_timezone).toBe(honolulu);
    expect(pact?.today.day).toBe(dayInZone(new Date(), honolulu));

    // And the day number a witness reads is the phone's.
    const progress = await progressFor(userId);
    expect(progress.current?.day).toBe(dayNumber(pact!.starts_at, 7, honolulu));
  });

  it("a summary stamped with the phone's day is accepted in the zone it names", async () => {
    const day = dayInZone(new Date(), honolulu);
    await upsertDailySummary(userId, pactId, {
      day,
      timezone: honolulu,
      apps: [{ package: instagram, minutes_used: 5, limit_min: 30, earned_min: 0 }],
    });
    const pact = await getCurrentPact(userId);
    expect(pact?.today).toEqual({ day, apps: [{ package: instagram, minutes_used: 5 }] });
  });

  it("a summary from another zone moves the calendar there", async () => {
    const day = dayInZone(new Date(), dhaka);
    await upsertDailySummary(userId, pactId, {
      day,
      timezone: dhaka,
      apps: [{ package: instagram, minutes_used: 7, limit_min: 30, earned_min: 0 }],
    });
    const pact = await getCurrentPact(userId);
    expect(pact?.phone_timezone).toBe(dhaka);
    expect(pact?.today).toEqual({ day, apps: [{ package: instagram, minutes_used: 7 }] });
  });

  it("a new phone brings its zone with the challenge", async () => {
    const newPhone = await takeOverOnPhone(
      userId,
      "session-of-the-new-phone",
      { install_id: "new-phone", app_version: "1.0.0", timezone: honolulu },
      async () => ({ ok: true }) as never,
    );
    const pact = await getCurrentPact(userId);
    expect(pact?.device_id).toBe(newPhone.id);
    expect(pact?.phone_timezone).toBe(honolulu);
  });

  it("the zone the challenge was locked in never moves", async () => {
    expect((await getCurrentPact(userId))?.timezone).toBe(dhaka);
  });
});
