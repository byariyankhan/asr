import { Kysely, sql } from "kysely";

/**
 * A name in two parts.
 *
 * Better Auth's `name` stays, as the display name every screen, notification
 * and email uses; it is now composed from these two ("First Last", or just
 * the first for people who have one name). Existing rows are split at the
 * first space, which is right for every real name here and harmless for the
 * sign-up placeholder (the email's local part), which About You replaces.
 */
export async function up(db: Kysely<unknown>): Promise<void> {
  await sql`alter table "user" add column first_name text, add column last_name text`.execute(db);
  await sql`
    update "user"
    set first_name = split_part(name, ' ', 1),
        last_name = nullif(btrim(substr(name, length(split_part(name, ' ', 1)) + 1)), '')
    where name <> ''`.execute(db);
}

export async function down(db: Kysely<unknown>): Promise<void> {
  await sql`alter table "user" drop column first_name, drop column last_name`.execute(db);
}
