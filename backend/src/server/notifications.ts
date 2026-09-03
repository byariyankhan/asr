import type { Kysely, Transaction } from "kysely";
import type { Database } from "./db/schema";
import { newId } from "@/lib/uuid";

type Db = Transaction<Database> | Kysely<Database>;

export type WitnessKind = "pact_started" | "pact_completed" | "pact_broken" | "protection_lost" | "uninstalled";
export type NotificationKind = WitnessKind | "witness_accepted" | "witness_removed" | "reaction" | "activity_failed";

const PREF_FOR_KIND: Record<WitnessKind, "notify_start" | "notify_success" | "notify_failure"> = {
  pact_started: "notify_start",
  pact_completed: "notify_success",
  pact_broken: "notify_failure",
  protection_lost: "notify_failure",
  uninstalled: "notify_failure",
};

// One queued push per accepted witness who asked for this kind. Delivery
// (FCM / Resend) is the watchdog's job; this only writes the ledger rows,
// and notification_dedupe_idx makes a retry harmless.
export async function queueWitnessNotifications(
  trx: Db,
  args: { userId: string; eventId: string; kind: WitnessKind; pactId: string },
): Promise<number> {
  const witnesses = await trx
    .selectFrom("witness")
    .innerJoin("user as u", "u.id", "witness.user_id")
    .select(["witness.witness_user_id", "witness.roast_mode", "u.name as user_name"])
    .where("witness.user_id", "=", args.userId)
    .where("witness.status", "=", "accepted")
    .where(`witness.${PREF_FOR_KIND[args.kind]}`, "=", true)
    .execute();

  let queued = 0;
  for (const w of witnesses) {
    if (!w.witness_user_id) continue;
    const copy = witnessCopy(args.kind, w.user_name, w.roast_mode);
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

export function witnessCopy(kind: WitnessKind, name: string, roast: boolean): { title: string; body: string } {
  switch (kind) {
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
    case "protection_lost":
      return roast
        ? { title: `${name} went dark`, body: `${name}'s protection has been off for a day. Suspicious.` }
        : { title: `${name}'s protection stopped`, body: `We haven't heard from ${name}'s phone in a day. Check in with them.` };
  }
}
