import { sql } from "kysely";
import { db, isUniqueViolation } from "./db/client";
import { requireOwnedDevice } from "./devices";
import { queueWitnessNotifications } from "./notifications";
import { canViewPact } from "./witnesses";
import { conflict, notFound } from "@/lib/http";
import type { PactCreate } from "@/lib/schemas";
import { addDays } from "@/lib/time";
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
  const device = await db
    .selectFrom("device")
    .select("model")
    .where("id", "=", pact.device_id)
    .executeTakeFirst();
  return { ...pact, device_model: device?.model ?? null };
}

/**
 * This phone is the one enforcing the challenge now.
 *
 * A pact names the device running it, and that used to be settled forever at
 * creation. But the challenge belongs to the person, not to the handset: they
 * reinstall, they lose the phone, they sign in on a second one. Whichever
 * phone has the pact loaded and the service running is the one whose word
 * about it means anything, and it says so here.
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
  await requireOwnedDevice(userId, deviceId);
  if (pact.device_id === deviceId) return pact;

  const now = new Date();
  return db.transaction().execute(async (trx) => {
    const moved = await trx
      .updateTable("pact")
      .set({ device_id: deviceId, updated_at: now })
      .where("id", "=", pactId)
      .returning(pactColumns)
      .executeTakeFirstOrThrow();

    // On the record, and the witnesses are told.
    //
    // Not because moving phones is suspicious -- somebody replacing a broken
    // handset is doing the honest thing and the copy says so. It is because
    // this is the only move that could be an escape and leave nothing
    // behind: a challenge runs on one phone, so taking it onto a tablet in a
    // drawer would leave the phone they actually use unblocked, reporting
    // nothing, looking perfect. That has to cost a message, exactly like
    // pressing Give up does.
    const eventId = newId();
    await trx
      .insertInto("pact_event")
      .values({
        id: eventId,
        pact_id: pactId,
        device_id: deviceId,
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
  });
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
