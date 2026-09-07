import { sql } from "kysely";
import { db, isUniqueViolation } from "./db/client";
import { queueWitnessNotifications } from "./notifications";
import { requireOwnedPact } from "./pacts";
import { conflict, notFound } from "@/lib/http";
import type { ActivityComplete, ActivityCreate, Snapshot } from "@/lib/schemas";
import { addDays, phoneZone } from "@/lib/time";
import { isUuidLike } from "@/lib/uuid";

export const activityColumns = [
  "id",
  "pact_id",
  "user_id",
  "type",
  "app_package",
  "target",
  "reward_min",
  "started_at",
  "deadline_at",
  "status",
  "ended_at",
  "created_at",
  "updated_at",
] as const;

type Rule = { reward_min: number; daily_cap_min: number; target?: number; target_min?: number; wait_min?: number };

function ruleFor(snapshot: Snapshot, type: ActivityCreate["type"]): Rule | undefined {
  return snapshot.activities[type];
}

/**
 * The app's name as somebody would say it, from the package the phone sent.
 *
 * The pact's snapshot is the right source: it is the list they chose,
 * frozen when the pact started, so the label cannot drift or disappear
 * because an app was uninstalled afterwards. "their limit" when there is no
 * package or no match, "daily" -- which is vaguer than naming the app and
 * reads correctly in every template that uses it: "the daily limit",
 * "ran out of daily time", "reached his daily limit".
 */
export function appLabel(snapshot: Snapshot, packageName: string | null): string {
  if (!packageName) return "daily";
  return snapshot.apps.find((a) => a.package === packageName)?.label ?? "daily";
}

function targetOf(rule: Rule): number {
  return rule.target ?? rule.target_min ?? rule.wait_min ?? 0;
}

// Start an activity. Target and reward come from the pact's locked rules,
// never from the request, and the day's cap counts pending as well as
// completed activities so a burst of starts cannot exceed it. The id is
// device-generated, so a retry returns the existing row.
//
// The cap is per app, per day, across both kinds of activity -- the rule
// the phone enforces and the one every screen states ("the most bonus time
// Instagram can have today"), and the one the daily summary already holds
// each app's earned minutes to. It used to be per kind across all apps,
// which refused the third walk of a day with two apps while the phone,
// counting per app, had already awarded it: minutes on the phone with
// nothing on the ledger and nothing said to the witnesses.
export async function createActivity(userId: string, pactId: string, input: ActivityCreate) {
  const pact = await requireOwnedPact(userId, pactId);

  const existing = await db.selectFrom("activity").select(activityColumns).where("id", "=", input.id).executeTakeFirst();
  if (existing) {
    if (existing.pact_id !== pactId) throw conflict("activity_id_reused", "That activity id belongs to another pact.");
    return { activity: existing, created: false };
  }

  if (pact.status !== "active") throw conflict("pact_closed", `This pact is already ${pact.status}.`);
  const rule = ruleFor(pact.snapshot, input.type);
  if (!rule) throw conflict("activity_not_allowed", "This pact does not include that activity.");

  const startedAt = new Date(input.started_at);
  const deadlineAt = new Date(input.deadline_at);
  const latest = addDays(startedAt, 1);
  if (deadlineAt > latest) throw conflict("deadline_too_far", "An activity must end within 24 hours of starting.");

  const zone = phoneZone(pact);
  const appPackage = input.app_package ?? null;
  const { used } = await db
    .selectFrom("activity")
    .select((eb) => eb.fn.coalesce(eb.fn.sum<number>("reward_min"), sql<number>`0`).as("used"))
    .where("pact_id", "=", pactId)
    .where((eb) => (appPackage === null ? eb("app_package", "is", null) : eb("app_package", "=", appPackage)))
    .where("status", "in", ["pending", "completed"])
    .where(sql<boolean>`(started_at at time zone ${zone})::date = (${startedAt} at time zone ${zone})::date`)
    .executeTakeFirstOrThrow();
  if (Number(used) + rule.reward_min > rule.daily_cap_min) {
    throw conflict("daily_cap_reached", `You have earned all the bonus time ${appLabel(pact.snapshot, appPackage)} can have today.`);
  }

  try {
    const activity = await db
      .insertInto("activity")
      .values({
        id: input.id,
        pact_id: pactId,
        user_id: userId,
        type: input.type,
        app_package: input.app_package ?? null,
        target: targetOf(rule),
        reward_min: rule.reward_min,
        started_at: startedAt,
        deadline_at: deadlineAt,
      })
      .returning(activityColumns)
      .executeTakeFirstOrThrow();
    return { activity, created: true };
  } catch (error) {
    if (isUniqueViolation(error, "activity_pkey")) {
      const winner = await db.selectFrom("activity").select(activityColumns).where("id", "=", input.id).executeTakeFirstOrThrow();
      return { activity: winner, created: false };
    }
    throw error;
  }
}

async function requireOwnedActivity(userId: string, activityId: string) {
  if (!isUuidLike(activityId)) throw notFound("Activity");
  const activity = await db
    .selectFrom("activity")
    .select(activityColumns)
    .where("id", "=", activityId)
    .where("user_id", "=", userId)
    .executeTakeFirst();
  if (!activity) throw notFound("Activity");
  return activity;
}

// Completion writes the activity_completed ledger event carrying the reward;
// the event id is the idempotency key, same as every device event.
export async function completeActivity(userId: string, activityId: string, input: ActivityComplete) {
  const activity = await requireOwnedActivity(userId, activityId);

  const existingEvent = await db
    .selectFrom("pact_event")
    .select(["id", "pact_id", "type", "minutes", "occurred_at", "received_at"])
    .where("id", "=", input.event_id)
    .executeTakeFirst();
  if (existingEvent) {
    if (existingEvent.pact_id !== activity.pact_id) throw conflict("event_id_reused", "That event id belongs to another pact.");
    return { activity, event: existingEvent, created: false };
  }

  if (activity.status !== "pending") throw conflict("activity_closed", `This activity is already ${activity.status}.`);
  const pact = await requireOwnedPact(userId, activity.pact_id);
  if (pact.status !== "active") throw conflict("pact_closed", `This pact is already ${pact.status}.`);

  const occurredAt = new Date(input.occurred_at);
  try {
    return await db.transaction().execute(async (trx) => {
      const updated = await trx
        .updateTable("activity")
        .set({ status: "completed", ended_at: occurredAt, updated_at: new Date() })
        .where("id", "=", activityId)
        .where("status", "=", "pending")
        .returning(activityColumns)
        .executeTakeFirst();
      if (!updated) throw conflict("activity_closed", "This activity was closed by an earlier request.");

      const event = await trx
        .insertInto("pact_event")
        .values({
          id: input.event_id,
          pact_id: activity.pact_id,
          device_id: pact.device_id,
          type: "activity_completed",
          reason: null,
          app_package: null,
          minutes: activity.reward_min,
          occurred_at: occurredAt,
          source: "device",
        })
        .returning(["id", "pact_id", "type", "minutes", "occurred_at", "received_at"])
        .executeTakeFirstOrThrow();

      // The earn is finished, so the minutes are real and are about to be
      // spent. Witnesses hear about it here rather than when the activity
      // started: a walk somebody abandons halfway is not news.
      await queueWitnessNotifications(trx, {
        userId,
        eventId: event.id,
        kind: "time_earned",
        pactId: activity.pact_id,
        appName: appLabel(pact.snapshot, updated.app_package),
        minutes: updated.reward_min,
      });
      return { activity: updated, event, created: true };
    });
  } catch (error) {
    if (isUniqueViolation(error, "pact_event_pkey")) {
      const event = await db
        .selectFrom("pact_event")
        .select(["id", "pact_id", "type", "minutes", "occurred_at", "received_at"])
        .where("id", "=", input.event_id)
        .executeTakeFirstOrThrow();
      const current = await requireOwnedActivity(userId, activityId);
      return { activity: current, event, created: false };
    }
    throw error;
  }
}

export async function cancelActivity(userId: string, activityId: string): Promise<void> {
  await requireOwnedActivity(userId, activityId);
  const now = new Date();
  const result = await db
    .updateTable("activity")
    .set({ status: "cancelled", ended_at: now, updated_at: now })
    .where("id", "=", activityId)
    .where("user_id", "=", userId)
    .where("status", "=", "pending")
    .executeTakeFirst();
  if (result.numUpdatedRows === 0n) throw conflict("activity_closed", "This activity is not pending.");
}

export async function listActivities(userId: string, pactId: string) {
  await requireOwnedPact(userId, pactId);
  return db
    .selectFrom("activity")
    .select(activityColumns)
    .where("pact_id", "=", pactId)
    .orderBy("started_at", "desc")
    .execute();
}
