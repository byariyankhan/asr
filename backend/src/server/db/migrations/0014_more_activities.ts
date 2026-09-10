import { Kysely, sql } from "kysely";

/**
 * Four more ways to earn time, so there is something easy on the list for
 * the person who would otherwise give the challenge up: a plank and a wall
 * sit (timed by the camera, as push-ups are counted by it), a run (steps
 * at a running cadence) and a climb (floors, from the barometer while the
 * step counter moves). The founder's pick from a longer list.
 *
 * The same two things 0013 did for push-ups. The check on `activity.type`
 * learns the names, and every pact already on the ledger gets a rule for
 * each in its snapshot, priced like its walk: the snapshot is locked when
 * a challenge starts so a phone cannot renegotiate a price mid-way, which
 * is exactly why a rule added later has to be added here or it is not
 * there at all. Pre-launch, no real users, the founder's call: on for
 * every challenge, running or future.
 *
 * Reward and cap are copied from the pact's own walk rule (or its focus
 * rule, or the defaults), never from a fresh constant, so a challenge
 * whose walk is worth 15 minutes gets the new ones at 15 too. Targets are
 * the app's own (android/.../EarnRules.kt); they are the ask, not the
 * price, and the phone reads them back from the snapshot as it does for
 * push-ups.
 */
const TYPES = [
  "walk_steps",
  "focus_session",
  "push_ups",
  "plank",
  "wall_sit",
  "run_steps",
  "stairs",
  "waiting_period",
];
const LEGACY = ["walk_steps", "focus_session", "push_ups", "waiting_period"];
const DEFAULT_REWARD_MIN = 10;
const DEFAULT_DAILY_CAP_MIN = 30;

/** Each new rule's own field and number; reward and cap come from the pact. */
const RULES: Array<{ type: string; value: number }> = [
  { type: "plank", value: 45 }, // seconds
  { type: "wall_sit", value: 45 }, // seconds
  { type: "run_steps", value: 1000 }, // steps
  { type: "stairs", value: 10 }, // floors
];

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
  for (const rule of RULES) {
    await sql`
      update pact
      set snapshot = jsonb_set(
        snapshot,
        '{activities}',
        coalesce(snapshot -> 'activities', '{}'::jsonb) || jsonb_build_object(
          ${sql.lit(rule.type)},
          jsonb_build_object(
            'target', ${sql.lit(rule.value)},
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
      where not (snapshot -> 'activities' ? ${sql.lit(rule.type)})
    `.execute(db);
  }
}

export async function down(db: Kysely<unknown>): Promise<void> {
  for (const rule of RULES) {
    await sql`update pact set snapshot = snapshot #- ${sql.lit(`{activities,${rule.type}}`)}::text[]`.execute(db);
  }
  // NOT VALID, as 0013's down: rows of the new types, if any, are left
  // where they are rather than deleted, and the old rule applies to
  // everything written from now on.
  await constraint(LEGACY, false).execute(db);
}
