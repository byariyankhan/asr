import { Kysely, sql } from "kysely";

/**
 * When the server itself was away.
 *
 * Every silence rule the watchdog has -- a day without a heartbeat, two
 * hours without protection, a suspicion of an uninstall standing for two
 * hours -- measures time in which the phone could have said something and
 * did not. Time the server was down is not that: nothing could reach it.
 * Before this, a server that came back after a day marked every running
 * challenge broken for "silence" and told every witness, because the only
 * clock it had was the wall.
 *
 * `watchdog_state` is the one row that says when the watchdog last ran; a
 * run that finds it more than half an hour old writes the gap down here as
 * an outage. Postgres and not Redis, because Redis restarts with the stack
 * and this has to survive exactly the event it records.
 */
export async function up(db: Kysely<unknown>): Promise<void> {
  await sql`
    create table watchdog_state (
      id          integer primary key check (id = 1),
      last_run_at timestamptz not null
    )`.execute(db);
  await sql`
    create table server_outage (
      started_at  timestamptz primary key,
      ended_at    timestamptz not null check (ended_at > started_at),
      created_at  timestamptz not null default now()
    )`.execute(db);
}

export async function down(db: Kysely<unknown>): Promise<void> {
  await sql`drop table server_outage`.execute(db);
  await sql`drop table watchdog_state`.execute(db);
}
