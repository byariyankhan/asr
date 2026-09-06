import { describe, expect, it } from "vitest";
import { pactAppAdd, pactCreate, eventCreate, snapshot } from "./schemas";

const goodSnapshot = {
  apps: [{ package: "com.instagram.android", label: "Instagram", daily_limit_min: 30 }],
  reset_time: "04:00",
};

const goodPact = {
  device_id: "0192f1c2-1234-7abc-8def-0123456789ab",
  duration_days: 7,
  timezone: "Asia/Dhaka",
  snapshot: goodSnapshot,
};

describe("pactCreate", () => {
  it("accepts a valid body and defaults activities to {}", () => {
    const parsed = pactCreate.parse(goodPact);
    expect(parsed.snapshot.activities).toEqual({});
  });

  it("accepts any whole number of days from 1 to 90 and nothing else", () => {
    expect(pactCreate.parse({ ...goodPact, duration_days: 21 }).duration_days).toBe(21);
    expect(pactCreate.parse({ ...goodPact, duration_days: 1 }).duration_days).toBe(1);
    expect(() => pactCreate.parse({ ...goodPact, duration_days: 0 })).toThrow();
    expect(() => pactCreate.parse({ ...goodPact, duration_days: 91 })).toThrow();
    expect(() => pactCreate.parse({ ...goodPact, duration_days: 7.5 })).toThrow();
  });

  it("rejects an unknown timezone", () => {
    expect(() => pactCreate.parse({ ...goodPact, timezone: "Mars/Olympus" })).toThrow();
  });

  it("rejects a device id that is not uuid-shaped", () => {
    expect(() => pactCreate.parse({ ...goodPact, device_id: "1 or 1=1" })).toThrow();
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

  it("requires minutes on activity_completed", () => {
    expect(() => eventCreate.parse({ ...base, type: "activity_completed" })).toThrow(/minutes/);
  });

  it("refuses server-only event types from a device", () => {
    expect(() => eventCreate.parse({ ...base, type: "protection_lost" })).toThrow();
    expect(() => eventCreate.parse({ ...base, type: "started" })).toThrow();
  });

  it("requires an offset on occurred_at", () => {
    expect(() => eventCreate.parse({ ...base, type: "completed", occurred_at: "2026-09-03T14:02:11" })).toThrow();
  });
});

/**
 * The body that brings one more app under a limit while a challenge runs.
 * Same shape as an app in the snapshot, minus the one field the server
 * owns: `added_on` is stamped from the pact's own calendar, never taken
 * from the phone, so a client cannot backdate an app into days it was not
 * under a limit on.
 */
describe("pactAppAdd", () => {
  const tiktok = { package: "com.zhiliaoapp.musically", label: "TikTok", daily_limit_min: 20 };

  it("accepts an app as the snapshot would", () => {
    expect(pactAppAdd.parse(tiktok)).toEqual(tiktok);
  });

  it("drops an added_on the client sends rather than believing it", () => {
    expect(pactAppAdd.parse({ ...tiktok, added_on: "2020-01-01" })).toEqual(tiktok);
  });

  it("holds the same limits as the snapshot: 0 to 1440 whole minutes, a real package name", () => {
    expect(pactAppAdd.parse({ ...tiktok, daily_limit_min: 0 }).daily_limit_min).toBe(0);
    expect(() => pactAppAdd.parse({ ...tiktok, daily_limit_min: 1441 })).toThrow();
    expect(() => pactAppAdd.parse({ ...tiktok, daily_limit_min: 7.5 })).toThrow();
    expect(() => pactAppAdd.parse({ ...tiktok, package: "tiktok" })).toThrow();
    expect(() => pactAppAdd.parse({ ...tiktok, label: "" })).toThrow();
  });
});

describe("snapshot apps added later", () => {
  const started = { package: "com.instagram.android", label: "Instagram", daily_limit_min: 30 };
  const added = { package: "com.zhiliaoapp.musically", label: "TikTok", daily_limit_min: 20, added_on: "2026-09-06" };

  it("carry the day they came in on, and the originals carry nothing", () => {
    const parsed = snapshot.parse({ apps: [started, added], reset_time: "00:00" });
    expect(parsed.apps.map((a) => a.added_on)).toEqual([undefined, "2026-09-06"]);
  });

  it("rejects a day that is not a day", () => {
    expect(() =>
      snapshot.parse({ apps: [{ ...added, added_on: "6 Sep 2026" }], reset_time: "00:00" }),
    ).toThrow();
  });
});
