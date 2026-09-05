import { Kysely, sql } from "kysely";

/**
 * When a phone first looked like it no longer has the app.
 *
 * Losing the heartbeat cannot tell an uninstall from a flat battery, and
 * treating the two the same means telling somebody's mother they deleted the
 * app because they spent an afternoon with their phone off. That is why the
 * heartbeat rule waits a whole day before saying anything.
 *
 * Firebase can tell them apart: a phone that is off or has no data has its
 * message accepted and queued, and only an installation Google no longer
 * knows about comes back as not-registered. So the watchdog asks, quietly,
 * and this is where the answer waits -- because one answer is not enough to
 * accuse anybody of anything. A second answer two hours later, with not a
 * word from the phone in between, is.
 *
 * Cleared by anything that proves the app is there: a heartbeat, or
 * registering again with a fresh token.
 */
export async function up(db: Kysely<unknown>): Promise<void> {
  await sql`alter table device add column removal_suspected_at timestamptz`.execute(db);
}

export async function down(db: Kysely<unknown>): Promise<void> {
  await sql`alter table device drop column removal_suspected_at`.execute(db);
}
