import { sql } from "kysely";
import { db, isUniqueViolation } from "./db/client";
import { requireOwnedDevice } from "./devices";
import { conflict, notFound } from "@/lib/http";
import type { CommitmentCreate } from "@/lib/schemas";
import { addDays } from "@/lib/time";
import { isUuidLike, newId } from "@/lib/uuid";

export const commitmentColumns = [
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

// Lock a commitment. The partial unique index commitment_one_active_idx is
// the real guard against two active commitments; the pre-check only makes
// the common case a clean 409 without a failed insert.
export async function createCommitment(userId: string, input: CommitmentCreate) {
  await requireOwnedDevice(userId, input.device_id);

  const startsAt = new Date();
  const id = newId();
  try {
    return await db.transaction().execute(async (trx) => {
      const commitment = await trx
        .insertInto("commitment")
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
        .returning(commitmentColumns)
        .executeTakeFirstOrThrow();

      await trx
        .insertInto("commitment_event")
        .values({
          id: newId(),
          commitment_id: id,
          device_id: input.device_id,
          type: "started",
          reason: null,
          app_package: null,
          minutes: null,
          occurred_at: startsAt,
          source: "server",
        })
        .execute();

      return commitment;
    });
  } catch (error) {
    if (isUniqueViolation(error, "commitment_one_active_idx")) {
      throw conflict("commitment_active", "You already have an active commitment.");
    }
    throw error;
  }
}

export async function getCurrentCommitment(userId: string) {
  return db
    .selectFrom("commitment")
    .select(commitmentColumns)
    .where("user_id", "=", userId)
    .where("status", "=", "active")
    .executeTakeFirst();
}

// Owner-only for now; witness access is added with the witness routes.
export async function requireOwnedCommitment(userId: string, commitmentId: string) {
  if (!isUuidLike(commitmentId)) throw notFound("Commitment");
  const commitment = await db
    .selectFrom("commitment")
    .select(commitmentColumns)
    .where("id", "=", commitmentId)
    .where("user_id", "=", userId)
    .executeTakeFirst();
  if (!commitment) throw notFound("Commitment");
  return commitment;
}

export async function getCommitmentWithEvents(userId: string, commitmentId: string) {
  const commitment = await requireOwnedCommitment(userId, commitmentId);
  const events = await db
    .selectFrom("commitment_event")
    .select(["id", "type", "reason", "app_package", "minutes", "occurred_at", "received_at", "source"])
    .where("commitment_id", "=", commitmentId)
    .orderBy("received_at", "desc")
    .orderBy("id", "desc")
    .execute();
  return { ...commitment, events };
}

// Newest first, cursor = id of the last row seen. The (created_at, id) tuple
// is compared inside SQL against the cursor row so microsecond precision is
// never lost through a JS Date.
export async function listCommitments(userId: string, cursor: string | undefined, limit: number) {
  let query = db
    .selectFrom("commitment")
    .select(commitmentColumns)
    .where("user_id", "=", userId)
    .orderBy("created_at", "desc")
    .orderBy("id", "desc")
    .limit(limit + 1);

  if (cursor) {
    if (!isUuidLike(cursor)) throw notFound("Cursor");
    query = query.where(
      sql<boolean>`(commitment.created_at, commitment.id) < (select c.created_at, c.id from commitment c where c.id = ${cursor} and c.user_id = ${userId})`,
    );
  }

  const rows = await query.execute();
  const hasMore = rows.length > limit;
  const items = hasMore ? rows.slice(0, limit) : rows;
  return { items, next_cursor: hasMore ? items.at(-1)!.id : null };
}
