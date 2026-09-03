import { describe, expect, it } from "vitest";
import { commitmentCreate, eventCreate, snapshot } from "./schemas";

const goodSnapshot = {
  apps: [{ package: "com.instagram.android", label: "Instagram", daily_limit_min: 30 }],
  reset_time: "04:00",
};

const goodCommitment = {
  device_id: "0192f1c2-1234-7abc-8def-0123456789ab",
  duration_days: 7,
  timezone: "Asia/Dhaka",
  snapshot: goodSnapshot,
};

describe("commitmentCreate", () => {
  it("accepts a valid body and defaults challenges to {}", () => {
    const parsed = commitmentCreate.parse(goodCommitment);
    expect(parsed.snapshot.challenges).toEqual({});
  });

  it("accepts any whole number of days from 1 to 90 and nothing else", () => {
    expect(commitmentCreate.parse({ ...goodCommitment, duration_days: 21 }).duration_days).toBe(21);
    expect(commitmentCreate.parse({ ...goodCommitment, duration_days: 1 }).duration_days).toBe(1);
    expect(() => commitmentCreate.parse({ ...goodCommitment, duration_days: 0 })).toThrow();
    expect(() => commitmentCreate.parse({ ...goodCommitment, duration_days: 91 })).toThrow();
    expect(() => commitmentCreate.parse({ ...goodCommitment, duration_days: 7.5 })).toThrow();
  });

  it("rejects an unknown timezone", () => {
    expect(() => commitmentCreate.parse({ ...goodCommitment, timezone: "Mars/Olympus" })).toThrow();
  });

  it("rejects a device id that is not uuid-shaped", () => {
    expect(() => commitmentCreate.parse({ ...goodCommitment, device_id: "1 or 1=1" })).toThrow();
  });
});

describe("snapshot", () => {
  it("rejects duplicate packages", () => {
    expect(() =>
      snapshot.parse({
        ...goodSnapshot,
        apps: [goodSnapshot.apps[0], goodSnapshot.apps[0]],
      }),
    ).toThrow(/duplicate/);
  });

  it("rejects a package name that is not dotted", () => {
    expect(() =>
      snapshot.parse({ ...goodSnapshot, apps: [{ package: "instagram", label: "x", daily_limit_min: 1 }] }),
    ).toThrow();
  });

  it("rejects a malformed reset time", () => {
    expect(() => snapshot.parse({ ...goodSnapshot, reset_time: "4:00" })).toThrow();
    expect(() => snapshot.parse({ ...goodSnapshot, reset_time: "24:00" })).toThrow();
  });

  it("allows limit 0 (fully blocked)", () => {
    expect(
      snapshot.parse({ ...goodSnapshot, apps: [{ ...goodSnapshot.apps[0], daily_limit_min: 0 }] }).apps[0]
        ?.daily_limit_min,
    ).toBe(0);
  });
});

describe("eventCreate", () => {
  const base = { id: "0192f1c2-1234-7abc-8def-0123456789ab", occurred_at: "2026-09-03T14:02:11+06:00" };

  it("requires a reason on broken", () => {
    expect(() => eventCreate.parse({ ...base, type: "broken" })).toThrow(/reason/);
    expect(eventCreate.parse({ ...base, type: "broken", reason: "limit_exceeded" }).reason).toBe("limit_exceeded");
  });

  it("requires minutes on challenge_completed", () => {
    expect(() => eventCreate.parse({ ...base, type: "challenge_completed" })).toThrow(/minutes/);
  });

  it("refuses server-only event types from a device", () => {
    expect(() => eventCreate.parse({ ...base, type: "protection_lost" })).toThrow();
    expect(() => eventCreate.parse({ ...base, type: "started" })).toThrow();
  });

  it("requires an offset on occurred_at", () => {
    expect(() => eventCreate.parse({ ...base, type: "completed", occurred_at: "2026-09-03T14:02:11" })).toThrow();
  });
});
