import { APIError } from "better-auth/api";
import { db } from "./db/client";
import { queueNotification } from "./notifications";
import { HttpError, notFound } from "@/lib/http";

export const DELETION_GRACE_DAYS = 7;

// Everything the user has ever given us or done, as one JSON document.
export async function exportAccount(userId: string) {
  const user = await db
    .selectFrom("user")
    .select(["id", "name", "email", "emailVerified", "timezone", "notify_email", "notify_push", "date_of_birth", "country", "gender", "createdAt"])
    .where("id", "=", userId)
    .executeTakeFirst();
  if (!user) throw notFound("User");

  const [devices, pacts, events, activities, witnesses, reactions, notifications, summaries] = await Promise.all([
    db.selectFrom("device").select(["id", "install_id", "model", "os_version", "app_version", "protection_enabled", "last_heartbeat_at", "created_at"]).where("user_id", "=", userId).execute(),
    db.selectFrom("pact").selectAll().where("user_id", "=", userId).orderBy("created_at").execute(),
    db
      .selectFrom("pact_event")
      .innerJoin("pact", "pact.id", "pact_event.pact_id")
      .select(["pact_event.id", "pact_event.pact_id", "pact_event.type", "pact_event.reason", "pact_event.app_package", "pact_event.minutes", "pact_event.occurred_at", "pact_event.received_at", "pact_event.source"])
      .where("pact.user_id", "=", userId)
      .orderBy("pact_event.received_at")
      .execute(),
    db.selectFrom("activity").selectAll().where("user_id", "=", userId).orderBy("started_at").execute(),
    db
      .selectFrom("witness")
      .select(["id", "user_id", "witness_user_id", "relationship", "status", "notify_start", "notify_success", "notify_failure", "notify_digest", "roast_mode", "views_progress", "invited_at", "responded_at"])
      .where((eb) => eb.or([eb("user_id", "=", userId), eb("witness_user_id", "=", userId)]))
      .execute(),
    db
      .selectFrom("reaction")
      .innerJoin("witness", "witness.id", "reaction.witness_id")
      .select(["reaction.id", "reaction.witness_id", "reaction.event_id", "reaction.emoji", "reaction.created_at"])
      .where((eb) => eb.or([eb("witness.user_id", "=", userId), eb("witness.witness_user_id", "=", userId)]))
      .execute(),
    db.selectFrom("notification").select(["id", "kind", "title", "body", "channel", "status", "sent_at", "read_at", "created_at"]).where("recipient_id", "=", userId).orderBy("created_at").execute(),
    db
      .selectFrom("daily_summary")
      .innerJoin("pact", "pact.id", "daily_summary.pact_id")
      .select(["daily_summary.pact_id", "daily_summary.day", "daily_summary.app_package", "daily_summary.minutes_used", "daily_summary.limit_min", "daily_summary.earned_min"])
      .where("pact.user_id", "=", userId)
      .orderBy("daily_summary.day")
      .execute(),
  ]);

  return {
    exported_at: new Date().toISOString(),
    user,
    devices,
    pacts,
    events,
    activities,
    witnesses,
    reactions,
    notifications,
    daily_summaries: summaries,
  };
}

// Verifies the password by signing in through Better Auth (the only
// component that knows how the hash is stored), then throws that session
// away along with every other one. The row is hard-deleted by the watchdog
// after the grace window; signing in before then cancels it.
export async function requestAccountDeletion(userId: string, password: string, signIn: (email: string, password: string) => Promise<void>) {
  const user = await db.selectFrom("user").select(["id", "email", "name"]).where("id", "=", userId).executeTakeFirst();
  if (!user) throw notFound("User");

  try {
    await signIn(user.email, password);
  } catch (error) {
    if (error instanceof APIError) throw new HttpError(403, "invalid_password", "That password is not right.");
    throw error;
  }

  const now = new Date();
  await db.transaction().execute(async (trx) => {
    await trx.updateTable("user").set({ deleted_at: now, updatedAt: now }).where("id", "=", userId).execute();
    await trx.deleteFrom("session").where("userId", "=", userId).execute();
    await trx.updateTable("device").set({ fcm_token: null, protection_enabled: false, updated_at: now }).where("user_id", "=", userId).execute();

    const links = await trx
      .selectFrom("witness")
      .select(["id", "user_id", "witness_user_id"])
      .where((eb) => eb.or([eb("user_id", "=", userId), eb("witness_user_id", "=", userId)]))
      .where("status", "in", ["invited", "accepted"])
      .execute();
    if (links.length > 0) {
      await trx
        .updateTable("witness")
        .set({ status: "removed", updated_at: now })
        .where("id", "in", links.map((l) => l.id))
        .execute();
    }
    for (const link of links) {
      const other = link.user_id === userId ? link.witness_user_id : link.user_id;
      if (!other) continue;
      await queueNotification(trx, {
        recipientId: other,
        aboutUserId: userId,
        kind: "witness_removed",
        title: `${user.name} left Asr`,
        body: `${user.name} deleted their account, so you are no longer connected.`,
        deepLink: "/witnesses",
      });
    }
  });
  return { deletes_at: new Date(now.getTime() + DELETION_GRACE_DAYS * 86_400_000) };
}

export async function cancelPendingDeletion(userId: string): Promise<void> {
  await db
    .updateTable("user")
    .set({ deleted_at: null, updatedAt: new Date() })
    .where("id", "=", userId)
    .where("deleted_at", "is not", null)
    .execute();
}

// Better Auth's sign-in as a password check: succeeds or throws APIError.
export function signInCheck(authApi: { signInEmail: (args: { body: { email: string; password: string } }) => Promise<unknown> }) {
  return async (email: string, password: string) => {
    await authApi.signInEmail({ body: { email, password } });
  };
}
