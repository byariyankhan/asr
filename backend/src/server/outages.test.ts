import { describe, expect, it } from "vitest";
import { downtimeWithin, RECOVERY_GRACE_MS, uptimeBetween } from "./outages";

const MIN = 60_000;
const HOUR = 60 * MIN;
const t0 = new Date("2026-09-05T00:00:00Z");
const at = (ms: number) => new Date(t0.getTime() + ms);
const away = (fromMs: number, toMs: number) => ({ started_at: at(fromMs), ended_at: at(toMs) });

describe("silence measured while the server was there", () => {
  it("is the wall clock when the server never went away", () => {
    expect(uptimeBetween([], at(0), at(25 * HOUR))).toBe(25 * HOUR);
    expect(downtimeWithin([], at(0), at(25 * HOUR))).toBe(0);
  });

  it("takes an outage out of the silence, and the recovery grace after it", () => {
    // Silent from t0; the server was away from hour 2 to hour 22.
    const outages = [away(2 * HOUR, 22 * HOUR)];
    expect(downtimeWithin(outages, at(0), at(25 * HOUR))).toBe(20 * HOUR + RECOVERY_GRACE_MS);
    expect(uptimeBetween(outages, at(0), at(25 * HOUR))).toBe(5 * HOUR - RECOVERY_GRACE_MS);
  });

  it("only counts the part of an outage that falls inside the silence", () => {
    // The phone last spoke at hour 10, in the middle of the outage.
    const outages = [away(2 * HOUR, 22 * HOUR)];
    expect(uptimeBetween(outages, at(10 * HOUR), at(25 * HOUR))).toBe(15 * HOUR - 12 * HOUR - RECOVERY_GRACE_MS);
    // And the grace is clipped at "now" when the server only just came back.
    expect(uptimeBetween(outages, at(10 * HOUR), at(22 * HOUR + 10 * MIN))).toBe(0);
  });

  it("ignores an outage outside the silence entirely", () => {
    const outages = [away(-10 * HOUR, -3 * HOUR), away(30 * HOUR, 31 * HOUR)];
    expect(uptimeBetween(outages, at(0), at(25 * HOUR))).toBe(25 * HOUR);
  });

  it("does not count two outages close together twice", () => {
    // Twenty minutes apart: the grace after the first covers the second.
    const outages = [away(HOUR, 2 * HOUR), away(2 * HOUR + 20 * MIN, 3 * HOUR)];
    expect(downtimeWithin(outages, at(0), at(10 * HOUR))).toBe(2 * HOUR + RECOVERY_GRACE_MS);
  });

  it("is never negative, and empty windows are zero", () => {
    const outages = [away(HOUR, 2 * HOUR)];
    expect(uptimeBetween(outages, at(HOUR), at(2 * HOUR))).toBe(0);
    expect(uptimeBetween(outages, at(5 * HOUR), at(5 * HOUR))).toBe(0);
    expect(uptimeBetween(outages, at(6 * HOUR), at(5 * HOUR))).toBe(0);
  });
});
