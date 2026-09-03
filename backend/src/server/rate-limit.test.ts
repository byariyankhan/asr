import { describe, expect, it } from "vitest";
import { checkRateLimit } from "./rate-limit";

// REDIS_URL is unset under vitest, so this exercises the in-process fallback
// that also takes over during a Redis outage.
describe("checkRateLimit (local fallback)", () => {
  it("allows up to the limit, then refuses with a reset time", async () => {
    const policy = { name: `t-${Date.now()}`, limit: 3, windowSeconds: 60 };
    const results = [];
    for (let i = 0; i < 4; i++) results.push(await checkRateLimit(policy, "user-a"));
    expect(results.map((r) => r.allowed)).toEqual([true, true, true, false]);
    expect(results[3]?.remaining).toBe(0);
    expect(results[3]?.resetSeconds).toBeGreaterThan(0);
    expect(results[3]?.resetSeconds).toBeLessThanOrEqual(60);
  });

  it("keeps identities separate", async () => {
    const policy = { name: `t2-${Date.now()}`, limit: 1, windowSeconds: 60 };
    expect((await checkRateLimit(policy, "a")).allowed).toBe(true);
    expect((await checkRateLimit(policy, "b")).allowed).toBe(true);
    expect((await checkRateLimit(policy, "a")).allowed).toBe(false);
  });
});
