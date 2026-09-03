import { describe, expect, it } from "vitest";
import { addDays, isValidTimeZone } from "./time";

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
