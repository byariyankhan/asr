import { describe, expect, it } from "vitest";
import { addDays, dayNumber, daysBetween, isValidTimeZone } from "./time";

describe("addDays", () => {
  it("adds exactly 24h per day regardless of DST", () => {
    const start = new Date("2026-03-07T12:00:00Z");
    expect(addDays(start, 7).getTime() - start.getTime()).toBe(7 * 86_400_000);
  });
});

describe("isValidTimeZone", () => {
  it("accepts IANA names and rejects junk", () => {
    expect(isValidTimeZone("Asia/Dhaka")).toBe(true);
    expect(isValidTimeZone("UTC")).toBe(true);
    expect(isValidTimeZone("Mars/Olympus")).toBe(false);
    expect(isValidTimeZone("")).toBe(false);
  });
});

describe("dayNumber", () => {
  // 23:30 in Dhaka on 1 September is 17:30Z.
  const start = new Date("2026-09-01T17:30:00Z");

  it("counts calendar days in the pact's zone, not 24-hour periods", () => {
    // 05:00 the next morning in Dhaka: five and a half hours in, and
    // already the second day the person will cross off.
    const nextMorning = new Date("2026-09-01T23:00:00Z");
    expect(dayNumber(start, 7, "Asia/Dhaka", nextMorning)).toBe(2);
    // The same instant is still 1 September in UTC.
    expect(dayNumber(start, 7, "UTC", nextMorning)).toBe(1);
  });

  it("is never below one and never past the last day", () => {
    expect(dayNumber(start, 7, "Asia/Dhaka", new Date("2026-08-20T00:00:00Z"))).toBe(1);
    expect(dayNumber(start, 7, "Asia/Dhaka", new Date("2026-10-01T00:00:00Z"))).toBe(7);
  });

  it("agrees with the completion rule: day N+1 is when a pact of N days has run its course", () => {
    // 8 September in Dhaka is day 8 of a 7-day pact, capped at 7 here and
    // "done" for hasRunItsCourse.
    expect(dayNumber(start, 7, "Asia/Dhaka", new Date("2026-09-07T18:30:00Z"))).toBe(7);
    expect(daysBetween("2026-09-01", "2026-09-08")).toBe(7);
  });
});

describe("daysBetween", () => {
  it("is plain date arithmetic, signed", () => {
    expect(daysBetween("2026-03-28", "2026-03-30")).toBe(2);
    expect(daysBetween("2026-03-30", "2026-03-28")).toBe(-2);
    expect(daysBetween("2026-12-31", "2027-01-01")).toBe(1);
  });
});
