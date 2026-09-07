import type { Transaction } from "kysely";
import { db, isUniqueViolation } from "./db/client";
import type { Database } from "./db/schema";
import { queueWitnessNotifications } from "./notifications";
import { requireOwnedPact } from "./pacts";
import { conflict } from "@/lib/http";
import type { EventCreate } from "@/lib/schemas";
import { addDaysToDay, dayInZone } from "@/lib/time";

export const eventColumns = [
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

const CLOSING: ReadonlySet<string> = new Set(["broken", "completed"]);

/** Room for a device clock that runs a little ahead of the server's. */
export const COMPLETION_GRACE_MS = 2 * 60 * 60 * 1000;

/**
 * Whether the phone's own completion rule can honestly be true yet.
 *
 * The phone completes a challenge on the first local day after its last one
 * -- day eight of seven, at midnight -- and the server's `ends_at` is the
 * exact instant N days after the start, which can be almost a day later. So
 * this is the phone's rule, computed here: today in the pact's zone is on or
 * past the day after the last day. Two hours of grace for a clock that runs
 * ahead; nothing for one that has been moved.
 *
 * Without this the ledger believed any `completed` a device sent, and a
 * phone whose date had been pushed a month forward closed a pact on day
 * three with the witnesses congratulated. The date is the easiest thing on
 * a phone to change, and the server is the only party with a clock of its
 * own.
 */
export function hasRunItsCourse(
  // The zone the challenge was locked in, on purpose -- not the one the
  // phone reports. Everything else about "today" follows the phone, but a
  // zone is as easy to change in Settings as the date, and following it
  // here would let a challenge be finished up to a day early by claiming
  // a zone further east.
  pact: { starts_at: Date; duration_days: number; timezone: string },
  now: Date,
): boolean {
  const firstDay = dayInZone(pact.starts_at, pact.timezone);
  const doneOn = addDaysToDay(firstDay, pact.duration_days);
  return dayInZone(new Date(now.getTime() + COMPLETION_GRACE_MS), pact.timezone) >= doneOn;
}

// A device reports an outcome. The event id was generated on the phone and is
// the primary key, so a retried POST after a lost response returns the row
// that already exists instead of double-reporting a break. A closing event
// also closes the pact; every other type on a closed pact is refused so a
// late limit_hit cannot resurrect anything.
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
  if (input.type === "completed" && !hasRunItsCourse(pact, new Date())) {
    throw conflict("pact_not_elapsed", "This pact has not run its course yet.");
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
          // A limit blown past and a pact switched off are both "broken",
          // and they are not the same message to somebody's mother.
          reason: input.reason ?? null,
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

export async function closePact(
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
