import { Kysely, sql } from "kysely";

/**
 * Two more ways to earn time, the founder's next two: a ride (metres at a
 * cycling speed, from GPS on the phone) and a meditation (minutes of a
 * guided breathing session with the phone lying still). What 0014 did
 * for the four before them: the type check learns the names, and every
 * pact on the ledger gets a rule for each, priced like its walk. A pact
 * created afterwards by a phone that predates them gets the same from
 * createPact (withLaterActivities).
 */
const TYPES = [
  "walk_steps",
  "focus_session",
  "push_ups",
  "plank",
  "wall_sit",
  "run_steps",
  "stairs",
  "cycling",
  "meditation",
  "waiting_period",
];
const LEGACY = TYPES.filter((t) => t !== "cycling" && t !== "meditation");
const DEFAULT_REWARD_MIN = 10;
const DEFAULT_DAILY_CAP_MIN = 30;

/** Each new rule's own field and number; reward and cap come from the pact. */
const RULES: Array<{ type: string; field: "target" | "target_min"; value: number }> = [
  { type: "cycling", field: "target", value: 3000 }, // metres
  { type: "meditation", field: "target_min", value: 10 }, // minutes
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
            ${sql.lit(rule.field)}, ${sql.lit(rule.value)},
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
  await constraint(LEGACY, false).execute(db);
}
