import { describe, expect, it } from "vitest";
import { assertRateLimit, assertRateLimitRoom, checkRateLimit, consumeRateLimit } from "./rate-limit";

// With REDIS_URL unset (a developer's machine) this exercises the in-process
// fallback that also takes over during a Redis outage; with it set (CI, which
// runs a Redis service) the same assertions run against Redis. Policy names
// carry a timestamp so a shared Redis never sees a stale counter.
describe("checkRateLimit (Redis when configured, local fallback otherwise)", () => {
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

/**
 * A limit on what somebody made, not on what they asked for.
 *
 * Written after somebody's invite quota was spent entirely on attempts the
 * server refused: creating an invite was charged before the request body
 * was parsed, and every refusal for having no active challenge -- our bug --
 * took one of the day's twenty with it.
 */
describe("room, then consume", () => {
  it("checking for room does not spend any", async () => {
    const policy = { name: `t3-${Date.now()}`, limit: 2, windowSeconds: 60 };
    for (let i = 0; i < 10; i++) await assertRateLimitRoom(policy, "a");
    // Ten looks, nothing taken: the two are still there.
    await consumeRateLimit(policy, "a");
    await consumeRateLimit(policy, "a");
    await expect(assertRateLimitRoom(policy, "a")).rejects.toMatchObject({ status: 429 });
  });

  it("says how long to wait, not only that it will not", async () => {
    const policy = { name: `t4-${Date.now()}`, limit: 1, windowSeconds: 7200 };
    await consumeRateLimit(policy, "a");
    await expect(assertRateLimitRoom(policy, "a")).rejects.toThrow(/Try again in about \d+ hours?\./);
  });

  it("the ordinary path says it too", async () => {
    const policy = { name: `t5-${Date.now()}`, limit: 1, windowSeconds: 60 };
    await assertRateLimit(policy, "a");
    await expect(assertRateLimit(policy, "a")).rejects.toThrow(/Try again in a minute\./);
  });

  it("keeps identities separate when looking as well as spending", async () => {
    const policy = { name: `t6-${Date.now()}`, limit: 1, windowSeconds: 60 };
    await consumeRateLimit(policy, "a");
    await assertRateLimitRoom(policy, "b");
    await expect(assertRateLimitRoom(policy, "a")).rejects.toMatchObject({ status: 429 });
  });
});
