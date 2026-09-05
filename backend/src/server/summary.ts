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

  // The limit is the one locked in the snapshot, not the one in the body.
  // The body's `limit_min` is accepted for the wire's sake and ignored: a
  // witness's "within limits" used to be computed from whatever the phone
  // sent, so a modified client with `limit_min: 1440` had a perfect streak
  // beside the real limits shown on the same screen. Earned minutes are
  // capped at what the pact's own rules allow in a day, for the same reason.
  const limits = new Map(pact.snapshot.apps.map((a) => [a.package, a.daily_limit_min]));
  const unknown = input.apps.find((a) => !limits.has(a.package));
  if (unknown) throw conflict("app_not_in_pact", `${unknown.package} is not part of this pact.`);
  const earnCap = Math.max(
    0,
    ...Object.values(pact.snapshot.activities ?? {}).map((rule) => rule?.daily_cap_min ?? 0),
  );

  const now = new Date();
  await db
    .insertInto("daily_summary")
    .values(
      input.apps.map((a) => ({
        pact_id: pactId,
        day: input.day,
        app_package: a.package,
        minutes_used: a.minutes_used,
        limit_min: limits.get(a.package)!,
        earned_min: Math.min(a.earned_min, earnCap),
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
