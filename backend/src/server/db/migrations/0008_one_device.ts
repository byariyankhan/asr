import { Kysely, sql } from "kysely";

/**
 * One account, one phone.
 *
 * Signing in somewhere signs the last phone out, and the challenge comes
 * with you. Which leaves a gap nothing could see before: the new phone holds
 * the challenge from the moment it signs in, and the permissions that make a
 * challenge mean anything -- usage access, drawing over other apps -- are
 * granted per install and are not there yet.
 *
 * So the pact remembers when it landed on a phone that could not enforce it.
 * Cleared by the first heartbeat that says protection is on, and read by the
 * watchdog: two hours of a challenge nothing is enforcing is something the
 * witnesses are told about. A column rather than a derived time, because
 * "when did this challenge become unenforced" is not answerable from the
 * device row -- that only knows the last thing it was told, not when it
 * changed hands.
 */
export async function up(db: Kysely<unknown>): Promise<void> {
  await sql`alter table pact add column protection_pending_since timestamptz`.execute(db);
}

export async function down(db: Kysely<unknown>): Promise<void> {
  await sql`alter table pact drop column protection_pending_since`.execute(db);
}
