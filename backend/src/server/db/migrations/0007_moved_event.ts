import { Kysely, sql } from "kysely";

/**
 * A challenge changing phones is a thing that happens to it.
 *
 * One challenge runs on one handset, because a phone can measure its own
 * screen and nothing else's -- two phones enforcing the same thirty minutes
 * is an hour. So there is a way to move it, and the move is an event on the
 * pact like every other: the person who replaced a broken phone has a record
 * that explains the gap, and the person who parked their challenge on a
 * tablet in a drawer has one that explains that too.
 *
 * The check constraint is the whole change. Writing the event without it
 * fails at the row, which is the right place for an unknown event type to
 * fail and the wrong place to find out about a new one.
 */
export async function up(db: Kysely<unknown>): Promise<void> {
  await sql`alter table pact_event drop constraint pact_event_type_check`.execute(db);
  await sql`
    alter table pact_event add constraint pact_event_type_check check (type in (
      'started', 'completed', 'broken',
      'protection_lost', 'uninstalled', 'restored', 'moved',
      'limit_hit', 'activity_completed', 'activity_failed'
    ))
  `.execute(db);
}

export async function down(db: Kysely<unknown>): Promise<void> {
  await sql`delete from pact_event where type = 'moved'`.execute(db);
  await sql`alter table pact_event drop constraint pact_event_type_check`.execute(db);
  await sql`
    alter table pact_event add constraint pact_event_type_check check (type in (
      'started', 'completed', 'broken',
      'protection_lost', 'uninstalled', 'restored',
      'limit_hit', 'activity_completed', 'activity_failed'
    ))
  `.execute(db);
}
