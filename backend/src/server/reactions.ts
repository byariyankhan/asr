import { db } from "./db/client";
import type { Emoji } from "./db/schema";
import { queueNotification } from "./notifications";
import { requireWitnessView } from "./witnesses";
import { notFound } from "@/lib/http";
import { isUuidLike, newId } from "@/lib/uuid";

const EMOJI_LABEL: Record<Emoji, string> = {
  laugh: "🤣",
  haha: "😂",
  shoe: "👞",
  tomato: "🍅",
  clap: "👏",
};

// A witness reacts to one of the user's events. One reaction per witness per
// event; sending again replaces it. The user is told (event_id is left null
// on the notification so several witnesses can react to the same event).
export async function react(callerId: string, witnessRowId: string, eventId: string, emoji: Emoji) {
  const row = await requireWitnessView(callerId, witnessRowId);
  if (!isUuidLike(eventId)) throw notFound("Event");
  const event = await db
    .selectFrom("pact_event")
    .innerJoin("pact", "pact.id", "pact_event.pact_id")
    .select(["pact_event.id", "pact_event.type", "pact.user_id"])
    .where("pact_event.id", "=", eventId)
    .where("pact.user_id", "=", row.user_id)
    .executeTakeFirst();
  if (!event) throw notFound("Event");

  const now = new Date();
  return db.transaction().execute(async (trx) => {
    const reaction = await trx
      .insertInto("reaction")
      .values({ id: newId(), witness_id: row.id, event_id: eventId, emoji })
      .onConflict((oc) => oc.columns(["witness_id", "event_id"]).doUpdateSet({ emoji, updated_at: now }))
      .returning(["id", "witness_id", "event_id", "emoji", "created_at", "updated_at"])
      .executeTakeFirstOrThrow();

    const witness = await trx.selectFrom("user").select("name").where("id", "=", callerId).executeTakeFirstOrThrow();
    await queueNotification(trx, {
      recipientId: row.user_id,
      aboutUserId: callerId,
      kind: "reaction",
      title: `${witness.name} reacted ${EMOJI_LABEL[emoji]}`,
      body:
        event.type === "completed"
          ? `${witness.name} saw you keep your word.`
          : `${witness.name} saw what happened. ${EMOJI_LABEL[emoji]}`,
      deepLink: `/witnesses`,
    });
    return reaction;
  });
}

export async function unreact(callerId: string, witnessRowId: string, eventId: string): Promise<void> {
  const row = await requireWitnessView(callerId, witnessRowId);
  if (!isUuidLike(eventId)) throw notFound("Event");
  const result = await db
    .deleteFrom("reaction")
    .where("witness_id", "=", row.id)
    .where("event_id", "=", eventId)
    .executeTakeFirst();
  if (result.numDeletedRows === 0n) throw notFound("Reaction");
}

// Reactions on my events, newest first, for the Witnesses screen.
export async function listMyReactions(userId: string, limit: number) {
  return db
    .selectFrom("reaction")
    .innerJoin("witness", "witness.id", "reaction.witness_id")
    .innerJoin("pact_event", "pact_event.id", "reaction.event_id")
    .innerJoin("pact", "pact.id", "pact_event.pact_id")
    .innerJoin("user as w", "w.id", "witness.witness_user_id")
    .select([
      "reaction.id",
      "reaction.emoji",
      "reaction.updated_at as reacted_at",
      "witness.id as witness_id",
      "witness.relationship",
      "w.id as witness_user_id",
      "w.name as witness_name",
      "pact_event.id as event_id",
      "pact_event.type as event_type",
      "pact_event.received_at as event_at",
      "pact.id as pact_id",
    ])
    .where("pact.user_id", "=", userId)
    .orderBy("reaction.updated_at", "desc")
    .limit(limit)
    .execute();
}
