import { db, isUniqueViolation } from "./db/client";
import type { Database } from "./db/schema";
import { requireOwnedCommitment } from "./commitments";
import { conflict } from "@/lib/http";
import type { EventCreate } from "@/lib/schemas";
import { newId } from "@/lib/uuid";
import type { Kysely, Transaction } from "kysely";

const eventColumns = [
  "id",
  "commitment_id",
  "device_id",
  "type",
  "reason",
  "app_package",
  "minutes",
  "occurred_at",
  "received_at",
  "source",
] as const;

export type RecordedEvent = {
  event: { id: string; type: string };
  created: boolean;
};

const CLOSING: ReadonlySet<string> = new Set(["broken", "completed"]);

// A device reports an outcome. The event id was generated on the phone and is
// the primary key, so a retried POST after a lost response returns the row
// that already exists instead of double-reporting a break. A closing event
// also closes the commitment; every other type on a closed commitment is
// refused so a late limit_hit cannot resurrect anything.
export async function recordDeviceEvent(userId: string, commitmentId: string, input: EventCreate) {
  const commitment = await requireOwnedCommitment(userId, commitmentId);

  const existing = await db
    .selectFrom("commitment_event")
    .select(eventColumns)
    .where("id", "=", input.id)
    .executeTakeFirst();
  if (existing) {
    if (existing.commitment_id !== commitmentId) {
      throw conflict("event_id_reused", "That event id belongs to another commitment.");
    }
    return { event: existing, created: false };
  }

  if (commitment.status !== "active") {
    throw conflict("commitment_closed", `This commitment is already ${commitment.status}.`);
  }

  try {
    return await db.transaction().execute(async (trx) => {
      const event = await trx
        .insertInto("commitment_event")
        .values({
          id: input.id,
          commitment_id: commitmentId,
          device_id: commitment.device_id,
          type: input.type,
          reason: input.reason ?? null,
          app_package: input.app_package ?? null,
          minutes: input.minutes ?? null,
          occurred_at: new Date(input.occurred_at),
          source: "device",
        })
        .returning(eventColumns)
        .executeTakeFirstOrThrow();

      if (CLOSING.has(input.type)) {
        await closeCommitment(trx, commitmentId, input.type as "broken" | "completed");
        await queueWitnessNotifications(trx, {
          userId,
          eventId: event.id,
          kind: input.type === "broken" ? "commitment_broken" : "commitment_completed",
          commitmentId,
        });
      }
      return { event, created: true };
    });
  } catch (error) {
    // Lost the race with an identical retry: hand back the winner's row.
    if (isUniqueViolation(error, "commitment_event_pkey")) {
      const winner = await db
        .selectFrom("commitment_event")
        .select(eventColumns)
        .where("id", "=", input.id)
        .executeTakeFirstOrThrow();
      return { event: winner, created: false };
    }
    throw error;
  }
}

async function closeCommitment(
  trx: Transaction<Database>,
  commitmentId: string,
  status: "broken" | "completed",
): Promise<void> {
  const now = new Date();
  const result = await trx
    .updateTable("commitment")
    .set({ status, ended_at: now, updated_at: now })
    .where("id", "=", commitmentId)
    .where("status", "=", "active")
    .executeTakeFirst();
  if (result.numUpdatedRows === 0n) {
    throw conflict("commitment_closed", "This commitment was closed by an earlier event.");
  }
}

// One queued notification per accepted witness who asked for this kind, per
// channel. Delivery (FCM / Resend) is the watchdog's job; this only writes
// the ledger rows, and the partial unique index makes a retry harmless.
export async function queueWitnessNotifications(
  trx: Transaction<Database> | Kysely<Database>,
  args: { userId: string; eventId: string; kind: "commitment_broken" | "commitment_completed" | "commitment_started"; commitmentId: string },
): Promise<number> {
  const prefColumn =
    args.kind === "commitment_broken"
      ? "notify_failure"
      : args.kind === "commitment_completed"
        ? "notify_success"
        : "notify_start";

  const witnesses = await trx
    .selectFrom("witness")
    .innerJoin("user as u", "u.id", "witness.user_id")
    .select(["witness.witness_user_id", "witness.roast_mode", "u.name as user_name"])
    .where("witness.user_id", "=", args.userId)
    .where("witness.status", "=", "accepted")
    .where(`witness.${prefColumn}`, "=", true)
    .execute();

  let queued = 0;
  for (const w of witnesses) {
    if (!w.witness_user_id) continue;
    const copy = notificationCopy(args.kind, w.user_name, w.roast_mode);
    const result = await trx
      .insertInto("notification")
      .values({
        id: newId(),
        recipient_id: w.witness_user_id,
        about_user_id: args.userId,
        event_id: args.eventId,
        channel: "push",
        kind: args.kind,
        title: copy.title,
        body: copy.body,
        deep_link: `/witness/${args.userId}/commitments/${args.commitmentId}`,
      })
      .onConflict((oc) => oc.doNothing())
      .executeTakeFirst();
    if (result.numInsertedOrUpdatedRows === 1n) queued += 1;
  }
  return queued;
}

export function notificationCopy(
  kind: "commitment_broken" | "commitment_completed" | "commitment_started",
  name: string,
  roast: boolean,
): { title: string; body: string } {
  switch (kind) {
    case "commitment_started":
      return { title: `${name} made a promise`, body: `${name} started a new commitment and named you as a witness.` };
    case "commitment_completed":
      return { title: `${name} kept their word`, body: `${name} finished their commitment. Tell them you noticed.` };
    case "commitment_broken":
      return roast
        ? { title: `${name} folded`, body: `${name} broke their commitment. You know what to do.` }
        : { title: `${name} broke their commitment`, body: `${name} didn't make it this time. A word from you might help.` };
  }
}
