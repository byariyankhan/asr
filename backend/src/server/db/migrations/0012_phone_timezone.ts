import { Kysely, sql } from "kysely";

/**
 * The calendar the phone is living in.
 *
 * `timezone` is the zone the challenge was locked in and never moves; it is
 * what completion is judged against, so a phone cannot bring the end of a
 * challenge forward by claiming a zone further east. Everything that means
 * "today" -- which summary rows are today's, the day number a witness sees,
 * the day an added app counts from, the day an activity's cap belongs to --
 * follows the phone, which reports its zone with every registration,
 * heartbeat and summary. Null until the first report: the zone at the start.
 */
export async function up(db: Kysely<unknown>): Promise<void> {
  await sql`alter table pact add column phone_timezone text`.execute(db);
}

export async function down(db: Kysely<unknown>): Promise<void> {
  await sql`alter table pact drop column phone_timezone`.execute(db);
}
