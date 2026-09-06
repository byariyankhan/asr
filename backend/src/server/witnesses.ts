import type { Kysely, Transaction } from "kysely";
import { imagePath } from "./avatar";
import type { Database } from "./db/schema";
import { db, isUniqueViolation } from "./db/client";
import { inviteEmail, sendEmail } from "./email";
import { witnessAcceptedCopy } from "./witness-copy";
import { queueNotification } from "./notifications";
import { conflict, forbidden, notFound } from "@/lib/http";
import { generateInviteCode, isInviteCode } from "@/lib/invite-code";
import type { WitnessInvite, WitnessPatch } from "@/lib/schemas";
import { isUuidLike, newId } from "@/lib/uuid";

export const witnessColumns = [
  "id",
  "user_id",
  "pact_id",
  "witness_user_id",
  "invite_code",
  "invite_email",
  "relationship",
  "status",
  "notify_start",
  "notify_success",
  "notify_failure",
  "notify_digest",
  "roast_mode",
  "views_progress",
  "invited_at",
  "responded_at",
  "created_at",
  "updated_at",
] as const;

type Db = Kysely<Database> | Transaction<Database>;

const SITE_URL = () => process.env.PUBLIC_SITE_URL ?? "https://joinasr.io";

export function inviteUrl(code: string): string {
  return `${SITE_URL()}/w/${code}`;
}

/**
 * Relationships only one person can hold.
 *
 * Nobody has two mothers. A second person accepting as "mother" is not a
 * second witness, it is a wrong one — and since the invite link is a code
 * that gets forwarded through WhatsApp, "whoever opens it first" is exactly
 * how a stranger ends up listed as somebody's wife.
 *
 * Brother, sister, friend, mentor and colleague are plural: several people
 * can hold each, and each gets their own invite.
 */
const SINGULAR = new Set(["mother", "father", "husband", "wife"]);

const ARTICLE: Record<string, string> = {
  mother: "a mother",
  father: "a father",
  husband: "a husband",
  wife: "a wife",
};

/**
 * Refuses a second holder of a singular relationship.
 *
 * Checked against accepted rows only, so an invite that was never answered
 * can be sent again — the common case is a mother who has not opened the
 * link yet, and refusing to re-send to her would be the app enforcing a rule
 * against the wrong person.
 */
async function assertSingularFree(
  db_: Db,
  pactId: string,
  relationship: string | null,
  excludeWitnessId?: string,
): Promise<void> {
  if (!relationship || !SINGULAR.has(relationship)) return;
  let query = db_
    .selectFrom("witness")
    .select("id")
    .where("pact_id", "=", pactId)
    .where("relationship", "=", relationship as never)
    .where("status", "=", "accepted");
  if (excludeWitnessId) query = query.where("id", "!=", excludeWitnessId);
  const taken = await query.executeTakeFirst();
  if (taken) {
    throw conflict(
      "relationship_taken",
      `You already have ${ARTICLE[relationship] ?? "one"} as a witness.`,
    );
  }
}

// A new invite row. The email, if given, is sent by the delivery worker;
// here it is only stored.
export async function createInvite(userId: string, input: WitnessInvite) {
  // A witness is invited to a challenge, and the invitation names how many
  // days it runs. Without one there is nothing to witness, and the link
  // would still work after setup was abandoned -- which is how somebody
  // ended up listed as a witness to nothing.
  const pact = await db
    .selectFrom("pact")
    .select("id")
    .where("user_id", "=", userId)
    .where("status", "=", "active")
    .executeTakeFirst();
  if (!pact) {
    throw conflict("no_active_pact", "Start a challenge before inviting witnesses to it.");
  }

  // Refused before a code is allocated, so the app can say why instead of
  // handing somebody a link that will be rejected when it is opened.
  await assertSingularFree(db, pact.id, input.relationship);
  for (let attempt = 0; attempt < 5; attempt++) {
    const code = generateInviteCode();
    try {
      const row = await db
        .insertInto("witness")
        .values({
          id: newId(),
          user_id: userId,
          pact_id: pact.id,
          witness_user_id: null,
          invite_code: code,
          invite_email: input.email ?? null,
          relationship: input.relationship,
        })
        .returning(["id", "relationship", "invite_email", "invited_at"])
        .executeTakeFirstOrThrow();
      // From the code that was just written, not read back out of a column
      // that is nullable because accepted rows carry none.
      const url = inviteUrl(code);
      if (input.email) {
        const inviter = await db
          .selectFrom("user")
          .select(["name", "gender"])
          .where("id", "=", userId)
          .executeTakeFirstOrThrow();
        const mail = inviteEmail(inviter.name, input.relationship, url, inviter.gender);
        // Best effort: a failed email must not fail the invite; the code is
        // still shareable from the app.
        sendEmail(input.email, mail.subject, mail.text).catch((e) => console.error("[invite email]", e));
      }
      return { ...row, invite_code: code, url };
    } catch (error) {
      if (!isUniqueViolation(error, "witness_invite_code_key")) throw error;
    }
  }
  throw new Error("could not allocate an invite code");
}

// What the accept screen may show before sign-in: who is asking, and as what.
export async function peekInvite(code: string, viewerId?: string) {
  if (!isInviteCode(code)) throw notFound("Invite");
  const row = await db
    .selectFrom("witness")
    .innerJoin("user as u", "u.id", "witness.user_id")
    .select([
      "witness.relationship",
      "witness.status",
      "witness.user_id",
      "witness.pact_id",
      "u.name as inviter_name",
      "u.image as inviter_image",
      "u.gender as inviter_gender",
    ])
    .where("witness.invite_code", "=", code)
    .where("u.deleted_at", "is", null)
    .executeTakeFirst();
  if (!row || row.status !== "invited") throw notFound("Invite");

  // The challenge this invitation was written for -- not "their current
  // one". Those are the same thing while it runs and different the moment
  // it does not, and the second is when somebody would be told they are
  // joining a challenge that ended.
  const pact = row.pact_id
    ? await db
        .selectFrom("pact")
        .select(["duration_days", "status"])
        .where("id", "=", row.pact_id)
        .executeTakeFirst()
    : undefined;
  // An invitation to a challenge that has ended is not an open invitation.
  // It reads the same as a used code, which is what it is.
  if (!pact || pact.status !== "active") throw notFound("Invite");

  // The photo travels with the name. Somebody deciding whether to vouch for
  // a person should see who is asking, and at this point they have no
  // account, which is the whole reason /v1/media needs no session.
  return {
    inviter_name: row.inviter_name,
    inviter_image: imagePath(row.inviter_image),
    relationship: row.relationship,
    days: pact.duration_days,
    // Whether the person reading this is the one who sent it. acceptInvite
    // already refuses -- nobody witnesses themselves -- but refusing after
    // the button is pressed is a worse way to learn it than never being
    // offered the button. It happens by accident: somebody tests their own
    // link, or taps it in the thread they just shared it to.
    own: viewerId != null && viewerId === row.user_id,
    // Whose challenge it is, so the page and the link preview say "his"
    // rather than a hedge. The profile holds it; sign-up asks for it.
    gender: row.inviter_gender,
    // Whether the person reading this already said yes to this challenge.
    //
    // The link stays open after somebody takes it -- that is the point of
    // one link -- so somebody who accepted an hour ago and taps it again in
    // the same chat gets the whole "will you be a witness" page a second
    // time, and an error under the button when they press it. The answer is
    // not to explain that; it is to take them where the link was always
    // going to take them once they had said yes.
    already:
      viewerId != null &&
      row.pact_id != null &&
      (await db
        .selectFrom("witness")
        .select("id")
        .where("witness_user_id", "=", viewerId)
        .where("pact_id", "=", row.pact_id)
        .where("status", "=", "accepted")
        .executeTakeFirst()) !== undefined,
  };
}

/**
 * Somebody opens the link and says yes.
 *
 * One link, any number of people. The invitation is a row that holds the
 * code and stays open; accepting inserts a *witness* beside it rather than
 * consuming it, so the same link works for the second person and the tenth.
 * That is what an invite link is for, and it is what every sentence the
 * product sends about one already promised.
 *
 * It used to be one row for both jobs, so the first acceptance spent the
 * link and everybody after was told the invitation had already been
 * answered.
 *
 * The exception is a relationship only one person can hold. Nobody has two
 * mothers, so a mother's link closes when a mother accepts -- and does not
 * merely start refusing, because an invitation nothing can answer is not an
 * open one.
 */
export async function acceptInvite(witnessUserId: string, code: string) {
  if (!isInviteCode(code)) throw notFound("Invite");
  const invitation = await db
    .selectFrom("witness")
    .select(witnessColumns)
    .where("invite_code", "=", code)
    .executeTakeFirst();
  if (!invitation) throw notFound("Invite");
  if (invitation.user_id === witnessUserId) throw conflict("own_invite", "You cannot witness yourself.");
  if (invitation.status !== "invited") throw conflict("invite_used", "This invite is closed.");
  const pactId = invitation.pact_id;
  if (!pactId) throw conflict("challenge_over", "That challenge is no longer running.");
  const invitedTo = await db
    .selectFrom("pact")
    .select("status")
    .where("id", "=", pactId)
    .executeTakeFirst();
  // Between sharing the link and somebody opening it, the challenge can
  // finish or break. Accepting then would add a witness to something that
  // is over.
  if (invitedTo?.status !== "active") {
    throw conflict("challenge_over", "That challenge is no longer running.");
  }

  const now = new Date();
  const singular = invitation.relationship != null && SINGULAR.has(invitation.relationship);
  try {
    const accepted = await db.transaction().execute(async (trx) => {
      // The real gate, and the last moment anybody can be told no: the code
      // travels through group chats, so the question is never who was
      // invited but who is opening it.
      await assertSingularFree(trx, pactId, invitation.relationship);
      const witnessRow = await trx
        .insertInto("witness")
        .values({
          id: newId(),
          user_id: invitation.user_id,
          pact_id: pactId,
          witness_user_id: witnessUserId,
          // Nothing to share: this row is a person watching, not an
          // invitation, and the link it came from is still the invitation.
          invite_code: null,
          invite_email: null,
          relationship: invitation.relationship,
          status: "accepted",
          responded_at: now,
        })
        .returning(witnessColumns)
        .executeTakeFirstOrThrow();

      if (singular) {
        await trx
          .updateTable("witness")
          .set({ status: "removed", responded_at: now, updated_at: now })
          .where("id", "=", invitation.id)
          .execute();
      }

      const witness = await trx
        .selectFrom("user")
        .select(["name", "gender"])
        .where("id", "=", witnessUserId)
        .executeTakeFirstOrThrow();
      await queueNotification(trx, {
        recipientId: invitation.user_id,
        aboutUserId: witnessUserId,
        kind: "witness_accepted",
        ...witnessAcceptedCopy(witness.name, invitation.relationship, witness.gender),
        deepLink: `/witnesses/${witnessRow.id}`,
      });
      return witnessRow;
    });
    return accepted;
  } catch (error) {
    if (isUniqueViolation(error, "witness_pair_idx")) {
      throw conflict("already_witness", "You already witness this person for this challenge.");
    }
    throw error;
  }
}

/**
 * Somebody opens the link and says no.
 *
 * Nothing is written. The link belongs to everybody it was sent to, and one
 * person declining must not close it for the rest -- which is what marking
 * the shared row `declined` would do. Nor is there anything to record about
 * this person: they are not a witness, and "declined" is not a state the
 * inviter is shown or notified about anywhere.
 *
 * So this exists to answer the screen: the code is real and open, you are
 * not being counted, and the button may close. Somebody who changes their
 * mind can open the same link again, which is the right answer for a link
 * that is still open.
 */
export async function declineInvite(witnessUserId: string, code: string): Promise<void> {
  if (!isInviteCode(code)) throw notFound("Invite");
  const invitation = await db
    .selectFrom("witness")
    .select(["user_id", "status"])
    .where("invite_code", "=", code)
    .executeTakeFirst();
  if (!invitation || invitation.status !== "invited") throw notFound("Invite");
  if (invitation.user_id === witnessUserId) throw conflict("own_invite", "You cannot witness yourself.");
}

/**
 * Both directions, scoped to challenges that are running.
 *
 * A witness belongs to a challenge. When it ends they were a witness to a
 * thing that finished — which is a record, not a list of people who would be
 * told if something broke today. Showing them alongside live ones made the
 * count on that screen a number about the past, and the count is the one
 * thing on it that has to be true.
 *
 * `mutual` is unchanged in meaning and now happens to be rarer: it needs two
 * live challenges, one each way.
 */
export async function listWitnesses(userId: string) {
  const mine = await db
    .selectFrom("witness")
    .leftJoin("user as w", "w.id", "witness.witness_user_id")
    .select([
      "witness.id",
      "witness.status",
      "witness.relationship",
      "witness.invite_code",
      "witness.invite_email",
      "witness.notify_start",
      "witness.notify_success",
      "witness.notify_failure",
      "witness.notify_digest",
      "witness.roast_mode",
      "witness.views_progress",
      "witness.invited_at",
      "witness.responded_at",
      "w.id as witness_id",
      "w.name as witness_name",
      "w.image as witness_image",
      "w.gender as witness_gender",
    ])
    .innerJoin("pact as p", "p.id", "witness.pact_id")
    .where("witness.user_id", "=", userId)
    .where("witness.status", "in", ["invited", "accepted"])
    .where("p.status", "=", "active")
    .orderBy("witness.invited_at", "desc")
    .execute();

  const supporting = await db
    .selectFrom("witness")
    .innerJoin("user as u", "u.id", "witness.user_id")
    .select([
      "witness.id",
      "witness.relationship",
      "witness.notify_start",
      "witness.notify_success",
      "witness.notify_failure",
      "witness.notify_digest",
      "witness.roast_mode",
      "witness.views_progress",
      "witness.responded_at",
      "u.id as person_id",
      "u.name as person_name",
      "u.image as person_image",
      "u.gender as person_gender",
    ])
    .innerJoin("pact as p", "p.id", "witness.pact_id")
    .where("witness.witness_user_id", "=", userId)
    .where("witness.status", "=", "accepted")
    .where("p.status", "=", "active")
    .orderBy("witness.responded_at", "desc")
    .execute();

  // What each of them has reacted with, most recent first.
  //
  // A reaction was a push notification and then nothing: somebody's brother
  // throws a tomato, the phone buzzes once, and by the evening there is no
  // trace it ever happened. It is the only thing a witness can actually
  // *do*, and the circle is where the people who did it are listed, so it
  // belongs on their card.
  const reactions = await db
    .selectFrom("reaction")
    .innerJoin("witness as w", "w.id", "reaction.witness_id")
    .select(["reaction.witness_id", "reaction.emoji", "reaction.updated_at"])
    .where("w.user_id", "=", userId)
    .where("w.status", "=", "accepted")
    .orderBy("reaction.updated_at", "desc")
    .execute();
  const byWitness = new Map<string, string[]>();
  for (const r of reactions) {
    const held = byWitness.get(r.witness_id) ?? [];
    // Three is what the card has room for, and the newest are the ones
    // worth having.
    if (held.length < 3) held.push(r.emoji);
    byWitness.set(r.witness_id, held);
  }

  const iSupport = new Set(supporting.map((s) => s.person_id));
  const myWitnessIds = new Set(mine.filter((m) => m.status === "accepted").map((m) => m.witness_id));

  return {
    my_witnesses: mine.map((m) => ({
      id: m.id,
      status: m.status,
      relationship: m.relationship,
      invite_code: m.status === "invited" ? m.invite_code : null,
      invite_url: m.status === "invited" && m.invite_code ? inviteUrl(m.invite_code) : null,
      invite_email: m.invite_email,
      // The pronoun the phone needs to talk about this person -- "his
      // progress", not "their progress" about a brother -- travels with the
      // name, the same as the photo does.
      user: m.witness_id
        ? { id: m.witness_id, name: m.witness_name, image: imagePath(m.witness_image), gender: m.witness_gender }
        : null,
      notify_start: m.notify_start,
      notify_success: m.notify_success,
      notify_failure: m.notify_failure,
      notify_digest: m.notify_digest,
      roast_mode: m.roast_mode,
      views_progress: m.views_progress,
      mutual: m.witness_id !== null && iSupport.has(m.witness_id),
      reactions: byWitness.get(m.id) ?? [],
      invited_at: m.invited_at,
      responded_at: m.responded_at,
    })),
    i_witness: supporting.map((s) => ({
      id: s.id,
      relationship: s.relationship,
      user: { id: s.person_id, name: s.person_name, image: imagePath(s.person_image), gender: s.person_gender },
      notify_start: s.notify_start,
      notify_success: s.notify_success,
      notify_failure: s.notify_failure,
      notify_digest: s.notify_digest,
      roast_mode: s.roast_mode,
      views_progress: s.views_progress,
      mutual: myWitnessIds.has(s.person_id),
      responded_at: s.responded_at,
    })),
  };
}

const USER_FIELDS = new Set(["views_progress", "relationship"]);
const WITNESS_FIELDS = new Set(["notify_start", "notify_success", "notify_failure", "notify_digest", "roast_mode"]);

async function requireRow(id: string) {
  if (!isUuidLike(id)) throw notFound("Witness");
  const row = await db.selectFrom("witness").select(witnessColumns).where("id", "=", id).executeTakeFirst();
  if (!row || row.status === "removed") throw notFound("Witness");
  return row;
}

// Each side edits only its own fields; asking for the other side's is 403,
// not silently ignored, so a client bug is visible.
export async function updateWitness(callerId: string, id: string, patch: WitnessPatch) {
  const row = await requireRow(id);
  const keys = Object.keys(patch);
  const allowed =
    callerId === row.user_id ? USER_FIELDS : callerId === row.witness_user_id ? WITNESS_FIELDS : null;
  if (!allowed) throw notFound("Witness");
  if (keys.some((k) => !allowed.has(k))) throw forbidden();
  return db
    .updateTable("witness")
    .set({ ...patch, updated_at: new Date() })
    .where("id", "=", id)
    .returning(witnessColumns)
    .executeTakeFirstOrThrow();
}

export async function removeWitness(callerId: string, id: string): Promise<void> {
  const row = await requireRow(id);
  if (callerId !== row.user_id && callerId !== row.witness_user_id) throw notFound("Witness");
  await db
    .updateTable("witness")
    .set({ status: "removed", updated_at: new Date() })
    .where("id", "=", id)
    .execute();
}

// The witness side of an accepted relationship, with progress access.
export async function requireWitnessView(callerId: string, id: string) {
  const row = await requireRow(id);
  if (row.witness_user_id !== callerId || row.status !== "accepted") throw notFound("Witness");
  if (!row.views_progress) throw forbidden();
  return row;
}

/**
 * Whether the caller may read one particular challenge of somebody's.
 *
 * Per challenge, not per person. It used to ask only whether the caller was
 * an accepted witness of the owner at all, so anybody who had ever watched
 * one challenge could read every other one the owner had by id -- including
 * the ones from before they were invited and the ones after they stopped
 * being a witness. Somebody agreed to watch a challenge. That is the whole
 * of what they agreed to.
 */
export async function canViewPact(callerId: string, ownerId: string, pactId: string): Promise<boolean> {
  if (callerId === ownerId) return true;
  const row = await db
    .selectFrom("witness")
    .select("id")
    .where("user_id", "=", ownerId)
    .where("witness_user_id", "=", callerId)
    .where("pact_id", "=", pactId)
    .where("status", "=", "accepted")
    .where("views_progress", "=", true)
    .executeTakeFirst();
  return row !== undefined;
}
