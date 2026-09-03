import { db, isUniqueViolation } from "./db/client";
import { queueNotification } from "./notifications";
import { conflict, forbidden, notFound } from "@/lib/http";
import { generateInviteCode, isInviteCode } from "@/lib/invite-code";
import type { WitnessInvite, WitnessPatch } from "@/lib/schemas";
import { isUuidLike, newId } from "@/lib/uuid";

export const witnessColumns = [
  "id",
  "user_id",
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

const SITE_URL = () => process.env.PUBLIC_SITE_URL ?? "https://joinasr.com";

export function inviteUrl(code: string): string {
  return `${SITE_URL()}/w/${code}`;
}

// A new invite row. The email, if given, is sent by the delivery worker;
// here it is only stored.
export async function createInvite(userId: string, input: WitnessInvite) {
  for (let attempt = 0; attempt < 5; attempt++) {
    const code = generateInviteCode();
    try {
      const row = await db
        .insertInto("witness")
        .values({
          id: newId(),
          user_id: userId,
          witness_user_id: null,
          invite_code: code,
          invite_email: input.email ?? null,
          relationship: input.relationship,
        })
        .returning(["id", "invite_code", "relationship", "invite_email", "invited_at"])
        .executeTakeFirstOrThrow();
      return { ...row, url: inviteUrl(row.invite_code) };
    } catch (error) {
      if (!isUniqueViolation(error, "witness_invite_code_key")) throw error;
    }
  }
  throw new Error("could not allocate an invite code");
}

// What the accept screen may show before sign-in: who is asking, and as what.
export async function peekInvite(code: string) {
  if (!isInviteCode(code)) throw notFound("Invite");
  const row = await db
    .selectFrom("witness")
    .innerJoin("user as u", "u.id", "witness.user_id")
    .select(["witness.relationship", "witness.status", "u.name as inviter_name"])
    .where("witness.invite_code", "=", code)
    .executeTakeFirst();
  if (!row || row.status !== "invited") throw notFound("Invite");
  return { inviter_name: row.inviter_name, relationship: row.relationship };
}

export async function acceptInvite(witnessUserId: string, code: string) {
  if (!isInviteCode(code)) throw notFound("Invite");
  const row = await db
    .selectFrom("witness")
    .select(witnessColumns)
    .where("invite_code", "=", code)
    .executeTakeFirst();
  if (!row) throw notFound("Invite");
  if (row.user_id === witnessUserId) throw conflict("own_invite", "You cannot witness yourself.");
  if (row.status !== "invited") throw conflict("invite_used", "This invite was already answered.");

  const now = new Date();
  try {
    const accepted = await db.transaction().execute(async (trx) => {
      const updated = await trx
        .updateTable("witness")
        .set({ witness_user_id: witnessUserId, status: "accepted", responded_at: now, updated_at: now })
        .where("id", "=", row.id)
        .where("status", "=", "invited")
        .returning(witnessColumns)
        .executeTakeFirst();
      if (!updated) throw conflict("invite_used", "This invite was already answered.");

      const witness = await trx.selectFrom("user").select("name").where("id", "=", witnessUserId).executeTakeFirstOrThrow();
      await queueNotification(trx, {
        recipientId: row.user_id,
        aboutUserId: witnessUserId,
        kind: "witness_accepted",
        title: `${witness.name} is your witness`,
        body: `${witness.name} accepted. They'll know if your pact breaks.`,
        deepLink: `/witnesses/${row.id}`,
      });
      return updated;
    });
    return accepted;
  } catch (error) {
    if (isUniqueViolation(error, "witness_pair_idx")) {
      throw conflict("already_witness", "You already witness this person.");
    }
    throw error;
  }
}

export async function declineInvite(witnessUserId: string, code: string): Promise<void> {
  if (!isInviteCode(code)) throw notFound("Invite");
  const now = new Date();
  const result = await db
    .updateTable("witness")
    .set({ witness_user_id: witnessUserId, status: "declined", responded_at: now, updated_at: now })
    .where("invite_code", "=", code)
    .where("status", "=", "invited")
    .where("user_id", "!=", witnessUserId)
    .executeTakeFirst();
  if (result.numUpdatedRows === 0n) throw notFound("Invite");
}

// Both directions, with a `mutual` flag where the same two people witness
// each other (the design's "MUTUAL" badge).
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
    ])
    .where("witness.user_id", "=", userId)
    .where("witness.status", "in", ["invited", "accepted"])
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
    ])
    .where("witness.witness_user_id", "=", userId)
    .where("witness.status", "=", "accepted")
    .orderBy("witness.responded_at", "desc")
    .execute();

  const iSupport = new Set(supporting.map((s) => s.person_id));
  const myWitnessIds = new Set(mine.filter((m) => m.status === "accepted").map((m) => m.witness_id));

  return {
    my_witnesses: mine.map((m) => ({
      id: m.id,
      status: m.status,
      relationship: m.relationship,
      invite_code: m.status === "invited" ? m.invite_code : null,
      invite_url: m.status === "invited" ? inviteUrl(m.invite_code) : null,
      invite_email: m.invite_email,
      user: m.witness_id ? { id: m.witness_id, name: m.witness_name } : null,
      notify_start: m.notify_start,
      notify_success: m.notify_success,
      notify_failure: m.notify_failure,
      notify_digest: m.notify_digest,
      roast_mode: m.roast_mode,
      views_progress: m.views_progress,
      mutual: m.witness_id !== null && iSupport.has(m.witness_id),
      invited_at: m.invited_at,
      responded_at: m.responded_at,
    })),
    i_witness: supporting.map((s) => ({
      id: s.id,
      relationship: s.relationship,
      user: { id: s.person_id, name: s.person_name },
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

export async function canViewUser(callerId: string, ownerId: string): Promise<boolean> {
  if (callerId === ownerId) return true;
  const row = await db
    .selectFrom("witness")
    .select("id")
    .where("user_id", "=", ownerId)
    .where("witness_user_id", "=", callerId)
    .where("status", "=", "accepted")
    .where("views_progress", "=", true)
    .executeTakeFirst();
  return row !== undefined;
}
