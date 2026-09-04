import { Kysely, sql } from "kysely";

/**
 * An accepted witness is not an invitation and does not hold a code.
 *
 * One link, any number of people. That is what an invite link is for and
 * what every message about it says -- "send it to whoever you want watching
 * this" -- and the table could not express it: the code lived on the witness
 * row, so accepting flipped that one row to `accepted` and the link was
 * spent. The second person to open it was told the invitation had already
 * been answered, which was true and was not the rule.
 *
 * So an invitation and a witness become different rows. The invitation keeps
 * the code and stays open; accepting inserts a witness beside it. Which
 * leaves accepted rows with no code to hold, and `not null` insisting they
 * invent one.
 *
 * The unique constraint stays. Postgres lets NULLs repeat in a unique index,
 * which is exactly right here: no two invitations share a code, and no
 * accepted row has one at all.
 */
export async function up(db: Kysely<unknown>): Promise<void> {
  await sql`alter table witness alter column invite_code drop not null`.execute(db);
}

export async function down(db: Kysely<unknown>): Promise<void> {
  await sql`delete from witness where invite_code is null`.execute(db);
  await sql`alter table witness alter column invite_code set not null`.execute(db);
}
