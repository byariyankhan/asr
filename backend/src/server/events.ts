import type { Transaction } from "kysely";
import { db, isUniqueViolation } from "./db/client";
import type { Database } from "./db/schema";
import { queueWitnessNotifications } from "./notifications";
import { requireOwnedPact } from "./pacts";
import { conflict } from "@/lib/http";
import type { EventCreate } from "@/lib/schemas";

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
