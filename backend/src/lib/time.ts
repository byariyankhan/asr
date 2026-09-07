const DAY_MS = 24 * 60 * 60 * 1000;

// A pact of N days ends exactly N*24h after it starts. Absolute time,
// not calendar days: "7 days" means the same thing in every timezone and
// across a DST change, and the phone shows the countdown from the same
// instant the server enforces.
export function addDays(from: Date, days: number): Date {
  return new Date(from.getTime() + days * DAY_MS);
}

// IANA zone names are validated by asking Intl; there is no list to keep.
export function isValidTimeZone(zone: string): boolean {
  try {
    new Intl.DateTimeFormat("en-US", { timeZone: zone });
    return true;
  } catch {
    return false;
  }
}

// ISO 3166-1 alpha-2 check via Intl: an unassigned code comes back unchanged
// (or as "Unknown Region"), an assigned one comes back as a name.
export function isValidCountry(code: string): boolean {
  try {
    const name = new Intl.DisplayNames(["en"], { type: "region" }).of(code);
    return typeof name === "string" && name !== code && name !== "Unknown Region";
  } catch {
    return false;
  }
}

// Calendar day (YYYY-MM-DD) of an instant in an IANA zone. en-CA formats as
// ISO order; no library needed.
export function dayInZone(at: Date, zone: string): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: zone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(at);
}

/**
 * The calendar a pact's "today" is on: the zone the phone last reported, or
 * the zone the challenge was locked in until it has reported one.
 *
 * The phone keys every day it stamps -- summaries, the carried-over day, an
 * added app's first day -- to the zone it is living in, and re-derives the
 * challenge's day number in that zone too. Comparing those to days computed
 * in the zone at the start was a mismatch for hours every day once the
 * person had travelled: a witness read "limits not reported today" against
 * a phone reporting every five minutes.
 */
export function phoneZone(pact: { timezone: string; phone_timezone?: string | null }): string {
  return pact.phone_timezone ?? pact.timezone;
}

/**
 * 1-based day number inside a pact, capped at its length.
 *
 * Calendar days in the pact's zone, not 24-hour steps. The phone counts the
 * day a person crosses off (somebody who starts at 23:30 is on day two the
 * next morning), and the server's own completion rule already counts the
 * same way. Counted in elapsed periods, a witness read "Day 1 · 6 days
 * left" all of the owner's second day, and "Day 6" on the morning the
 * challenge was about to complete.
 */
export function dayNumber(startsAt: Date, durationDays: number, timezone: string, now = new Date()): number {
  const elapsed = daysBetween(dayInZone(startsAt, timezone), dayInZone(now, timezone)) + 1;
  return Math.max(1, Math.min(durationDays, elapsed));
}

/** Whole calendar days from one YYYY-MM-DD to another; negative when `to` is the earlier one. */
export function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / DAY_MS);
}

/**
 * The calendar day before this one, as YYYY-MM-DD.
 *
 * Pure date arithmetic, not "minus 24 hours". Those are the same number on
 * most days and different on the two a year a zone changes offset, which is
 * exactly when a streak counted in 24-hour steps skips a day or counts one
 * twice.
 */
export function previousDay(day: string): string {
  return addDaysToDay(day, -1);
}

/** A calendar day plus (or minus) whole days, as YYYY-MM-DD. Same arithmetic as `previousDay`. */
export function addDaysToDay(day: string, days: number): string {
  const at = new Date(`${day}T00:00:00Z`);
  at.setUTCDate(at.getUTCDate() + days);
  return at.toISOString().slice(0, 10);
}
