import { db } from "./db/client";
import { sql } from "kysely";
import { dayInZone, dayNumber, previousDay } from "@/lib/time";

const DAY_MS = 86_400_000;

/**
 * Everything the Progress tab and a witness's "View progress" show, derived
 * from the ledger. No raw usage is involved: "within limits" comes from the
 * daily summary the phone chose to send, or from limit_hit events.
 *
 * `witnessTo` narrows it to one challenge, and is how a witness reads it.
 * Somebody agreed to watch a challenge; they did not agree to be handed the
 * person's history. Without it this returned the last three challenges'
 * events, a lifetime count of how many were completed and broken, and a
 * longest streak measured across all of them -- to somebody who joined last
 * Tuesday and may have been invited by a stranger.
 */
export async function progressFor(userId: string, witnessTo?: string) {
  const user = await db.selectFrom("user").select(["id", "name"]).where("id", "=", userId).executeTakeFirstOrThrow();

  const pacts = await db
    .selectFrom("pact")
    .select(["id", "duration_days", "timezone", "starts_at", "ends_at", "status", "ended_at", "snapshot"])
    .where("user_id", "=", userId)
    .orderBy("created_at", "desc")
    .execute();

  const now = new Date();
  // A witness reads exactly the challenge they were invited to, and only
  // while it runs. The owner reads their own current one.
  const current = witnessTo
    ? (pacts.find((p) => p.id === witnessTo && p.status === "active") ?? null)
    : (pacts.find((p) => p.status === "active") ?? null);
  const completed = witnessTo ? 0 : pacts.filter((p) => p.status === "completed").length;
  const broken = witnessTo ? 0 : pacts.filter((p) => p.status === "broken").length;

  const survivedDays = (p: (typeof pacts)[number]) => {
    if (p.status === "completed") return p.duration_days;
    if (p.status === "broken" && p.ended_at) {
      return Math.min(p.duration_days, Math.max(0, Math.floor((p.ended_at.getTime() - p.starts_at.getTime()) / DAY_MS)));
    }
    return dayNumber(p.starts_at, p.duration_days, now) - 1;
  };
  const longest = witnessTo ? 0 : pacts.reduce((m, p) => Math.max(m, survivedDays(p)), 0);

  let currentView = null;
  if (current) {
    const day = dayNumber(current.starts_at, current.duration_days, now);
    const today = dayInZone(now, current.timezone);
    const summary = await db
      .selectFrom("daily_summary")
      .select(["app_package", "minutes_used", "limit_min", "earned_min"])
      .where("pact_id", "=", current.id)
      .where("day", "=", today)
      .execute();
    const apps = current.snapshot.apps;
    const today_by_app = new Map(summary.map((s) => [s.app_package, s]));
    let within: number;
    if (summary.length > 0) {
      const over = new Set(summary.filter((s) => s.minutes_used > s.limit_min + s.earned_min).map((s) => s.app_package));
      within = apps.filter((a) => !over.has(a.package)).length;
    } else {
      // The day the limits are actually keyed to, in the challenge's own
      // timezone -- not the last twenty-four hours. Those are different
      // things every morning: a limit reached at eleven last night would
      // otherwise still be "today" at ten the next day, and the screen would
      // show somebody over their limit on a day they had not opened the app.
      // Postgres does the conversion, with its own timezone database.
      const hits = await db
        .selectFrom("pact_event")
        .select("app_package")
        .where("pact_id", "=", current.id)
        .where("type", "=", "limit_hit")
        .where(sql<boolean>`(occurred_at at time zone ${sql.lit(current.timezone)})::date = ${sql.lit(today)}::date`)
        .execute();
      const hit = new Set(hits.map((h) => h.app_package));
      within = apps.filter((a) => !hit.has(a.package)).length;
    }
    currentView = {
      pact_id: current.id,
      day,
      of: current.duration_days,
      status: current.status,
      starts_at: current.starts_at,
      ends_at: current.ends_at,
      // minutes_used is null rather than 0 when the phone has not sent
      // today's summary yet. A witness looking at "0 / 20 min" would read it
      // as somebody who has not opened the app, which is a different fact
      // from "we have not heard from that phone today".
      apps: apps.map((a) => {
        const today = today_by_app.get(a.package);
        return {
          label: a.label,
          package: a.package,
          limit_min: a.daily_limit_min,
          minutes_used: today ? today.minutes_used : null,
          earned_min: today ? today.earned_min : 0,
        };
      }),
      apps_within_limits_today: { within, total: apps.length },
    };
  }

  // The owner sees their last three challenges. A witness sees the one they
  // are watching and nothing else -- not the one before it, and nothing at
  // all once it has ended.
  const recentPactIds = witnessTo ? (current ? [current.id] : []) : pacts.slice(0, 3).map((p) => p.id);
  const recent_events =
    recentPactIds.length === 0
      ? []
      : await db
          .selectFrom("pact_event")
          .select(["id", "pact_id", "type", "reason", "app_package", "minutes", "received_at"])
          .where("pact_id", "in", recentPactIds)
          .orderBy("received_at", "desc")
          .limit(10)
          .execute();

  return {
    user,
    current: currentView,
    streak_days: current ? await streakDays(current, now) : 0,
    longest_streak_days: longest,
    completed,
    broken,
    recent_events,
  };
}

/**
 * Days in a row, ending yesterday, on which every limit held.
 *
 * It used to be the day number: start a fifty-day challenge and the screen
 * read "1 days · CURRENT STREAK" before the first day was over and before
 * the phone had reported anything at all. That is not a streak, it is a
 * calendar, and it congratulated somebody for having started.
 *
 * Today is never counted. A day is over or it is not, and a streak claimed
 * at nine in the morning is a claim about the afternoon.
 *
 * A day with no summary breaks it. The phone sends one per day, so a missing
 * one means the day is unknown -- and an unknown day counted as a good one
 * is the app telling a witness something it does not know. That reads as
 * harsh on a phone that was off; it is the direction to be wrong in, because
 * the alternative is a number nobody should trust.
 */
async function streakDays(
  pact: { id: string; timezone: string; starts_at: Date; duration_days: number },
  now: Date,
): Promise<number> {
  const rows = await db
    .selectFrom("daily_summary")
    .select(["day", "minutes_used", "limit_min", "earned_min"])
    .where("pact_id", "=", pact.id)
    .execute();
  if (rows.length === 0) return 0;

  const over = new Set<string>();
  const seen = new Set<string>();
  for (const row of rows) {
    const day = typeof row.day === "string" ? row.day : dayInZone(row.day, "UTC");
    seen.add(day);
    if (row.minutes_used > row.limit_min + row.earned_min) over.add(day);
  }

  const firstDay = dayInZone(pact.starts_at, pact.timezone);
  let streak = 0;
  // Back from yesterday by calendar days, stopping at the first that was not
  // kept, was not reported, or is before the challenge began. Calendar days
  // rather than 24-hour steps, so the two days a year a zone shifts offset
  // do not skip a date or visit one twice.
  let day = previousDay(dayInZone(now, pact.timezone));
  for (let counted = 0; counted < pact.duration_days; counted++) {
    if (day < firstDay) break;
    if (!seen.has(day) || over.has(day)) break;
    streak += 1;
    day = previousDay(day);
  }
  return streak;
}
