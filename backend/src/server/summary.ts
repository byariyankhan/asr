import { db } from "./db/client";
import { requireOwnedPact } from "./pacts";
import { conflict } from "@/lib/http";
import type { SummaryCreate } from "@/lib/schemas";
import { dayInZone } from "@/lib/time";

// The one per-app number the server keeps: minutes per day for apps under
// the pact, so a witness can see "2 of 3 within limits". Upsert, so the
// phone can resend the day as it finalises.
export async function upsertDailySummary(userId: string, pactId: string, input: SummaryCreate): Promise<void> {
  const pact = await requireOwnedPact(userId, pactId);

  const firstDay = dayInZone(pact.starts_at, pact.timezone);
  const lastInstant = pact.ended_at && pact.ended_at < pact.ends_at ? pact.ended_at : pact.ends_at;
  const lastDay = dayInZone(new Date(Math.min(lastInstant.getTime(), Date.now())), pact.timezone);
  if (input.day < firstDay || input.day > lastDay) {
    throw conflict("day_out_of_range", `Summaries are accepted for ${firstDay} to ${lastDay}.`);
  }

  const allowed = new Set(pact.snapshot.apps.map((a) => a.package));
  const unknown = input.apps.find((a) => !allowed.has(a.package));
  if (unknown) throw conflict("app_not_in_pact", `${unknown.package} is not part of this pact.`);

  const now = new Date();
  await db
    .insertInto("daily_summary")
    .values(
      input.apps.map((a) => ({
        pact_id: pactId,
        day: input.day,
        app_package: a.package,
        minutes_used: a.minutes_used,
        limit_min: a.limit_min,
        earned_min: a.earned_min,
        received_at: now,
      })),
    )
    .onConflict((oc) =>
      oc.columns(["pact_id", "day", "app_package"]).doUpdateSet((eb) => ({
        minutes_used: eb.ref("excluded.minutes_used"),
        limit_min: eb.ref("excluded.limit_min"),
        earned_min: eb.ref("excluded.earned_min"),
        received_at: now,
      })),
    )
    .execute();
}
