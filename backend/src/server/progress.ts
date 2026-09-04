import { db } from "./db/client";
import { dayInZone, dayNumber } from "@/lib/time";

const DAY_MS = 86_400_000;

// Everything the Progress tab and a witness's "View progress" show, derived
// from the ledger. No raw usage is involved: "within limits" comes from the
// daily summary the phone chose to send, or from limit_hit events.
export async function progressFor(userId: string) {
  const user = await db.selectFrom("user").select(["id", "name"]).where("id", "=", userId).executeTakeFirstOrThrow();

  const pacts = await db
    .selectFrom("pact")
    .select(["id", "duration_days", "timezone", "starts_at", "ends_at", "status", "ended_at", "snapshot"])
    .where("user_id", "=", userId)
    .orderBy("created_at", "desc")
    .execute();

  const now = new Date();
  const current = pacts.find((p) => p.status === "active") ?? null;
  const completed = pacts.filter((p) => p.status === "completed").length;
  const broken = pacts.filter((p) => p.status === "broken").length;

  const survivedDays = (p: (typeof pacts)[number]) => {
    if (p.status === "completed") return p.duration_days;
    if (p.status === "broken" && p.ended_at) {
      return Math.min(p.duration_days, Math.max(0, Math.floor((p.ended_at.getTime() - p.starts_at.getTime()) / DAY_MS)));
    }
    return dayNumber(p.starts_at, p.duration_days, now) - 1;
  };
  const longest = pacts.reduce((m, p) => Math.max(m, survivedDays(p)), 0);

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
      const hits = await db
        .selectFrom("pact_event")
        .select("app_package")
        .where("pact_id", "=", current.id)
        .where("type", "=", "limit_hit")
        .where("occurred_at", ">=", new Date(now.getTime() - DAY_MS))
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

  const recentPactIds = pacts.slice(0, 3).map((p) => p.id);
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
    streak_days: current ? dayNumber(current.starts_at, current.duration_days, now) : 0,
    longest_streak_days: longest,
    completed,
    broken,
    recent_events,
  };
}
