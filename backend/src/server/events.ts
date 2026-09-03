import { db, isUniqueViolation } from "./db/client";
import type { Database } from "./db/schema";
import { requireOwnedPact } from "./pacts";
import { conflict } from "@/lib/http";
import type { EventCreate } from "@/lib/schemas";
import { newId } from "@/lib/uuid";
import type { Kysely, Transaction } from "kysely";

const eventColumns = [
  "id",
  "pact_id",
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
// also closes the pact; every other type on a closed pact is
// refused so a late limit_hit cannot resurrect anything.
export async function recordDeviceEvent(userId: string, pactId: string, input: EventCreate) {
  const pact = await requireOwnedPact(userId, pactId);

  const existing = await db
    .selectFrom("pact_event")
    .select(eventColumns)
    .where("id", "=", input.id)
    .executeTakeFirst();
  if (existing) {
    if (existing.pact_id !== pactId) {
      throw conflict("event_id_reused", "That event id belongs to another pact.");
    }
    return { event: existing, created: false };
  }

  if (pact.status !== "active") {
    throw conflict("pact_closed", `This pact is already ${pact.status}.`);
  }

  try {
    return await db.transaction().execute(async (trx) => {
      const event = await trx
        .insertInto("pact_event")
        .values({
          id: input.id,
          pact_id: pactId,
          device_id: pact.device_id,
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
        await closePact(trx, pactId, input.type as "broken" | "completed");
        await queueWitnessNotifications(trx, {
          userId,
          eventId: event.id,
          kind: input.type === "broken" ? "pact_broken" : "pact_completed",
          pactId,
        });
      }
      return { event, created: true };
    });
  } catch (error) {
    // Lost the race with an identical retry: hand back the winner's row.
    if (isUniqueViolation(error, "pact_event_pkey")) {
      const winner = await db
        .selectFrom("pact_event")
        .select(eventColumns)
        .where("id", "=", input.id)
        .executeTakeFirstOrThrow();
      return { event: winner, created: false };
    }
    throw error;
  }
}

async function closePact(
  trx: Transaction<Database>,
  pactId: string,
  status: "broken" | "completed",
): Promise<void> {
  const now = new Date();
  const result = await trx
    .updateTable("pact")
    .set({ status, ended_at: now, updated_at: now })
    .where("id", "=", pactId)
    .where("status", "=", "active")
    .executeTakeFirst();
  if (result.numUpdatedRows === 0n) {
    throw conflict("pact_closed", "This pact was closed by an earlier event.");
  }
}

// One queued notification per accepted witness who asked for this kind, per
// channel. Delivery (FCM / Resend) is the watchdog's job; this only writes
// the ledger rows, and the partial unique index makes a retry harmless.
export async function queueWitnessNotifications(
  trx: Transaction<Database> | Kysely<Database>,
  args: { userId: string; eventId: string; kind: "pact_broken" | "pact_completed" | "pact_started"; pactId: string },
): Promise<number> {
  const prefColumn =
    args.kind === "pact_broken"
      ? "notify_failure"
      : args.kind === "pact_completed"
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
        deep_link: `/witness/${args.userId}/pacts/${args.pactId}`,
      })
      .onConflict((oc) => oc.doNothing())
      .executeTakeFirst();
    if (result.numInsertedOrUpdatedRows === 1n) queued += 1;
  }
  return queued;
}

export function notificationCopy(
  kind: "pact_broken" | "pact_completed" | "pact_started",
  name: string,
  roast: boolean,
): { title: string; body: string } {
  switch (kind) {
    case "pact_started":
      return { title: `${name} made a promise`, body: `${name} started a new pact and named you as a witness.` };
    case "pact_completed":
      return { title: `${name} kept their word`, body: `${name} finished their pact. Tell them you noticed.` };
    case "pact_broken":
      return roast
        ? { title: `${name} folded`, body: `${name} broke their pact. You know what to do.` }
        : { title: `${name} broke their pact`, body: `${name} didn't make it this time. A word from you might help.` };
  }
}
