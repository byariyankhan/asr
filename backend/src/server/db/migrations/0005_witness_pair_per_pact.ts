import { Kysely, sql } from "kysely";

/**
 * One accepted witness per person, per challenge — not per person, ever.
 *
 * `witness_pair_idx` was `unique (user_id, witness_user_id)` on accepted
 * rows, from when a witness joined two people and nothing else. 0004 moved a
 * witness onto a challenge and left this behind, so the index went on
 * enforcing the rule the rest of the product had already stopped believing.
 *
 * What that does in practice: somebody's friend witnesses their 14-day
 * challenge, that challenge ends, they start another one and send the same
 * friend a link. The friend opens it, presses Accept, and is told "You
 * already witness this person" — about a challenge that finished. There is
 * no way round it and no way to undo it. The second challenge simply cannot
 * have the witness the first one had, forever.
 *
 * Which is the opposite of what "witnesses do not carry over" was supposed
 * to mean. They do not carry over, so they have to be invitable again.
 *
 * Adding `pact_id` only widens the index, so no row that exists can conflict
 * with it. Rows still holding a null `pact_id` are the pre-0004 ones; NULLs
 * never collide in a Postgres unique index, which is the right answer for
 * them too — they are history, and history does not need to be unique.
 */
export async function up(db: Kysely<unknown>): Promise<void> {
  await sql`drop index if exists witness_pair_idx`.execute(db);
  await sql`
    create unique index witness_pair_idx on witness (user_id, witness_user_id, pact_id)
      where witness_user_id is not null and status = 'accepted'
  `.execute(db);
}

export async function down(db: Kysely<unknown>): Promise<void> {
  await sql`drop index if exists witness_pair_idx`.execute(db);
  await sql`
    create unique index witness_pair_idx on witness (user_id, witness_user_id)
      where witness_user_id is not null and status = 'accepted'
  `.execute(db);
}
