import { Kysely, sql } from "kysely";

/**
 * A witness belongs to a challenge, not to an account.
 *
 * The table was written the other way: a row joined two people and nothing
 * else, so a witness outlived the pact they agreed to watch. That produced a
 * screen reading "Ariyan Khan / No challenge running · Brother" — somebody
 * listed as a witness to nothing — and it was reachable by ordinary use,
 * because the setup flow invited witnesses at step five and created the pact
 * at step eight. Send the invitations, abandon setup, and the invitations
 * still worked.
 *
 * Every message the product sends says the other thing: "I'm starting a
 * 14-day challenge… I want you to be my witness", "Become a witness for
 * their challenge". The copy was right and the schema was wrong.
 *
 * Nullable, because rows written before this exist and cannot be invented
 * into a pact they were never part of. They are backfilled where there is an
 * obvious answer — the account's most recent pact — and the ones that remain
 * null belong to accounts that never started one. Those are read as ended:
 * listed nowhere, notified about nothing, and left in place rather than
 * deleted, because they are somebody's record of having said yes.
 */
export async function up(db: Kysely<unknown>): Promise<void> {
  await db.schema
    .alterTable("witness")
    .addColumn("pact_id", "uuid", (col) => col.references("pact.id").onDelete("cascade"))
    .execute();

  await sql`
    update witness w
       set pact_id = (
             select p.id
               from pact p
              where p.user_id = w.user_id
              order by (p.status = 'active') desc, p.created_at desc
              limit 1
           )
     where w.pact_id is null
  `.execute(db);

  await db.schema
    .createIndex("witness_pact_idx")
    .on("witness")
    .columns(["pact_id", "status"])
    .execute();

  await sql`comment on column witness.pact_id is 'The challenge this witness was invited to. Null only for rows from before 0004 whose account never started one.'`.execute(db);
}

export async function down(db: Kysely<unknown>): Promise<void> {
  await db.schema.dropIndex("witness_pact_idx").execute();
  await db.schema.alterTable("witness").dropColumn("pact_id").execute();
}
