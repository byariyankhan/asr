import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { newId } from "@/lib/uuid";
import type { PlayPurchase, PlaySubscriptionState, PurchaseVerifier } from "@/server/play";

const DATABASE_URL = process.env.DATABASE_URL;

describe.skipIf(!DATABASE_URL)("subscriptions", async () => {
  const { db } = await import("@/server/db/client");
  const { verifyAndStore, refreshPurchase, subscriptionStateFor, isEntitled } = await import("@/server/subscriptions");
  const { getMe } = await import("@/server/me");

  const alice = newId();
  const bob = newId();
  const HOUR = 3_600_000;

  const purchase = (state: PlaySubscriptionState, expiresAt: Date | null, linked?: string): PlayPurchase => ({
    subscriptionState: state,
    expiresAt,
    productId: "asr_plus_monthly",
    linkedPurchaseToken: linked ?? null,
    raw: { subscriptionState: state },
  });
  const verifier =
    (p: PlayPurchase): PurchaseVerifier =>
    async () =>
      p;
  const failing =
    (message: string): PurchaseVerifier =>
    async () => {
      throw new Error(message);
    };

  beforeAll(async () => {
    const now = new Date();
    await db
      .insertInto("user")
      .values(
        [
          [alice, "Alice"],
          [bob, "Bob"],
        ].map(([id, name]) => ({ id: id!, name: name!, email: `${id}@test.local`, emailVerified: false, createdAt: now, updatedAt: now })),
      )
      .execute();
  });

  afterAll(async () => {
    await db.deleteFrom("user").where("id", "in", [alice, bob]).execute();
    await db.destroy();
  });

  it("stores what Play says, not what the client claims", async () => {
    const expires = new Date(Date.now() + 30 * 24 * HOUR);
    const state = await verifyAndStore(alice, "token-a", verifier(purchase("SUBSCRIPTION_STATE_ACTIVE", expires)));
    expect(state).toMatchObject({ plan: "plus", status: "active", product_id: "asr_plus_monthly" });
  });

  it("re-verifying the same token updates in place", async () => {
    const expires = new Date(Date.now() + 40 * 24 * HOUR);
    await verifyAndStore(alice, "token-a", verifier(purchase("SUBSCRIPTION_STATE_IN_GRACE_PERIOD", expires)));
    const rows = await db.selectFrom("subscription").select(["status"]).where("purchase_token", "=", "token-a").execute();
    expect(rows).toEqual([{ status: "grace" }]);
    expect((await subscriptionStateFor(alice)).plan).toBe("plus");
  });

  it("keeps a cancelled subscription entitled until it expires", () => {
    const future = new Date(Date.now() + HOUR);
    expect(isEntitled({ status: "cancelled", expires_at: future })).toBe(true);
    expect(isEntitled({ status: "cancelled", expires_at: new Date(Date.now() - HOUR) })).toBe(false);
    expect(isEntitled({ status: "on_hold", expires_at: future })).toBe(false);
    expect(isEntitled({ status: "paused", expires_at: future })).toBe(false);
    expect(isEntitled({ status: "pending", expires_at: future })).toBe(false);
    expect(isEntitled(undefined)).toBe(false);
  });

  it("expires the old token when an upgrade links to it", async () => {
    const expires = new Date(Date.now() + 60 * 24 * HOUR);
    await verifyAndStore(alice, "token-b", verifier(purchase("SUBSCRIPTION_STATE_ACTIVE", expires, "token-a")));
    const old = await db.selectFrom("subscription").select("status").where("purchase_token", "=", "token-a").executeTakeFirstOrThrow();
    expect(old.status).toBe("expired");
    expect((await subscriptionStateFor(alice)).status).toBe("active");
  });

  it("refuses a purchase already claimed by another account", async () => {
    await expect(verifyAndStore(bob, "token-b", verifier(purchase("SUBSCRIPTION_STATE_ACTIVE", null)))).rejects.toMatchObject({
      status: 409,
      code: "purchase_claimed",
    });
  });

  it("maps upstream failures to honest statuses and stores nothing", async () => {
    await expect(verifyAndStore(bob, "token-x", failing("play_unknown_token"))).rejects.toMatchObject({ status: 404, code: "unknown_purchase" });
    await expect(verifyAndStore(bob, "token-x", failing("play_not_configured"))).rejects.toMatchObject({ status: 503 });
    await expect(verifyAndStore(bob, "token-x", failing("network is on fire"))).rejects.toMatchObject({ status: 502 });
    expect(await db.selectFrom("subscription").select("id").where("purchase_token", "=", "token-x").executeTakeFirst()).toBeUndefined();
  });

  it("a webhook refresh re-reads Play; an unknown token is ignored", async () => {
    expect(await refreshPurchase("never-seen", verifier(purchase("SUBSCRIPTION_STATE_ACTIVE", null)))).toBe(false);
    expect(await refreshPurchase("token-b", verifier(purchase("SUBSCRIPTION_STATE_EXPIRED", new Date(Date.now() - HOUR))))).toBe(true);
    expect((await subscriptionStateFor(alice)).plan).toBe("free");
  });

  it("a refund voids the row rather than leaving it entitled", async () => {
    await verifyAndStore(alice, "token-c", verifier(purchase("SUBSCRIPTION_STATE_ACTIVE", new Date(Date.now() + 24 * HOUR))));
    expect((await subscriptionStateFor(alice)).plan).toBe("plus");
    expect(await refreshPurchase("token-c", failing("play_unknown_token"))).toBe(true);
    expect((await subscriptionStateFor(alice)).plan).toBe("free");
  });

  it("shows up on /me", async () => {
    expect((await getMe(bob)).subscription).toEqual({ plan: "free", status: null, product_id: null, expires_at: null });
    expect((await getMe(alice)).subscription).toMatchObject({ plan: "free", status: "expired" });
  });
});
