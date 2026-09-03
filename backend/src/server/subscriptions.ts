import { sql } from "kysely";
import { db } from "./db/client";
import type { SubscriptionStatus } from "./db/schema";
import { verifyPurchase, type PlaySubscriptionState, type PurchaseVerifier } from "./play";
import { conflict, HttpError } from "@/lib/http";
import { newId } from "@/lib/uuid";

const STATUS_FOR_STATE: Record<PlaySubscriptionState, SubscriptionStatus> = {
  SUBSCRIPTION_STATE_PENDING: "pending",
  SUBSCRIPTION_STATE_ACTIVE: "active",
  SUBSCRIPTION_STATE_PAUSED: "paused",
  SUBSCRIPTION_STATE_IN_GRACE_PERIOD: "grace",
  SUBSCRIPTION_STATE_ON_HOLD: "on_hold",
  SUBSCRIPTION_STATE_CANCELED: "cancelled",
  SUBSCRIPTION_STATE_EXPIRED: "expired",
};

// "Cancelled" on Play means auto-renew is off, not that access stopped: the
// subscriber keeps what they paid for until the period ends. Entitlement is
// therefore status AND expiry, never status alone.
const ENTITLING: ReadonlySet<SubscriptionStatus> = new Set<SubscriptionStatus>(["active", "grace", "cancelled"]);

export function isEntitled(row: { status: SubscriptionStatus; expires_at: Date | null } | undefined): boolean {
  if (!row || !ENTITLING.has(row.status)) return false;
  return row.expires_at === null || row.expires_at > new Date();
}

// A user accumulates rows: old tokens replaced by upgrades, refunded ones,
// the live one. "Current" means the row that actually entitles them, and
// only failing that the most recent — ordering by expiry alone would let a
// long-dated expired row outrank the live subscription.
export async function currentSubscription(userId: string) {
  return db
    .selectFrom("subscription")
    .select(["id", "product_id", "status", "expires_at", "last_verified_at"])
    .where("user_id", "=", userId)
    .orderBy(
      sql`(status in ('active', 'grace', 'cancelled') and (expires_at is null or expires_at > now())) desc`,
    )
    .orderBy(sql`expires_at desc nulls last`)
    .orderBy("created_at", "desc")
    .executeTakeFirst();
}

export async function subscriptionStateFor(userId: string) {
  const row = await currentSubscription(userId);
  return {
    plan: isEntitled(row) ? "plus" : "free",
    status: row?.status ?? null,
    product_id: row?.product_id ?? null,
    expires_at: row?.expires_at ?? null,
  };
}

// The app hands over a purchase token; Play is asked what it currently
// means. We never trust the app's idea of the state, only the token's
// identity, so a replayed or edited request buys nothing.
export async function verifyAndStore(
  userId: string,
  purchaseToken: string,
  verify: PurchaseVerifier = verifyPurchase,
) {
  const owner = await db
    .selectFrom("subscription")
    .select(["user_id"])
    .where("purchase_token", "=", purchaseToken)
    .executeTakeFirst();
  if (owner && owner.user_id !== userId) {
    throw conflict("purchase_claimed", "That purchase belongs to another account.");
  }

  let purchase;
  try {
    purchase = await verify(purchaseToken);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    if (message === "play_unknown_token") {
      throw new HttpError(404, "unknown_purchase", "Google Play does not recognise that purchase.");
    }
    if (message === "play_not_configured") {
      throw new HttpError(503, "billing_unavailable", "Billing is not configured on this server.");
    }
    console.error("[play] verification failed:", message);
    throw new HttpError(502, "billing_upstream", "Could not reach Google Play. Try again.");
  }

  const status = STATUS_FOR_STATE[purchase.subscriptionState] ?? "expired";
  const now = new Date();

  await db.transaction().execute(async (trx) => {
    await trx
      .insertInto("subscription")
      .values({
        id: newId(),
        user_id: userId,
        product_id: purchase.productId ?? "unknown",
        purchase_token: purchaseToken,
        status,
        expires_at: purchase.expiresAt,
        last_verified_at: now,
        raw: JSON.stringify(purchase.raw),
      })
      .onConflict((oc) =>
        oc.column("purchase_token").doUpdateSet({
          product_id: purchase.productId ?? "unknown",
          status,
          expires_at: purchase.expiresAt,
          last_verified_at: now,
          raw: JSON.stringify(purchase.raw),
          updated_at: now,
        }),
      )
      .execute();

    // An upgrade or downgrade issues a new token and points it at the old
    // one; the old purchase is finished the moment the new one exists.
    if (purchase.linkedPurchaseToken) {
      await trx
        .updateTable("subscription")
        .set({ status: "expired", updated_at: now })
        .where("purchase_token", "=", purchase.linkedPurchaseToken)
        .where("status", "!=", "expired")
        .execute();
    }
  });

  return subscriptionStateFor(userId);
}

// Real-time developer notifications tell us a token changed, never what it
// changed to. Re-verifying is both simpler and more trustworthy than
// mapping the twenty notification types, and it self-heals a missed event.
export async function refreshPurchase(purchaseToken: string, verify: PurchaseVerifier = verifyPurchase): Promise<boolean> {
  const row = await db
    .selectFrom("subscription")
    .select(["user_id"])
    .where("purchase_token", "=", purchaseToken)
    .executeTakeFirst();
  if (!row) return false; // a purchase we never saw: nothing to update
  try {
    await verifyAndStore(row.user_id, purchaseToken, verify);
    return true;
  } catch (error) {
    if (error instanceof HttpError && error.status === 404) {
      await db
        .updateTable("subscription")
        .set({ status: "expired", updated_at: new Date() })
        .where("purchase_token", "=", purchaseToken)
        .execute();
      return true;
    }
    throw error;
  }
}
