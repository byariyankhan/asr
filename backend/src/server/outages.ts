import { db } from "./db/client";

/**
 * Time the server was away, and what it does to every rule built on silence.
 *
 * The watchdog breaks a challenge after a day without a heartbeat, tells the
 * witnesses after two hours without protection, and calls an app deleted
 * after a suspicion has stood for two hours with nothing from the phone.
 * Silence is only evidence while the server was there to be spoken to. A
 * server that was down for a day used to come back and, in its first run,
 * break every running challenge at once and tell every witness -- the
 * phones had been talking to a wall.
 *
 * So the watchdog keeps its own clock. Each run writes down that it ran
 * (`watchdog_state`); a run that finds the previous one more than
 * [OUTAGE_GAP_MS] old writes the gap down as an outage (`server_outage`).
 * Every silence rule then measures with [uptimeBetween]: the wall-clock
 * silence less the outages inside it, each extended by [RECOVERY_GRACE_MS]
 * because the phones did not know the server was away and will each check
 * in at their next half-hourly heartbeat.
 *
 * A gap in the watchdog counts as an outage whether the whole server was
 * down or only the loop, which errs on the side of the phone: at worst a
 * real uninstall is noticed a little later.
 */
export type Outage = { started_at: Date; ended_at: Date };

/**
 * A run later than this after the previous one is a gap the server was
 * away for. Twice the loop's interval: one late tick is not an outage.
 */
export const OUTAGE_GAP_MS = 30 * 60 * 1000;

/**
 * How long after the server comes back before silence counts again.
 *
 * A phone heartbeats every half hour and did not know the server was gone,
 * so nothing it has not said in the first three-quarters of an hour after
 * recovery means anything yet. The same margin the probe uses for "one
 * missed heartbeat".
 */
export const RECOVERY_GRACE_MS = 45 * 60 * 1000;

/** How far back outages are loaded for a run. Far longer than any silence
 *  rule, so a rule never sees a silence longer than its own outage list. */
const OUTAGE_LOOKBACK_MS = 30 * 24 * 60 * 60 * 1000;

/**
 * Milliseconds of [from, to] during which nothing could reach the server:
 * every outage plus the grace after it, merged where they touch so that two
 * outages close together are not counted twice.
 */
export function downtimeWithin(outages: readonly Outage[], from: Date, to: Date, grace = RECOVERY_GRACE_MS): number {
  const lo = from.getTime();
  const hi = to.getTime();
  if (hi <= lo) return 0;
  const spans = outages
    .map((o): [number, number] => [o.started_at.getTime(), o.ended_at.getTime() + grace])
    .filter(([start, end]) => end > start)
    .sort((a, b) => a[0] - b[0]);
  let total = 0;
  let current: [number, number] | null = null;
  for (const span of spans) {
    if (current && span[0] <= current[1]) {
      current[1] = Math.max(current[1], span[1]);
    } else {
      if (current) total += overlap(current, lo, hi);
      current = [span[0], span[1]];
    }
  }
  if (current) total += overlap(current, lo, hi);
  return total;
}

function overlap([start, end]: [number, number], lo: number, hi: number): number {
  return Math.max(0, Math.min(end, hi) - Math.max(start, lo));
}

/** Milliseconds between `from` and `to` in which the server was there to be spoken to. */
export function uptimeBetween(outages: readonly Outage[], from: Date, to: Date, grace = RECOVERY_GRACE_MS): number {
  const wall = to.getTime() - from.getTime();
  if (wall <= 0) return 0;
  return wall - downtimeWithin(outages, from, to, grace);
}

/**
 * Writes down that the watchdog is running now, and notices a gap.
 *
 * Returns the outage it noticed, or null. A marker that is in the future --
 * a clock that was put back -- is left alone: nothing is recorded and the
 * marker is not moved backwards, so a bad clock cannot drag it into the
 * past and have the correction read as a day-long outage.
 */
export async function recordRun(now: Date): Promise<Outage | null> {
  const previous = await db.selectFrom("watchdog_state").select("last_run_at").where("id", "=", 1).executeTakeFirst();
  if (previous && previous.last_run_at.getTime() > now.getTime()) return null;

  let outage: Outage | null = null;
  if (previous && now.getTime() - previous.last_run_at.getTime() > OUTAGE_GAP_MS) {
    outage = { started_at: previous.last_run_at, ended_at: now };
    await db
      .insertInto("server_outage")
      .values(outage)
      .onConflict((oc) => oc.column("started_at").doNothing())
      .execute();
  }
  await db
    .insertInto("watchdog_state")
    .values({ id: 1, last_run_at: now })
    .onConflict((oc) => oc.column("id").doUpdateSet({ last_run_at: now }))
    .execute();
  return outage;
}

/** The outages that could still bear on a silence being measured now. */
export async function recentOutages(now: Date): Promise<Outage[]> {
  return db
    .selectFrom("server_outage")
    .select(["started_at", "ended_at"])
    .where("ended_at", ">", new Date(now.getTime() - OUTAGE_LOOKBACK_MS))
    .orderBy("started_at")
    .execute();
}
