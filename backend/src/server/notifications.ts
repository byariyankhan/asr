import type { Kysely, Transaction } from "kysely";
import { after } from "next/server";
import type { Database } from "./db/schema";
import { relationshipCopy, type WitnessEvent } from "./witness-copy";
import type { EventReason } from "@/lib/schemas";
import { newId } from "@/lib/uuid";

type Db = Transaction<Database> | Kysely<Database>;

export type WitnessKind =
  | "pact_started"
  | "pact_completed"
  | "pact_broken"
  | "protection_lost"
  | "uninstalled"
  | "pact_moved"
  | "protection_off"
  | "time_earned";
export type NotificationKind = WitnessKind | "witness_accepted" | "witness_removed" | "reaction" | "activity_failed";

const PREF_FOR_KIND: Record<
  WitnessKind,
  "notify_start" | "notify_success" | "notify_failure" | "views_progress"
> = {
  pact_started: "notify_start",
  pact_completed: "notify_success",
  pact_broken: "notify_failure",
  protection_lost: "notify_failure",
  uninstalled: "notify_failure",
  // Earning time is not a failure and not a milestone -- it is the pact
  // running as designed. It goes to the witnesses who asked to see progress,
  // and to nobody who only wanted to hear about the ending.
  time_earned: "views_progress",
  // Moving a challenge to another phone is not a failure and not an ending,
  // so it does not go to somebody who only asked to hear how it turned out.
  // It goes to the witnesses watching it happen -- because it is the one
  // move that could be an escape. A challenge runs on one phone, and taking
  // it onto a handset nobody uses would leave the real one unblocked with
  // nothing on the record. This is that record.
  pact_moved: "views_progress",
  // A challenge with nothing enforcing it for two hours: it landed on a
  // phone nobody granted the permissions on, or the permissions were taken
  // away on the phone it was already on. This one is a failure: there is a
  // live challenge and nothing enforcing it, which is the exact thing a
  // witness signed up to be told about.
  protection_off: "notify_failure",
};

/**
 * Reasons that mean the person got out from under the pact.
 *
 * Removing Asr or switching protection off. Every line of the abandoned copy
 * says some version of "they removed Asr", because that is what these are.
 */
const ABANDONED: ReadonlySet<string> = new Set<EventReason>([
  "app_removed",
  "protection_disabled",
]);

/**
 * The voice each kind is spoken in.
 *
 * `pact_broken` is three things. A limit somebody blew past is
 * `limit_broken`. A pact somebody switched off or deleted their way out of
 * is `challenge_abandoned`. And somebody who opened the app and pressed
 * Give up is `challenge_given_up` -- the same ending, reached by the
 * opposite act, and telling their mother they uninstalled it would be false
 * about the one person who was honest about stopping.
 */
export function eventForKind(kind: WitnessKind, reason?: EventReason | null): WitnessEvent {
  switch (kind) {
    case "time_earned": return "time_earned";
    case "uninstalled": return "challenge_abandoned";
    case "pact_broken":
      if (reason === "user_gave_up") return "challenge_given_up";
      if (reason && ABANDONED.has(reason)) return "challenge_abandoned";
      return "limit_broken";
    case "pact_started": return "pact_started";
    case "pact_completed": return "pact_completed";
    case "pact_moved": return "pact_moved";
    case "protection_off": return "protection_off";
    case "protection_lost": return "protection_lost";
  }
}

// One queued push per accepted witness who asked for this kind. Delivery
// (FCM / Resend) is the watchdog's job; this only writes the ledger rows,
// and notification_dedupe_idx makes a retry harmless.
export async function queueWitnessNotifications(
  trx: Db,
  args: {
    userId: string;
    eventId: string;
    kind: WitnessKind;
    pactId: string;
    /** Why the pact closed, when it closed. Decides whether "broken" means
     *  a limit blown past or a pact switched off. */
    reason?: EventReason | null;
    /** The app the limit belonged to, by label. `time_earned` only. */
    appName?: string;
    /** Minutes the activity awarded. `time_earned` only. */
    minutes?: number;
  },
): Promise<number> {
  const witnesses = await trx
    .selectFrom("witness")
    .innerJoin("user as u", "u.id", "witness.user_id")
    .select([
      "witness.witness_user_id",
      "witness.relationship",
      "u.name as user_name",
      // Asked for at sign-up and required before the profile counts as
      // complete, so these messages say "he", "she" or "they" about the
      // right person instead of guessing at one.
      "u.gender as user_gender",
    ])
    .where("witness.user_id", "=", args.userId)
    // The witnesses of *this* challenge. Somebody who watched a pact that
    // finished last month agreed to watch that one, and is not owed an
    // alert about a new one they were never told about.
    .where("witness.pact_id", "=", args.pactId)
    .where("witness.status", "=", "accepted")
    .where(`witness.${PREF_FOR_KIND[args.kind]}`, "=", true)
    .execute();

  const event = eventForKind(args.kind, args.reason);

  let queued = 0;
  for (const w of witnesses) {
    if (!w.witness_user_id) continue;
    // Every kind reads in the voice of the relationship, about a person
    // whose pronoun the profile knows.
    const copy = relationshipCopy(event, w.relationship, {
      userName: w.user_name,
      gender: w.user_gender,
      appName: args.appName ?? "daily",
      extraMinutes: args.minutes ?? 0,
    });
    const inserted = await queueNotification(trx, {
      recipientId: w.witness_user_id,
      aboutUserId: args.userId,
      eventId: args.eventId,
      kind: args.kind,
      ...copy,
      deepLink: `/witness/${args.userId}/pacts/${args.pactId}`,
    });
    if (inserted) queued += 1;
  }
  return queued;
}

export async function queueNotification(
  trx: Db,
  n: {
    recipientId: string;
    aboutUserId?: string;
    eventId?: string;
    kind: NotificationKind;
    title: string;
    body: string;
    deepLink?: string;
  },
): Promise<boolean> {
  const result = await trx
    .insertInto("notification")
    .values({
      id: newId(),
      recipient_id: n.recipientId,
      about_user_id: n.aboutUserId ?? null,
      event_id: n.eventId ?? null,
      channel: "push",
      kind: n.kind,
      title: n.title,
      body: n.body,
      deep_link: n.deepLink ?? null,
    })
    .onConflict((oc) => oc.doNothing())
    .executeTakeFirst();
  const inserted = result.numInsertedOrUpdatedRows === 1n;
  if (inserted) deliverAfterResponse();
  return inserted;
}

/**
 * Pushes the rows this request queued as soon as it has answered.
 *
 * Scheduled, not awaited: the row is still inside the caller's transaction
 * here, and the response should not wait on Firebase. Next runs the callback
 * once the response is sent, by which time the transaction has committed and
 * the sweep's own query can see the row. Outside a request -- the watchdog
 * queueing rows during its run, a test -- `after` has nothing to attach to
 * and throws; then the sweep that is already running delivers, as it always
 * did. The import is deferred to keep this module free of a cycle with the
 * watchdog, which imports it.
 */
function deliverAfterResponse(): void {
  try {
    after(async () => {
      const { deliverQueuedNow } = await import("./watchdog");
      await deliverQueuedNow();
    });
  } catch {
    // Not in a request. The sweep has it.
  }
}
