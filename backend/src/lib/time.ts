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

// 1-based day number inside a pact, capped at its length.
export function dayNumber(startsAt: Date, durationDays: number, now = new Date()): number {
  const elapsed = Math.floor((now.getTime() - startsAt.getTime()) / DAY_MS) + 1;
  return Math.max(1, Math.min(durationDays, elapsed));
}
