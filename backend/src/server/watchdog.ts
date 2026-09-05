import { sql, type Transaction } from "kysely";
import { discardAvatarsFor } from "./avatar";
import { db } from "./db/client";
import type { Database } from "./db/schema";
import { closePact } from "./events";
import { sendPush, type PushSender } from "./fcm";
import { WATCHDOG_LAST_RUN_KEY } from "./health";
import { queueWitnessNotifications } from "./notifications";
import { key, redis } from "./redis";
import { newId } from "@/lib/uuid";

// The 15-minute job. Every step is idempotent and scoped by state, so
// running it twice, or after a crash mid-run, changes nothing the second
// time. Order matters only for delivery, which goes last so the rows the
// earlier steps queue are sent in the same run.

export const HEARTBEAT_TIMEOUT_MS = 24 * 60 * 60 * 1000;
/**
 * How long a challenge may sit on a phone that cannot enforce it.
 *
 * Short, and deliberately far shorter than the day a silent phone gets. A
 * silent phone might be a flat battery; this is a phone that is awake,
 * signed in, holding somebody's challenge, and blocking nothing -- and the
 * person is looking at the screen that asks them to fix it. Two hours is
 * long enough to be at work and short enough that it cannot be a strategy.
 */
export const PROTECTION_GRACE_MS = 2 * 60 * 60 * 1000;
/**
 * How long a phone must have been quiet before it is worth asking Firebase
 * about it. One missed heartbeat: a phone that checked in twenty minutes ago
 * is plainly there.
 */
export const PROBE_AFTER_MS = 45 * 60 * 1000;

/**
 * How long a suspicion has to stand before it is said out loud.
 *
 * One answer from Firebase is not enough to tell somebody's mother they
 * deleted the app. Tokens rotate, phones get restored from backups, and a
 * wrong accusation is worse than a slow true one. Two hours, a second
 * answer, and not a word from the phone in between -- an app that is really
 * running clears this three times over in that window, because it heartbeats
 * every half hour.
 */
export const REMOVAL_CONFIRM_MS = 2 * 60 * 60 * 1000;

export const DELETION_GRACE_MS = 7 * 24 * 60 * 60 * 1000;
export const NOTIFICATION_RETENTION_MS = 90 * 24 * 60 * 60 * 1000;
export const SUMMARY_RETENTION_MS = 400 * 24 * 60 * 60 * 1000;
const LOCK_KEY = key("watchdog", "lock");
const LOCK_MS = 10 * 60 * 1000;

export type WatchdogReport = {
  protection_lost: number;
  removals_found: number;
  protection_off: number;
  uninstalled: number;
  activities_failed: number;
  pacts_completed: number;
  notifications_sent: number;
  notifications_failed: number;
  accounts_purged: number;
  rows_expired: number;
};

export async function runWatchdog(opts: { push?: PushSender; now?: Date } = {}): Promise<WatchdogReport | null> {
  const client = redis();
  if (client) {
    const got = await client.set(LOCK_KEY, String(process.pid), "PX", LOCK_MS, "NX").catch(() => null);
    if (got !== "OK") return null; // another replica is running it
  }
  try {
    const now = opts.now ?? new Date();
    const report: WatchdogReport = {
      protection_lost: await markProtectionLost(now),
      removals_found: await probeForRemovals(opts.push ?? sendPush, now),
      protection_off: await reportUnprotectedHandovers(now),
      uninstalled: 0,
      activities_failed: await failExpiredActivities(now),
      pacts_completed: await completeElapsedPacts(now),
      notifications_sent: 0,
      notifications_failed: 0,
      accounts_purged: await purgeDeletedAccounts(now),
      rows_expired: await expireOldRows(now),
    };
    const push = opts.push ?? sendPush;
    const delivered = await deliverNotifications(push, now);
    report.notifications_sent = delivered.sent;
    report.notifications_failed = delivered.failed;
    report.uninstalled = delivered.uninstalled;
    // An uninstall is only discovered while delivering (FCM answers
    // UNREGISTERED), which queues witness rows the loop has already passed.
    // One extra pass sends them now instead of 15 minutes from now. Bounded
    // at one: that pass cannot itself discover a new dead token, because the
    // devices it would use were just marked invalid.
    if (delivered.uninstalled > 0) {
      const second = await deliverNotifications(push, now);
      report.notifications_sent += second.sent;
      report.notifications_failed += second.failed;
    }
    if (client) await client.set(WATCHDOG_LAST_RUN_KEY, String(Date.now())).catch(() => {});
    return report;
  } finally {
    if (client) await client.del(LOCK_KEY).catch(() => {});
  }
}

/**
 * Asks Firebase whether the phone running a challenge still has the app.
 *
 * The heartbeat cannot answer this. It stops for an uninstall, for a flat
 * battery and for a weekend without signal, and those are not the same
 * thing at all -- which is why the rule built on it waits a full day before
 * saying anything, and why a day is long enough to be a strategy.
 *
 * Firebase can tell them apart, and this is the only reason to ask it. A
 * phone that is off, or has data switched off, has the message accepted and
 * queued for whenever it comes back. Only an installation Google no longer
 * knows about answers not-registered. So somebody sitting in an office with
 * their phone off is never mistaken for somebody who deleted the app -- the
 * two produce different answers, not a slower version of the same one.
 *
 * Not on one answer, though. A token can rotate while the app is offline and
 * a phone can be restored from a backup, so the first not-registered only
 * starts a clock: it takes a second one, [REMOVAL_CONFIRM_MS] later, with no
 * heartbeat in between. A running app clears the suspicion three times over
 * in that window.
 *
 * The message is silent -- data only, no notification block -- because
 * nobody should get an empty line in their shade every half hour for an
 * internal check.
 */
export async function probeForRemovals(push: PushSender, now: Date): Promise<number> {
  const quiet = new Date(now.getTime() - PROBE_AFTER_MS);
  const candidates = await db
    .selectFrom("pact")
    .innerJoin("device", "device.id", "pact.device_id")
    .select([
      "device.id as device_id",
      "device.fcm_token",
      "device.removal_suspected_at",
    ])
    .where("pact.status", "=", "active")
    .where("device.fcm_token", "is not", null)
    .where("device.fcm_token_invalid", "=", false)
    .where((eb) =>
      eb.or([eb("device.last_heartbeat_at", "is", null), eb("device.last_heartbeat_at", "<", quiet)]),
    )
    .execute();

  let found = 0;
  for (const device of candidates) {
    const result = await push(device.fcm_token!, {
      title: "",
      body: "",
      silent: true,
      data: { kind: "ping" },
    });

    if (!(result.ok === false && result.unregistered)) {
      // Accepted, or failed for any other reason. Either way Firebase still
      // knows this installation, so nothing here is evidence of anything.
      if (device.removal_suspected_at) {
        await db
          .updateTable("device")
          .set({ removal_suspected_at: null, updated_at: now })
          .where("id", "=", device.device_id)
          .execute();
      }
      continue;
    }

    if (!device.removal_suspected_at) {
      await db
        .updateTable("device")
        .set({ removal_suspected_at: now, updated_at: now })
        .where("id", "=", device.device_id)
        .execute();
      continue;
    }
    if (now.getTime() - device.removal_suspected_at.getTime() < REMOVAL_CONFIRM_MS) continue;

    // Twice, two hours apart, and the phone has said nothing at all in
    // between. That is an app that is gone.
    await db
      .updateTable("device")
      .set({ fcm_token_invalid: true, updated_at: now })
      .where("id", "=", device.device_id)
      .execute();
    if (await handleDeadDevice(device.device_id, now)) found += 1;
  }
  return found;
}

/**
 * A challenge that changed phones and was never switched back on.
 *
 * Moving to a new phone takes the challenge with it, and takes none of the
 * permissions: usage access and drawing over other apps are granted per
 * install. Between signing in and granting them there is a live challenge
 * that nothing enforces, and from the outside that looks exactly like a
 * perfect day -- no breaches, because nothing is watching.
 *
 * The app will not let anybody past that screen, so this is for the person
 * who signs in and puts the phone down. Two hours, then the witnesses are
 * told in as many words: the challenge is running and nothing is stopping
 * the apps.
 *
 * The pact is not closed. It is not broken -- nobody has used anything they
 * agreed not to -- and the person who grants the permission at hour three
 * should find their challenge where they left it. The clock stops the moment
 * a heartbeat says protection is on.
 */
export async function reportUnprotectedHandovers(now: Date): Promise<number> {
  const cutoff = new Date(now.getTime() - PROTECTION_GRACE_MS);
  const stuck = await db
    .selectFrom("pact")
    .select(["id", "user_id", "device_id"])
    .where("status", "=", "active")
    .where("protection_pending_since", "is not", null)
    .where("protection_pending_since", "<", cutoff)
    .execute();

  let count = 0;
  for (const pact of stuck) {
    await db.transaction().execute(async (trx) => {
      const eventId = newId();
      await trx
        .insertInto("pact_event")
        .values({
          id: eventId,
          pact_id: pact.id,
          device_id: pact.device_id,
          type: "protection_lost",
          reason: "permission_revoked",
          app_package: null,
          minutes: null,
          occurred_at: now,
          source: "server",
        })
        .execute();
      await queueWitnessNotifications(trx, {
        userId: pact.user_id,
        eventId,
        kind: "protection_off",
        pactId: pact.id,
      });
      // Said once. The column is the "we have not told anybody yet" flag as
      // much as it is the clock, and a witness told every fifteen minutes
      // stops reading anything this app sends.
      await trx
        .updateTable("pact")
        .set({ protection_pending_since: null, updated_at: now })
        .where("id", "=", pact.id)
        .execute();
    });
    count += 1;
  }
  return count;
}

// An active pact whose device has been silent for a day: protection was
// turned off, the phone is dead, or the app is gone. V1 rule: that breaks
// the pact. Devices that never heartbeated count from the pact's start.
export async function markProtectionLost(now: Date): Promise<number> {
  const cutoff = new Date(now.getTime() - HEARTBEAT_TIMEOUT_MS);
  const silent = await db
    .selectFrom("pact")
    .leftJoin("device", "device.id", "pact.device_id")
    .select(["pact.id", "pact.user_id", "pact.device_id"])
    .where("pact.status", "=", "active")
    .where("pact.starts_at", "<", cutoff)
    .where((eb) => eb.or([eb("device.last_heartbeat_at", "is", null), eb("device.last_heartbeat_at", "<", cutoff)]))
    .execute();

  let count = 0;
  for (const pact of silent) {
    const done = await db.transaction().execute(async (trx) => {
      const eventId = newId();
      await trx
        .insertInto("pact_event")
        .values({
          id: eventId,
          pact_id: pact.id,
          device_id: pact.device_id,
          type: "protection_lost",
          reason: "heartbeat_timeout",
          app_package: null,
          minutes: null,
          occurred_at: now,
          source: "server",
        })
        .execute();
      try {
        await closePact(trx, pact.id, "broken");
      } catch {
        return false; // closed by a device event between select and now
      }
      await queueWitnessNotifications(trx, { userId: pact.user_id, eventId, kind: "protection_lost", pactId: pact.id });
      return true;
    });
    if (done) count += 1;
  }
  return count;
}

export async function failExpiredActivities(now: Date): Promise<number> {
  const expired = await db
    .selectFrom("activity")
    .innerJoin("pact", "pact.id", "activity.pact_id")
    .select(["activity.id", "activity.pact_id", "pact.device_id", "pact.status as pact_status"])
    .where("activity.status", "=", "pending")
    .where("activity.deadline_at", "<", now)
    .execute();

  let count = 0;
  for (const a of expired) {
    await db.transaction().execute(async (trx) => {
      const updated = await trx
        .updateTable("activity")
        .set({ status: "failed", ended_at: now, updated_at: now })
        .where("id", "=", a.id)
        .where("status", "=", "pending")
        .executeTakeFirst();
      if (updated.numUpdatedRows === 0n) return;
      if (a.pact_status === "active") {
        await trx
          .insertInto("pact_event")
          .values({
            id: newId(),
            pact_id: a.pact_id,
            device_id: a.device_id,
            type: "activity_failed",
            reason: "deadline_passed",
            app_package: null,
            minutes: null,
            occurred_at: now,
            source: "server",
          })
          .execute();
      }
      count += 1;
    });
  }
  return count;
}

export async function completeElapsedPacts(now: Date): Promise<number> {
  const elapsed = await db
    .selectFrom("pact")
    .select(["id", "user_id", "device_id"])
    .where("status", "=", "active")
    .where("ends_at", "<=", now)
    .execute();

  let count = 0;
  for (const pact of elapsed) {
    const done = await db.transaction().execute(async (trx) => {
      const eventId = newId();
      await trx
        .insertInto("pact_event")
        .values({
          id: eventId,
          pact_id: pact.id,
          device_id: pact.device_id,
          type: "completed",
          reason: null,
          app_package: null,
          minutes: null,
          occurred_at: now,
          source: "server",
        })
        .execute();
      try {
        await closePact(trx, pact.id, "completed");
      } catch {
        return false;
      }
      await queueWitnessNotifications(trx, { userId: pact.user_id, eventId, kind: "pact_completed", pactId: pact.id });
      return true;
    });
    if (done) count += 1;
  }
  return count;
}

// Push delivery. A token FCM reports as gone marks the device invalid and,
// if that was the phone running an active pact with no other live device,
// records an uninstall and breaks the pact.
export async function deliverNotifications(push: PushSender, now: Date): Promise<{ sent: number; failed: number; uninstalled: number }> {
  const queued = await db
    .selectFrom("notification")
    .innerJoin("user as u", "u.id", "notification.recipient_id")
    .select(["notification.id", "notification.recipient_id", "notification.title", "notification.body", "notification.deep_link", "notification.kind", "u.notify_push"])
    .where("notification.status", "=", "queued")
    .where("notification.channel", "=", "push")
    .orderBy("notification.created_at")
    .limit(200)
    .execute();

  let sent = 0;
  let failed = 0;
  let uninstalled = 0;
  const deadDevices = new Set<string>();

  for (const n of queued) {
    if (!n.notify_push) {
      await db.updateTable("notification").set({ status: "failed", error: "push_disabled" }).where("id", "=", n.id).execute();
      failed += 1;
      continue;
    }
    const devices = await db
      .selectFrom("device")
      .select(["id", "fcm_token"])
      .where("user_id", "=", n.recipient_id)
      .where("fcm_token", "is not", null)
      .where("fcm_token_invalid", "=", false)
      .execute();
    if (devices.length === 0) {
      await db.updateTable("notification").set({ status: "unregistered", error: "no_device" }).where("id", "=", n.id).execute();
      failed += 1;
      continue;
    }

    let providerId: string | null = null;
    let lastError: string | null = null;
    for (const d of devices) {
      const result = await push(d.fcm_token!, {
        title: n.title,
        body: n.body,
        data: { kind: n.kind, deep_link: n.deep_link ?? "", notification_id: n.id },
      });
      if (result.ok) {
        providerId ??= result.id;
      } else {
        lastError = result.error;
        if (result.unregistered) {
          await db.updateTable("device").set({ fcm_token_invalid: true, updated_at: now }).where("id", "=", d.id).execute();
          deadDevices.add(d.id);
        }
      }
    }
    if (providerId) {
      await db.updateTable("notification").set({ status: "sent", provider_id: providerId, sent_at: now }).where("id", "=", n.id).execute();
      sent += 1;
    } else {
      const allDead = devices.every((d) => deadDevices.has(d.id));
      await db
        .updateTable("notification")
        .set({ status: allDead ? "unregistered" : "failed", error: lastError })
        .where("id", "=", n.id)
        .execute();
      failed += 1;
    }
  }

  for (const deviceId of deadDevices) {
    if (await handleDeadDevice(deviceId, now)) uninstalled += 1;
  }
  return { sent, failed, uninstalled };
}

/**
 * FCM said this device's token is gone. If it was the device running an
 * active pact, the challenge has lost the phone enforcing it.
 *
 * The question is ownership and nothing else. It used to also look for any
 * other device of theirs with a live token and a recent heartbeat, and let
 * that stand as "they are fine" -- which had it exactly backwards, because
 * the device most likely to satisfy that test is the fresh install that
 * replaced the one being reported dead. Delete the app, install it again,
 * and the new copy vouched for the old one: the pact stayed open, nothing
 * was enforcing it, and no witness was told. A two-minute way around the
 * one rule this product has.
 *
 * A phone that takes over a challenge says so -- `claimPact` moves
 * `device_id` -- so ownership is the whole answer. If another phone is
 * really running this challenge, this pact is not the one it owns.
 */
export async function handleDeadDevice(deviceId: string, now: Date): Promise<boolean> {
  const pact = await db
    .selectFrom("pact")
    .select(["id", "user_id"])
    .where("device_id", "=", deviceId)
    .where("status", "=", "active")
    .executeTakeFirst();
  if (!pact) return false;

  return db.transaction().execute(async (trx: Transaction<Database>) => {
    const eventId = newId();
    await trx
      .insertInto("pact_event")
      .values({
        id: eventId,
        pact_id: pact.id,
        device_id: deviceId,
        type: "uninstalled",
        reason: "fcm_unregistered",
        app_package: null,
        minutes: null,
        occurred_at: now,
        source: "server",
      })
      .execute();
    try {
      await closePact(trx, pact.id, "broken");
    } catch {
      return false;
    }
    await queueWitnessNotifications(trx, { userId: pact.user_id, eventId, kind: "uninstalled", pactId: pact.id });
    return true;
  });
}

export async function purgeDeletedAccounts(now: Date): Promise<number> {
  const cutoff = new Date(now.getTime() - DELETION_GRACE_MS);

  // Collected before the rows go, because once the user is deleted there is
  // nothing left that knows which objects were theirs. A photo surviving its
  // owner is not an acceptable outcome of "delete my account".
  const doomed = await db
    .selectFrom("user")
    .select("image")
    .where("deleted_at", "<", cutoff)
    .where("image", "is not", null)
    .execute();

  const result = await db.deleteFrom("user").where("deleted_at", "<", cutoff).executeTakeFirst();

  // After the rows, and never allowed to fail the purge: the database is the
  // record of the promise, and a bucket that is temporarily unreachable must
  // not keep an account alive.
  await discardAvatarsFor(doomed.map((row) => row.image).filter((k): k is string => k !== null));

  return Number(result.numDeletedRows);
}

export async function expireOldRows(now: Date): Promise<number> {
  const n = await db
    .deleteFrom("notification")
    .where("created_at", "<", new Date(now.getTime() - NOTIFICATION_RETENTION_MS))
    .executeTakeFirst();
  const s = await db
    .deleteFrom("daily_summary")
    .where("received_at", "<", new Date(now.getTime() - SUMMARY_RETENTION_MS))
    .executeTakeFirst();
  const d = await db
    .deleteFrom("device")
    .where("fcm_token_invalid", "=", true)
    .where((eb) => eb.or([eb("last_heartbeat_at", "is", null), eb("last_heartbeat_at", "<", new Date(now.getTime() - 180 * 86_400_000))]))
    .where(({ not, exists, selectFrom }) => not(exists(selectFrom("pact").select("id").whereRef("pact.device_id", "=", "device.id").where("pact.status", "=", "active"))))
    .executeTakeFirst();
  return Number(n.numDeletedRows) + Number(s.numDeletedRows) + Number(d.numDeletedRows);
}

// Started once per API process (src/instrumentation.ts). The Redis lock
// keeps two processes from running the same tick.
export function startWatchdogLoop(intervalMs = 15 * 60 * 1000): void {
  const tick = async () => {
    try {
      const report = await runWatchdog();
      if (report) console.info("[watchdog]", JSON.stringify(report));
    } catch (error) {
      console.error("[watchdog] failed:", error);
    }
  };
  setTimeout(tick, 30_000);
  setInterval(tick, intervalMs);
}

// Used by /v1/health to say whether the loop is alive.
export const watchdogStaleAfterMs = 30 * 60 * 1000;
export { sql };
