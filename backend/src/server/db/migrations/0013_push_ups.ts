import { Kysely, sql } from "kysely";

/**
 * Push-ups: a third way to earn time (android/PUSH_UPS.md).
 *
 * Two things. The check on `activity.type` learns the new name, and every
 * pact already on the ledger gets a `push_ups` rule in its snapshot, priced
 * like its walk: the snapshot is locked when a challenge starts precisely so
 * a phone cannot renegotiate an activity's price mid-way, which means a rule
 * added afterwards has to be added here or it is not there at all. Nothing
 * has launched and nobody's price is being changed, so the founder's call
 * was that push-ups are simply on for every challenge, running or future.
 *
 * The reward and cap are copied from the pact's own walk rule (or its focus
 * rule, or the app's defaults), never from a fresh constant: a challenge
 * whose walk is worth 15 minutes gets push-ups worth 15, and the phone's
 * "+10 for Instagram" copy stays true for the challenges it started.
 */
const TYPES = ["walk_steps", "focus_session", "push_ups", "waiting_period"];
const LEGACY = ["walk_steps", "focus_session", "waiting_period"];
const DEFAULT_TARGET = 7;
const DEFAULT_REWARD_MIN = 10;
const DEFAULT_DAILY_CAP_MIN = 30;

const constraint = (values: string[], valid: boolean) =>
  sql`
    alter table activity
      drop constraint if exists activity_type_check,
      add constraint activity_type_check
        check (type in (${sql.join(values.map((v) => sql.lit(v)))}))
        ${valid ? sql`` : sql`not valid`}
  `;

export async function up(db: Kysely<unknown>): Promise<void> {
  await constraint(TYPES, true).execute(db);
  await sql`
    update pact
    set snapshot = jsonb_set(
      snapshot,
      '{activities}',
      coalesce(snapshot -> 'activities', '{}'::jsonb) || jsonb_build_object(
        'push_ups',
        jsonb_build_object(
          'target', ${sql.lit(DEFAULT_TARGET)},
          'reward_min', coalesce(
            (snapshot #>> '{activities,walk_steps,reward_min}')::int,
            (snapshot #>> '{activities,focus_session,reward_min}')::int,
            ${sql.lit(DEFAULT_REWARD_MIN)}
          ),
          'daily_cap_min', coalesce(
            (snapshot #>> '{activities,walk_steps,daily_cap_min}')::int,
            (snapshot #>> '{activities,focus_session,daily_cap_min}')::int,
            ${sql.lit(DEFAULT_DAILY_CAP_MIN)}
          )
        )
      ),
      true
    )
    where not (snapshot -> 'activities' ? 'push_ups')
  `.execute(db);
}

export async function down(db: Kysely<unknown>): Promise<void> {
  await sql`update pact set snapshot = snapshot #- '{activities,push_ups}'`.execute(db);
  // NOT VALID: rows of the new type, if any, are left where they are rather
  // than deleted, and the old rule applies to everything written from now
  // on. The previous release never reads them; it has no code that would.
  await constraint(LEGACY, false).execute(db);
}
