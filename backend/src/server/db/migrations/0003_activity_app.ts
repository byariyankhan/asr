import { Kysely, sql } from "kysely";

/**
 * Which app the extra minutes were earned for.
 *
 * A witness is told "{userName} reached the TikTok limit and earned 10 more
 * minutes", and the app's name is the part that makes that sentence mean
 * anything — "reached a limit" could be any of the apps they picked. The
 * activity row is where it belongs: the earn is started from a block screen
 * that already knows the package, and the pact's snapshot turns a package
 * into the label somebody recognises.
 *
 * Nullable, because rows written before this exist and an earn without a
 * package is still a valid earn: the notification simply says "their limit"
 * instead of naming one.
 */
export async function up(db: Kysely<unknown>): Promise<void> {
  await db.schema.alterTable("activity").addColumn("app_package", "text").execute();
  await sql`comment on column activity.app_package is 'The app whose limit this earn was for. Null for rows written before 0003.'`.execute(db);
}

export async function down(db: Kysely<unknown>): Promise<void> {
  await db.schema.alterTable("activity").dropColumn("app_package").execute();
}
