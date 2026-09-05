import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { newId } from "@/lib/uuid";

const DATABASE_URL = process.env.DATABASE_URL;

/**
 * One account, one phone.
 *
 * A phone can measure its own screen and nothing else's, so two phones
 * signed into one account cannot both enforce a thirty-minute limit -- that
 * is an hour, reported twice, overwriting each other. The newest phone is
 * the phone; the one before it is signed out and the challenge comes across.
 */
describe.skipIf(!DATABASE_URL)("signing in on a new phone", async () => {
  const { db } = await import("@/server/db/client");
  const { registerDevice, recordHeartbeat } = await import("@/server/devices");
  const { createPact, getCurrentPact } = await import("@/server/pacts");
  const { takeOverOnPhone } = await import("@/server/one-device");
  const { reportUnprotectedHandovers, PROTECTION_GRACE_MS } = await import("@/server/watchdog");
  const { createInvite, acceptInvite } = await import("@/server/witnesses");

  const owner = newId();
  const friend = newId();
  const snapshot = {
    apps: [{ package: "com.instagram.android", label: "Instagram", daily_limit_min: 30 }],
    reset_time: "00:00",
    activities: {},
  };
  let oldPhone = "";
  let newPhone = "";
  const oldSession = newId();
  const newSession = newId();
  const pushed: { token: string; kind: string | undefined }[] = [];

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
    const expires = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000);
    await db
      .insertInto("session")
      .values(
        [oldSession, newSession].map((id) => ({
          id,
          userId: owner,
          token: `token-${id}`,
          expiresAt: expires,
          createdAt: now,
          updatedAt: now,
          ipAddress: null,
          userAgent: null,
        })),
      )
      .execute();

    oldPhone = (
      await registerDevice(owner, { install_id: "old", model: "Galaxy A54", app_version: "1.0.0", fcm_token: "tok-old" })
    ).id;
    await createPact(owner, { device_id: oldPhone, duration_days: 30, timezone: "Asia/Dhaka", snapshot });
    // Protection on, on the phone that made it: the clock below has to start
    // from the handover and not from the challenge.
    await recordHeartbeat(owner, oldPhone, { protection_enabled: true, app_version: "1.0.0" });
    const invite = await createInvite(owner, { relationship: "friend" });
    await acceptInvite(friend, invite.invite_code);
  });

  afterAll(async () => {
    await db.deleteFrom("user").where("id", "in", [owner, friend]).execute();
  });

  it("signs the old phone out, and tells it so while its token still works", async () => {
    const device = await takeOverOnPhone(
      owner,
      newSession,
      { install_id: "new", model: "Pixel 8", app_version: "1.0.0", fcm_token: "tok-new" },
      async (token, message) => {
        pushed.push({ token, kind: message.data?.kind });
        return { ok: true, id: "sent" };
      },
    );
    newPhone = device.id;

    // Told, rather than left to discover it on its next request half an hour
    // from now.
    expect(pushed).toEqual([{ token: "tok-old", kind: "signed_out" }]);

    // And the session is gone, which is the part no app can ignore.
    const sessions = await db.selectFrom("session").select("id").where("userId", "=", owner).execute();
    expect(sessions.map((s) => s.id)).toEqual([newSession]);

    // The old phone is unreachable now: its notifications would be this
    // person's breaches going to a handset that is no longer theirs.
    const old = await db.selectFrom("device").select("fcm_token").where("id", "=", oldPhone).executeTakeFirstOrThrow();
    expect(old.fcm_token).toBeNull();
  });

  it("brings the challenge with it, and starts the clock on an unenforced one", async () => {
    const pact = (await getCurrentPact(owner))!;
    expect(pact.device_id).toBe(newPhone);
    expect(pact.device_model).toBe("Pixel 8");
    // Permissions are per install. Until this phone says otherwise there is
    // a live challenge here that nothing is enforcing.
    expect(pact.protection_pending_since).not.toBeNull();

    // Nothing yet: two hours is two hours.
    expect(await reportUnprotectedHandovers(new Date())).toBe(0);
  });

  it("hands the new phone the day the old one already spent", async () => {
    const { upsertDailySummary } = await import("@/server/summary");
    const { dayInZone } = await import("@/lib/time");
    const pact = (await getCurrentPact(owner))!;
    const day = dayInZone(new Date(), pact.timezone);
    // What the old phone reported before it was signed out.
    await upsertDailySummary(owner, pact.id, {
      day,
      apps: [{ package: "com.instagram.android", minutes_used: 30, limit_min: 30, earned_min: 0 }],
    });

    // Without this the new phone opens on zero -- it can only measure its
    // own screen -- and thirty minutes of Instagram becomes sixty for the
    // cost of signing in. Once per phone, every day.
    const current = (await getCurrentPact(owner))!;
    expect(current.today.day).toBe(day);
    expect(current.today.apps).toEqual([{ package: "com.instagram.android", minutes_used: 30 }]);
  });

  it("stops the clock when the new phone says protection is on", async () => {
    await recordHeartbeat(owner, newPhone, { protection_enabled: true, app_version: "1.0.0" });
    expect((await getCurrentPact(owner))!.protection_pending_since).toBeNull();

    const later = new Date(Date.now() + PROTECTION_GRACE_MS + 60_000);
    expect(await reportUnprotectedHandovers(later)).toBe(0);
  });

  it("tells the witnesses when it is still not on two hours later, and tells them once", async () => {
    // Back to unprotected: the person granted nothing, or took it away again.
    await db
      .updateTable("pact")
      .set({ protection_pending_since: new Date(Date.now() - PROTECTION_GRACE_MS - 60_000) })
      .where("user_id", "=", owner)
      .where("status", "=", "active")
      .execute();

    expect(await reportUnprotectedHandovers(new Date())).toBe(1);
    const told = await db
      .selectFrom("notification")
      .select(["kind", "body"])
      .where("recipient_id", "=", friend)
      .where("kind", "=", "protection_off")
      .execute();
    expect(told).toHaveLength(1);
    expect(told[0]?.body).toContain("nothing is stopping the apps");

    // The challenge is not broken by it. Nobody used anything they agreed
    // not to; the apps were simply not being watched.
    expect((await getCurrentPact(owner))!.status).toBe("active");

    // And it is said once. A witness told every fifteen minutes stops
    // reading anything this app sends.
    expect(await reportUnprotectedHandovers(new Date())).toBe(0);
  });
});
