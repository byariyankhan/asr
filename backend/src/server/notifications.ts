import type { Kysely, Transaction } from "kysely";
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
 * Which of the two relationship-aware events this is, or null for the ones
 * that keep the older plain copy.
 *
 * `pact_broken` is three things. A limit somebody blew past keeps the plain
 * copy. A pact somebody switched off or deleted their way out of is
 * `challenge_abandoned`. And somebody who opened the app and pressed Give up
 * is `challenge_given_up` -- the same ending, reached by the opposite act,
 * and telling their mother they uninstalled it would be false about the one
 * person who was honest about stopping.
 */
function relationshipEventFor(kind: WitnessKind, reason?: EventReason | null): WitnessEvent | null {
  if (kind === "time_earned") return "time_earned";
  if (kind === "uninstalled") return "challenge_abandoned";
  if (kind === "pact_broken" && reason === "user_gave_up") return "challenge_given_up";
  if (kind === "pact_broken" && reason && ABANDONED.has(reason)) return "challenge_abandoned";
  return null;
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
      "witness.roast_mode",
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

  const event = relationshipEventFor(args.kind, args.reason);

  let queued = 0;
  for (const w of witnesses) {
    if (!w.witness_user_id) continue;
    // The two events this product has words for read in the voice of the
    // relationship. Everything else keeps the plainer copy: nobody has
    // written nine versions of "they started a pact", and inventing them
    // would be guessing at a tone.
    const copy = event
      ? relationshipCopy(event, w.relationship, {
          userName: w.user_name,
          gender: w.user_gender,
          appName: args.appName ?? "daily",
          extraMinutes: args.minutes ?? 0,
        })
      : witnessCopy(args.kind, w.user_name, w.roast_mode);
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
  return result.numInsertedOrUpdatedRows === 1n;
}

/**
 * The plain copy, for the events nobody has written relationship voices for.
 *
 * `uninstalled` and `time_earned` never reach here in practice --
 * relationshipEventFor claims both -- but they stay answered, because a
 * function that can return undefined for a case somebody adds later is a
 * notification that silently does not send.
 */
export function witnessCopy(kind: WitnessKind, name: string, roast: boolean): { title: string; body: string } {
  switch (kind) {
    case "time_earned":
      return { title: `${name} earned more time`, body: `${name} earned extra minutes inside the rules.` };
    case "pact_started":
      return { title: `${name} made a pact`, body: `${name} started a new pact and named you as a witness.` };
    case "pact_completed":
      return { title: `${name} kept their word`, body: `${name} finished their pact. Tell them you noticed.` };
    case "pact_broken":
      return roast
        ? { title: `${name} folded`, body: `${name} broke their pact. You know what to do.` }
        : { title: `${name} broke their pact`, body: `${name} didn't make it this time. A word from you might help.` };
    case "uninstalled":
      return roast
        ? { title: `${name} deleted the app`, body: `${name} removed Asr mid-pact. Bold move.` }
        : { title: `${name} removed Asr`, body: `${name} uninstalled the app during their pact, so it ended as broken.` };
    case "pact_moved":
      return roast
        ? {
            title: `${name} changed phones`,
            body: `${name} moved the challenge to another phone. Same days, same limits, new handset.`,
          }
        : {
            title: `${name} moved to another phone`,
            body: `${name}'s challenge is being kept on a different phone now. The days and the limits are unchanged.`,
          };
    case "protection_off":
      return roast
        ? {
            title: `${name} switched blocking off`,
            body: `${name}'s phone has not been blocking anything for two hours. The challenge is still running. Nothing is stopping the apps.`,
          }
        : {
            title: `${name}'s challenge is not being enforced`,
            body: `Blocking has been off on ${name}'s phone for two hours. The challenge is running, but nothing is stopping the apps.`,
          };
    case "protection_lost":
      return roast
        ? { title: `${name} went dark`, body: `${name}'s protection has been off for a day. Suspicious.` }
        : { title: `${name}'s protection stopped`, body: `We haven't heard from ${name}'s phone in a day. Check in with them.` };
  }
}
