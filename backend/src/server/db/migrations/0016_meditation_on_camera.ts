import { Kysely, sql } from "kysely";

/**
 * The meditation moved from the phone lying still under a breathing guide
 * to the camera watching the person sit still, and its ask came down from
 * ten minutes to seven in one sitting: the founder's price for the harder
 * proof. Every pact whose rule still says ten is re-priced to seven, so
 * the ledger's record of a meditation matches what the phone now
 * measures. Only the minutes change; the reward and the cap, and any
 * pact whose phone sent a number of its own, are left alone.
 */
const OLD_MIN = 10;
const NEW_MIN = 7;

export async function up(db: Kysely<unknown>): Promise<void> {
  await sql`
    update pact
    set snapshot = jsonb_set(snapshot, '{activities,meditation,target_min}', ${sql.lit(String(NEW_MIN))}::jsonb, false)
    where (snapshot #>> '{activities,meditation,target_min}')::int = ${sql.lit(OLD_MIN)}
  `.execute(db);
}

export async function down(db: Kysely<unknown>): Promise<void> {
  await sql`
    update pact
    set snapshot = jsonb_set(snapshot, '{activities,meditation,target_min}', ${sql.lit(String(OLD_MIN))}::jsonb, false)
    where (snapshot #>> '{activities,meditation,target_min}')::int = ${sql.lit(NEW_MIN)}
  `.execute(db);
}
