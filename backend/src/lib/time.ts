const DAY_MS = 24 * 60 * 60 * 1000;

// A commitment of N days ends exactly N*24h after it starts. Absolute time,
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
