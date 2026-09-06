import { sql, type Transaction } from "kysely";
import type { Database } from "./db/schema";
import { db, isUniqueViolation } from "./db/client";
import { requireOwnedDevice } from "./devices";
import { queueWitnessNotifications } from "./notifications";
import { canViewPact } from "./witnesses";
import { conflict, notFound } from "@/lib/http";
import { MAX_SNAPSHOT_APPS, type PactAppAdd, type PactCreate, type Snapshot } from "@/lib/schemas";
import { addDays, dayInZone } from "@/lib/time";
import { isUuidLike, newId } from "@/lib/uuid";

export const pactColumns = [
  "id",
  "user_id",
  "device_id",
  "duration_days",
  "timezone",
  "starts_at",
  "ends_at",
  "status",
  "ended_at",
  "snapshot",
  "protection_pending_since",
  "created_at",
  "updated_at",
] as const;

// Lock a pact. The partial unique index pact_one_active_idx is the real
// guard against two active pacts; the resulting 409 is the API's answer.
// Witnesses who opted in hear that it started.
export async function createPact(userId: string, input: PactCreate) {
  await requireOwnedDevice(userId, input.device_id);

  const startsAt = new Date();
  const id = newId();
  try {
    return await db.transaction().execute(async (trx) => {
      const pact = await trx
        .insertInto("pact")
        .values({
          id,
          user_id: userId,
          device_id: input.device_id,
          duration_days: input.duration_days,
          timezone: input.timezone,
          starts_at: startsAt,
          ends_at: addDays(startsAt, input.duration_days),
          snapshot: JSON.stringify(input.snapshot),
        })
        .returning(pactColumns)
        .executeTakeFirstOrThrow();

      const eventId = newId();
      await trx
        .insertInto("pact_event")
        .values({
          id: eventId,
          pact_id: id,
          device_id: input.device_id,
          type: "started",
          reason: null,
          app_package: null,
          minutes: null,
          occurred_at: startsAt,
          source: "server",
        })
        .execute();

      await queueWitnessNotifications(trx, { userId, eventId, kind: "pact_started", pactId: id });
      return pact;
    });
  } catch (error) {
    if (isUniqueViolation(error, "pact_one_active_idx")) {
      throw conflict("pact_active", "You already have an active pact.");
    }
    throw error;
  }
}

/**
 * The active challenge, and which handset is running it.
 *
 * `device_model` is here because the answer a second phone has to give its
 * owner is not "this pact is owned by 0191ab...", it is "your challenge is
 * running on your Galaxy A54". A phone signed into an account it has just
 * signed into cannot know that name any other way -- the row belongs to a
 * device it has never met.
 */
export async function getCurrentPact(userId: string) {
  const pact = await db
    .selectFrom("pact")
    .select(pactColumns)
    .where("user_id", "=", userId)
    .where("status", "=", "active")
    .executeTakeFirst();
  if (!pact) return undefined;
  return withToday(pact);
}

type PactRow = Awaited<ReturnType<typeof requireOwnedPact>>;

/** A pact row as the phone wants it: with the handset's name and today's minutes. */
async function withToday(pact: PactRow) {
  const device = await db
    .selectFrom("device")
    .select("model")
    .where("id", "=", pact.device_id)
    .executeTakeFirst();

  // Today's minutes, as the last phone reported them.
  //
  // Without this, changing phones handed somebody a fresh allowance: a phone
  // can only measure its own screen, so the new one opens on zero and thirty
  // minutes of Instagram becomes sixty by signing in somewhere else. The day
  // belongs to the person, not to the handset, and this is the only place
  // that knows the whole of it.
  const day = dayInZone(new Date(), pact.timezone);
  const today = await db
    .selectFrom("daily_summary")
    .select(["app_package", "minutes_used"])
    .where("pact_id", "=", pact.id)
    .where("day", "=", day)
    .execute();

  return {
    ...pact,
    device_model: device?.model ?? null,
    today: {
      day,
      apps: today.map((row) => ({ package: row.app_package, minutes_used: row.minutes_used })),
    },
  };
}

/**
 * Brings one more app under a limit while the challenge is running.
 *
 * The snapshot is locked when a challenge starts, and this is the one edit
 * it takes, in the one direction that keeps the lock meaning something: an
 * app can be added, never removed, and no limit moves. Adding tightens the
 * promise the witnesses are watching, so nobody is told; their summary
 * simply shows one more app from today. The phone counts the new app
 * against today's minutes at once -- an app already past the limit locks
 * the moment it is added, which is the day's usage and not a breach, the
 * same as starting a challenge in the afternoon.
 *
 * `added_on` is today in the pact's own zone, kept so a screen looking back
 * over the week does not judge the days before the app was under a limit.
 * The row is locked for the write: two adds in flight would otherwise each
 * read the same list and the second would drop the first's app.
 */
export async function addAppToPact(userId: string, pactId: string, app: PactAppAdd) {
  await requireOwnedPact(userId, pactId);
  const updated = await db.transaction().execute(async (trx) => {
    const pact = await trx
      .selectFrom("pact")
      .select(["status", "timezone", "snapshot"])
      .where("id", "=", pactId)
      .forUpdate()
      .executeTakeFirstOrThrow();
    if (pact.status !== "active") {
      throw conflict("pact_closed", `This pact is already ${pact.status}.`);
    }
    const apps = pact.snapshot.apps;
    if (apps.some((a) => a.package === app.package)) {
      throw conflict("app_already_in_pact", `${app.package} is already part of this pact.`);
    }
    if (apps.length >= MAX_SNAPSHOT_APPS) {
      throw conflict("too_many_apps", `A pact holds at most ${MAX_SNAPSHOT_APPS} apps.`);
    }

    const now = new Date();
    const snapshot: Snapshot = {
      ...pact.snapshot,
      apps: [...apps, { ...app, added_on: dayInZone(now, pact.timezone) }],
    };
    return trx
      .updateTable("pact")
      .set({ snapshot: JSON.stringify(snapshot), updated_at: now })
      .where("id", "=", pactId)
      .returning(pactColumns)
      .executeTakeFirstOrThrow();
  });
  // The same answer as GET /pacts/current, so the phone can take the whole
  // thing as its new copy rather than patch its own.
  return withToday(updated);
}

/**
 * This phone is the one enforcing the challenge now.
 *
 * One account runs on one phone, so a challenge is never in two places: it
 * is wherever the person last signed in, and it gets there by itself.
 * [registerDevice] calls this the moment a new handset announces itself,
 * which is why nobody is ever asked to move a challenge by hand.
 *
 * It is what makes the uninstall check honest as well. "Did the device
 * running this challenge disappear" is only a real question if the answer can
 * change hands.
 */
export async function claimPact(userId: string, pactId: string, deviceId: string) {
  const pact = await requireOwnedPact(userId, pactId);
  if (pact.status !== "active") {
    throw conflict("challenge_over", "That challenge is no longer running.");
  }
  const device = await requireOwnedDevice(userId, deviceId);
  if (pact.device_id === deviceId) return pact;
  return db.transaction().execute((trx) => movePactToDevice(trx, pact.id, userId, device, new Date()));
}

/**
 * Moves an active pact onto [device], records it, and tells the witnesses.
 *
 * Two things happen here that both matter.
 *
 * The witnesses are told, and not because changing phones is suspicious --
 * somebody whose handset died is doing the honest thing and the words say
 * so. It is because this is the one move that could be an escape leaving
 * nothing behind: sign in on a tablet in a drawer and the phone they
 * actually use stops being enforced, reports nothing, and looks perfect. It
 * costs a message, the way pressing Give up does.
 *
 * And `protection_pending_since` starts running, unless the phone taking it
 * over has already said its protection is on. Permissions are granted per
 * install: a new phone -- or the same phone after a reinstall -- has none of
 * them, so between here and the person granting them there is a live
 * challenge that nothing is enforcing. The watchdog reads this column and
 * gives them two hours before saying so out loud.
 */
export async function movePactToDevice(
  trx: Transaction<Database>,
  pactId: string,
  userId: string,
  device: { id: string; protection_enabled: boolean },
  now: Date,
) {
  const moved = await trx
    .updateTable("pact")
    .set({
      device_id: device.id,
      protection_pending_since: device.protection_enabled ? null : now,
      updated_at: now,
    })
    .where("id", "=", pactId)
    .returning(pactColumns)
    .executeTakeFirstOrThrow();

  const eventId = newId();
  await trx
    .insertInto("pact_event")
    .values({
      id: eventId,
      pact_id: pactId,
      device_id: device.id,
      type: "moved",
      reason: null,
      app_package: null,
      minutes: null,
      occurred_at: now,
      source: "server",
    })
    .execute();
  await queueWitnessNotifications(trx, { userId, eventId, kind: "pact_moved", pactId });
  return moved;
}

export async function requireOwnedPact(userId: string, pactId: string) {
  if (!isUuidLike(pactId)) throw notFound("Pact");
  const pact = await db
    .selectFrom("pact")
    .select(pactColumns)
    .where("id", "=", pactId)
    .where("user_id", "=", userId)
    .executeTakeFirst();
  if (!pact) throw notFound("Pact");
  return pact;
}

// Owner, or an accepted witness the owner lets see progress.
export async function getPactWithEvents(callerId: string, pactId: string) {
  if (!isUuidLike(pactId)) throw notFound("Pact");
  const pact = await db.selectFrom("pact").select(pactColumns).where("id", "=", pactId).executeTakeFirst();
  if (!pact || !(await canViewPact(callerId, pact.user_id, pact.id))) throw notFound("Pact");
  const events = await db
    .selectFrom("pact_event")
    .select(["id", "type", "reason", "app_package", "minutes", "occurred_at", "received_at", "source"])
    .where("pact_id", "=", pactId)
    .orderBy("received_at", "desc")
    .orderBy("id", "desc")
    .execute();
  const activities = await db
    .selectFrom("activity")
    .select(["id", "type", "target", "reward_min", "started_at", "deadline_at", "status", "ended_at"])
    .where("pact_id", "=", pactId)
    .orderBy("started_at", "desc")
    .execute();
  return { ...pact, events, activities };
}

// Newest first, cursor = id of the last row seen. The (created_at, id) tuple
// is compared inside SQL against the cursor row so microsecond precision is
// never lost through a JS Date.
export async function listPacts(userId: string, cursor: string | undefined, limit: number) {
  let query = db
    .selectFrom("pact")
    .select(pactColumns)
    .where("user_id", "=", userId)
    .orderBy("created_at", "desc")
    .orderBy("id", "desc")
    .limit(limit + 1);

  if (cursor) {
    if (!isUuidLike(cursor)) throw notFound("Cursor");
    query = query.where(
      sql<boolean>`(pact.created_at, pact.id) < (select c.created_at, c.id from pact c where c.id = ${cursor} and c.user_id = ${userId})`,
    );
  }

  const rows = await query.execute();
  const hasMore = rows.length > limit;
  const items = hasMore ? rows.slice(0, limit) : rows;
  return { items, next_cursor: hasMore ? items.at(-1)!.id : null };
}
