import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { newId } from "@/lib/uuid";
import type { PushResult } from "@/server/fcm";

const DATABASE_URL = process.env.DATABASE_URL;

/**
 * Telling an uninstall apart from a phone that is simply off.
 *
 * The heartbeat cannot: it stops for an uninstall, for a flat battery and
 * for an afternoon in an office with the phone switched off, and those are
 * not the same thing. That is why the rule built on it waits a whole day --
 * and why a day is long enough to be a strategy.
 *
 * Firebase can. A phone that is off has its message accepted and queued;
 * only an installation Google no longer knows about comes back as
 * not-registered.
 */
describe.skipIf(!DATABASE_URL)("noticing that the app is gone", async () => {
  const { db } = await import("@/server/db/client");
  const { registerDevice, recordHeartbeat } = await import("@/server/devices");
  const { createPact, getCurrentPact } = await import("@/server/pacts");
  const { probeForRemovals, REMOVAL_CONFIRM_MS, PROBE_AFTER_MS } = await import("@/server/watchdog");
  const { createInvite, acceptInvite } = await import("@/server/witnesses");

  const owner = newId();
  const friend = newId();
  const snapshot = {
    apps: [{ package: "com.instagram.android", label: "Instagram", daily_limit_min: 30 }],
    reset_time: "00:00",
    activities: {},
  };
  let phone = "";

  /** Firebase's answer for a phone that is off, or has no data. */
  const queued = async (): Promise<PushResult> => ({ ok: true, id: "queued" });
  /** And for an installation it no longer knows about. */
  const gone = async (): Promise<PushResult> => ({
    ok: false,
    unregistered: true,
    error: "messaging/registration-token-not-registered",
  });

  /** Silent since the last heartbeat, so the probe has reason to ask. */
  const quiet = () => new Date(Date.now() + PROBE_AFTER_MS + 60_000);

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
    await createPact(owner, { device_id: phone, duration_days: 30, timezone: "Asia/Dhaka", snapshot });
    const invite = await createInvite(owner, { relationship: "friend" });
    await acceptInvite(friend, invite.invite_code);
  });

  afterAll(async () => {
    await db.deleteFrom("user").where("id", "in", [owner, friend]).execute();
  });

  const suspicion = async () =>
    (await db.selectFrom("device").select("removal_suspected_at").where("id", "=", phone).executeTakeFirstOrThrow())
      .removal_suspected_at;

  it("leaves a phone that is switched off completely alone", async () => {
    // The office afternoon: phone off, or data off. Nothing has been heard
    // from it, and none of that is evidence of anything.
    expect(await probeForRemovals(queued, quiet())).toBe(0);
    expect(await suspicion()).toBeNull();
    expect((await getCurrentPact(owner))!.status).toBe("active");
  });

  it("does not accuse anybody on the first answer", async () => {
    expect(await probeForRemovals(gone, quiet())).toBe(0);
    expect(await suspicion()).not.toBeNull();
    // Still running. One answer starts a clock, it does not end a challenge.
    expect((await getCurrentPact(owner))!.status).toBe("active");
  });

  it("forgets the suspicion the moment the phone says it is there", async () => {
    await recordHeartbeat(owner, phone, { protection_enabled: true, app_version: "1.0.0" });
    expect(await suspicion()).toBeNull();

    // And a probe that gets through clears it too, for a phone whose app is
    // alive but which had a bad answer earlier.
    await probeForRemovals(gone, quiet());
    expect(await suspicion()).not.toBeNull();
    await probeForRemovals(queued, quiet());
    expect(await suspicion()).toBeNull();
  });

  it("says so on the second answer, two hours later", async () => {
    await probeForRemovals(gone, quiet());
    const started = await suspicion();
    expect(started).not.toBeNull();

    // Not yet: two hours means two hours.
    expect(await probeForRemovals(gone, quiet())).toBe(0);
    expect((await getCurrentPact(owner))!.status).toBe("active");

    const later = new Date(started!.getTime() + REMOVAL_CONFIRM_MS + 60_000);
    expect(await probeForRemovals(gone, later)).toBe(1);

    // The challenge is over, and the people watching are told what happened
    // rather than being left to work it out from silence.
    expect(await getCurrentPact(owner)).toBeUndefined();
    const told = await db
      .selectFrom("notification")
      .select(["kind", "body"])
      .where("recipient_id", "=", friend)
      .where("kind", "=", "uninstalled")
      .execute();
    expect(told).toHaveLength(1);
  });
});
