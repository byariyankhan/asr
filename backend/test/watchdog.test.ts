import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { newId } from "@/lib/uuid";
import type { PushResult, PushSender } from "@/server/fcm";

const DATABASE_URL = process.env.DATABASE_URL;

describe.skipIf(!DATABASE_URL)("watchdog", async () => {
  const { db } = await import("@/server/db/client");
  const { registerDevice, recordHeartbeat } = await import("@/server/devices");
  const { createPact, getCurrentPact } = await import("@/server/pacts");
  const { createActivity } = await import("@/server/activities");
  const { createInvite, acceptInvite } = await import("@/server/witnesses");
  const { runWatchdog, markProtectionLost, failExpiredActivities, completeElapsedPacts, deliverNotifications, purgeDeletedAccounts, expireOldRows, REMOVAL_CONFIRM_MS } =
    await import("@/server/watchdog");
  const { reportUnprotectedHandovers, noteUnregistered, WATCHDOG_LOCK_KEY } = await import("@/server/watchdog");
  const { recordRun, recentOutages } = await import("@/server/outages");
  const { redis } = await import("@/server/redis");

  const MIN = 60_000;
  const HOUR = 60 * MIN;
  const DAY = 24 * HOUR;
  const users = {
    silent: newId(),
    walker: newId(),
    finisher: newId(),
    witness: newId(),
    leaver: newId(),
    sleeper: newId(),
    sleeper2: newId(),
    stuck: newId(),
    suspect: newId(),
  };
  const snapshot = {
    apps: [{ package: "com.instagram.android", label: "Instagram", daily_limit_min: 30 }],
    reset_time: "04:00",
    activities: { waiting_period: { wait_min: 10, reward_min: 5, daily_cap_min: 30 } },
  };
  const devices: Record<string, string> = {};

  beforeAll(async () => {
    const now = new Date();
    await db
      .insertInto("user")
      .values(Object.entries(users).map(([k, id]) => ({ id, name: k, email: `${id}@test.local`, emailVerified: false, createdAt: now, updatedAt: now })))
      .execute();
    for (const [k, id] of Object.entries(users)) {
      devices[k] = (await registerDevice(id, { install_id: `${k}-phone`, app_version: "1.0.0", fcm_token: `tok-${k}` })).id;
    }
    // The pacts come first: a witness is invited to a challenge, so there
    // has to be one before anybody can be named.
    await createPact(users.silent, { device_id: devices.silent!, duration_days: 7, timezone: "UTC", snapshot });
    await createPact(users.walker, { device_id: devices.walker!, duration_days: 7, timezone: "UTC", snapshot });
    await createPact(users.finisher, { device_id: devices.finisher!, duration_days: 1, timezone: "UTC", snapshot });
    await createPact(users.sleeper, { device_id: devices.sleeper!, duration_days: 7, timezone: "UTC", snapshot });
    await createPact(users.sleeper2, { device_id: devices.sleeper2!, duration_days: 7, timezone: "UTC", snapshot });
    await createPact(users.stuck, { device_id: devices.stuck!, duration_days: 7, timezone: "UTC", snapshot });

    // everyone but the leaver names `witness` as their witness
    for (const k of ["silent", "walker", "finisher"] as const) {
      const invite = await createInvite(users[k], { relationship: "friend" });
      await acceptInvite(users.witness, invite.invite_code);
    }
  });

  afterAll(async () => {
    await db.deleteFrom("user").where("id", "in", Object.values(users)).execute();
    await db.destroy();
  });

  it("breaks a pact whose device has been silent for a day, once", async () => {
    const pact = (await getCurrentPact(users.silent))!;
    // Age the pact and the heartbeat past the threshold.
    await db.updateTable("pact").set({ starts_at: new Date(Date.now() - 2 * DAY) }).where("id", "=", pact.id).execute();
    await db.updateTable("device").set({ last_heartbeat_at: new Date(Date.now() - 25 * HOUR) }).where("id", "=", devices.silent!).execute();

    expect(await markProtectionLost(new Date())).toBe(1);
    expect(await markProtectionLost(new Date())).toBe(0);

    const row = await db.selectFrom("pact").select("status").where("id", "=", pact.id).executeTakeFirstOrThrow();
    expect(row.status).toBe("broken");
    const events = await db.selectFrom("pact_event").select(["type", "reason", "source"]).where("pact_id", "=", pact.id).orderBy("received_at").execute();
    expect(events).toEqual([
      { type: "started", reason: null, source: "server" },
      { type: "protection_lost", reason: "heartbeat_timeout", source: "server" },
    ]);
    const told = await db.selectFrom("notification").select("kind").where("recipient_id", "=", users.witness).where("about_user_id", "=", users.silent).execute();
    // No "pact_started": witnesses are invited to a challenge that is
    // already running, so by the time anybody accepted, the start had
    // happened and there was nobody to tell.
    expect(told.map((t) => t.kind).sort()).toEqual(["protection_lost"]);
  });

  it("does not touch a pact whose device is alive", async () => {
    await recordHeartbeat(users.walker, devices.walker!, { protection_enabled: true, app_version: "1.0.0" });
    expect(await markProtectionLost(new Date())).toBe(0);
    expect((await getCurrentPact(users.walker))?.status).toBe("active");
  });

  it("fails activities past their deadline and records it", async () => {
    const pact = (await getCurrentPact(users.walker))!;
    const start = new Date(Date.now() - 2 * HOUR);
    const { activity } = await createActivity(users.walker, pact.id, {
      id: newId(),
      type: "waiting_period",
      started_at: start.toISOString(),
      deadline_at: new Date(start.getTime() + HOUR).toISOString(),
    });
    expect(await failExpiredActivities(new Date())).toBe(1);
    expect(await failExpiredActivities(new Date())).toBe(0);
    const row = await db.selectFrom("activity").select("status").where("id", "=", activity.id).executeTakeFirstOrThrow();
    expect(row.status).toBe("failed");
    const ev = await db.selectFrom("pact_event").select("reason").where("pact_id", "=", pact.id).where("type", "=", "activity_failed").execute();
    expect(ev).toEqual([{ reason: "deadline_passed" }]);
  });

  it("completes a pact whose time is up and tells the witness", async () => {
    const pact = (await getCurrentPact(users.finisher))!;
    await db.updateTable("pact").set({ ends_at: new Date(Date.now() - 1000) }).where("id", "=", pact.id).execute();
    await recordHeartbeat(users.finisher, devices.finisher!, { protection_enabled: true, app_version: "1.0.0" });

    expect(await completeElapsedPacts(new Date())).toBe(1);
    const row = await db.selectFrom("pact").select("status").where("id", "=", pact.id).executeTakeFirstOrThrow();
    expect(row.status).toBe("completed");
    const told = await db.selectFrom("notification").select("kind").where("recipient_id", "=", users.witness).where("about_user_id", "=", users.finisher).execute();
    expect(told.map((t) => t.kind).sort()).toEqual(["pact_completed"]);
  });

  it("delivers queued pushes, and takes a dead token during delivery as one answer, not a verdict", async () => {
    const sentTo: string[] = [];
    const push: PushSender = async (token): Promise<PushResult> => {
      sentTo.push(token);
      if (token === "tok-walker") return { ok: false, unregistered: true, error: "messaging/registration-token-not-registered" };
      return { ok: true, id: `msg-${token}` };
    };
    // Something queued for the walker: their own witness reacting is not set up, so queue directly.
    await db
      .insertInto("notification")
      .values({ id: newId(), recipient_id: users.walker, kind: "reaction", channel: "push", title: "t", body: "b", deep_link: null })
      .execute();

    const first = await deliverNotifications(push, new Date());
    expect(first.sent).toBeGreaterThanOrEqual(1);
    expect(sentTo).toContain("tok-witness");
    expect(sentTo).toContain("tok-walker");

    // One not-registered answer is a suspicion and nothing more. This used
    // to close the pact on the spot: a token that had merely rotated told
    // the witnesses that the app had been deleted. The token is kept, the
    // row stays queued for the token the next heartbeat brings, and the
    // challenge is untouched.
    expect(first.uninstalled).toBe(0);
    const suspected = await db
      .selectFrom("device")
      .select(["fcm_token_invalid", "removal_suspected_at"])
      .where("id", "=", devices.walker!)
      .executeTakeFirstOrThrow();
    expect(suspected.fcm_token_invalid).toBe(false);
    expect(suspected.removal_suspected_at).not.toBeNull();
    expect((await getCurrentPact(users.walker))?.status).toBe("active");
    const pending = await db.selectFrom("notification").select("status").where("recipient_id", "=", users.walker).executeTakeFirstOrThrow();
    expect(pending.status).toBe("queued");

    // The same answer two hours later, with no heartbeat in between, is the
    // app being gone -- the rule the probe has always used.
    const later = new Date(suspected.removal_suspected_at!.getTime() + REMOVAL_CONFIRM_MS + 60_000);
    const second = await deliverNotifications(push, later);
    expect(second.uninstalled).toBe(1);

    const walkerDevice = await db.selectFrom("device").select("fcm_token_invalid").where("id", "=", devices.walker!).executeTakeFirstOrThrow();
    expect(walkerDevice.fcm_token_invalid).toBe(true);
    const walkerPact = await db.selectFrom("pact").select("status").where("user_id", "=", users.walker).executeTakeFirstOrThrow();
    expect(walkerPact.status).toBe("broken");
    const ev = await db.selectFrom("pact_event").select("reason").innerJoin("pact", "pact.id", "pact_event.pact_id").where("pact.user_id", "=", users.walker).where("type", "=", "uninstalled").execute();
    expect(ev).toEqual([{ reason: "fcm_unregistered" }]);
    const closed = await db.selectFrom("notification").select("status").where("recipient_id", "=", users.walker).executeTakeFirstOrThrow();
    expect(closed.status).toBe("unregistered");

    // The uninstall was confirmed during this pass, so its witness rows were
    // queued after the loop had already gone by; everything older is sent.
    const statuses = await db.selectFrom("notification").select(["kind", "status"]).where("recipient_id", "=", users.witness).execute();
    expect(statuses.filter((s) => s.status !== "sent").map((s) => s.kind)).toEqual(["uninstalled"]);

    const third = await deliverNotifications(push, later);
    expect(third.uninstalled).toBe(0);
    const after = await db.selectFrom("notification").select("status").where("recipient_id", "=", users.witness).execute();
    expect(after.every((s) => s.status === "sent")).toBe(true);
  });

  it("purges accounts after the grace window and expires old rows", async () => {
    await db.updateTable("user").set({ deleted_at: new Date(Date.now() - 8 * DAY) }).where("id", "=", users.leaver).execute();
    expect(await purgeDeletedAccounts(new Date())).toBe(1);
    expect(await db.selectFrom("user").select("id").where("id", "=", users.leaver).executeTakeFirst()).toBeUndefined();

    await db
      .insertInto("notification")
      .values({ id: newId(), recipient_id: users.witness, kind: "reaction", channel: "push", title: "old", body: "b", created_at: new Date(Date.now() - 100 * DAY) })
      .execute();
    expect(await expireOldRows(new Date())).toBeGreaterThanOrEqual(1);
  });

  it("keeps its own clock, and writes a gap down as an outage", async () => {
    // Far in the future, so nothing else running against this database can
    // be in the way, and so a real-time run afterwards leaves the marker
    // alone instead of reading the difference as an outage.
    const t0 = new Date("2031-01-01T00:00:00Z");
    const at = (ms: number) => new Date(t0.getTime() + ms);
    await recordRun(t0);
    // One tick late is not an outage.
    expect(await recordRun(at(10 * MIN))).toBeNull();
    const outage = await recordRun(at(10 * MIN + 3 * HOUR));
    expect(outage).toEqual({ started_at: at(10 * MIN), ended_at: at(10 * MIN + 3 * HOUR) });
    expect(await recentOutages(at(4 * HOUR))).toContainEqual(outage);
    // A clock put back does not pull the marker into the past.
    expect(await recordRun(at(HOUR))).toBeNull();
    const state = await db.selectFrom("watchdog_state").select("last_run_at").where("id", "=", 1).executeTakeFirstOrThrow();
    expect(state.last_run_at).toEqual(at(10 * MIN + 3 * HOUR));
  });

  it("does not hold a server outage against a phone", async () => {
    const pact = (await getCurrentPact(users.sleeper))!;
    const now = new Date();
    await db.updateTable("pact").set({ starts_at: new Date(now.getTime() - 3 * DAY) }).where("id", "=", pact.id).execute();
    // Silent for 25 hours by the wall clock; the server was away for 20 of them.
    await db.updateTable("device").set({ last_heartbeat_at: new Date(now.getTime() - 25 * HOUR) }).where("id", "=", devices.sleeper!).execute();
    const away = [{ started_at: new Date(now.getTime() - 22 * HOUR), ended_at: new Date(now.getTime() - 2 * HOUR) }];

    await markProtectionLost(now, away);
    expect((await getCurrentPact(users.sleeper))?.status).toBe("active");

    // By the wall clock it is a day. While the server was there to hear it,
    // five hours less the grace after it came back.
    await markProtectionLost(now, []);
    const row = await db.selectFrom("pact").select("status").where("id", "=", pact.id).executeTakeFirstOrThrow();
    expect(row.status).toBe("broken");
  });

  it("does break a pact once the silence adds up to a day of the server being there", async () => {
    const pact = (await getCurrentPact(users.sleeper2))!;
    const now = new Date();
    await db.updateTable("pact").set({ starts_at: new Date(now.getTime() - 3 * DAY) }).where("id", "=", pact.id).execute();
    // Silent 26 hours; the server was away for half an hour of it, which
    // with the 45 minutes after still leaves a day and three quarters.
    await db.updateTable("device").set({ last_heartbeat_at: new Date(now.getTime() - 26 * HOUR) }).where("id", "=", devices.sleeper2!).execute();
    const away = [{ started_at: new Date(now.getTime() - 25 * HOUR), ended_at: new Date(now.getTime() - 24.5 * HOUR) }];

    await markProtectionLost(now, away);
    const row = await db.selectFrom("pact").select("status").where("id", "=", pact.id).executeTakeFirstOrThrow();
    expect(row.status).toBe("broken");
  });

  it("gives a suspicion and a protection clock the same credit", async () => {
    const now = new Date();
    // Both clocks started three hours ago; the server was away for two and a
    // half of them and has been back for twenty minutes.
    const away = [{ started_at: new Date(now.getTime() - 170 * MIN), ended_at: new Date(now.getTime() - 20 * MIN) }];

    await db.updateTable("device").set({ removal_suspected_at: new Date(now.getTime() - 3 * HOUR) }).where("id", "=", devices.suspect!).execute();
    expect(await noteUnregistered(devices.suspect!, now, away)).toBe(false);
    const kept = await db.selectFrom("device").select("fcm_token_invalid").where("id", "=", devices.suspect!).executeTakeFirstOrThrow();
    expect(kept.fcm_token_invalid).toBe(false);
    expect(await noteUnregistered(devices.suspect!, now, [])).toBe(true);

    const pact = (await getCurrentPact(users.stuck))!;
    await db.updateTable("pact").set({ protection_pending_since: new Date(now.getTime() - 3 * HOUR) }).where("id", "=", pact.id).execute();
    await reportUnprotectedHandovers(now, away);
    const waiting = await db.selectFrom("pact").select("protection_pending_since").where("id", "=", pact.id).executeTakeFirstOrThrow();
    expect(waiting.protection_pending_since).not.toBeNull();
    await reportUnprotectedHandovers(now, []);
    const told = await db.selectFrom("pact").select("protection_pending_since").where("id", "=", pact.id).executeTakeFirstOrThrow();
    expect(told.protection_pending_since).toBeNull();
  });

  it("never runs twice at once in one process", async () => {
    const push: PushSender = async () => ({ ok: true, id: "x" });
    const first = runWatchdog({ push });
    expect(await runWatchdog({ push })).toBeNull();
    expect(await first).not.toBeNull();
  });

  describe.skipIf(!process.env.REDIS_URL)("with Redis", () => {
    const push: PushSender = async () => ({ ok: true, id: "x" });

    it("skips when another process holds the lock, and leaves that lock alone", async () => {
      const client = redis()!;
      await client.set(WATCHDOG_LOCK_KEY, "another-process", "PX", 60_000);
      try {
        expect(await runWatchdog({ push })).toBeNull();
        expect(await client.get(WATCHDOG_LOCK_KEY)).toBe("another-process");
      } finally {
        await client.del(WATCHDOG_LOCK_KEY);
      }
    });

    it("releases its own lock when the run is over", async () => {
      expect(await runWatchdog({ push })).not.toBeNull();
      expect(await redis()!.exists(WATCHDOG_LOCK_KEY)).toBe(0);
    });
  });

  it("runs end to end and reports", async () => {
    const report = await runWatchdog({ push: async () => ({ ok: true, id: "x" }) });
    expect(report).toMatchObject({ protection_lost: 0, pacts_completed: 0, activities_failed: 0 });
  });
});
