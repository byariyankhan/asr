import { sql, type Kysely } from "kysely";

/**
 * A witness is one person, so the app should name one person.
 *
 * The first list lumped them — "Parent", "Husband or wife" — which reads as
 * though the app is guessing because it does not know. It never had to
 * guess: whoever is sending the invitation knows exactly who they are
 * inviting. So the values are specific now, and the label under each is a
 * single relationship rather than a pair with "or" in it.
 *
 * The three lumped values stay accepted. Rows written before this exist,
 * and a constraint that refused them would break every witness already
 * invited to make a list read better.
 */
const ALL = [
  "mother",
  "father",
  "brother",
  "sister",
  "husband",
  "wife",
  "partner",
  "friend",
  "mentor",
  "colleague",
  "other",
  // Legacy, still stored, no longer offered.
  "parent",
  "sibling",
  "spouse",
];

const LEGACY = ["parent", "sibling", "spouse", "partner", "friend", "mentor", "colleague", "other"];

const constraint = (values: string[]) =>
  sql`
    alter table witness
      drop constraint if exists witness_relationship_check,
      add constraint witness_relationship_check
        check (relationship is null or relationship in (${sql.join(values.map((v) => sql.lit(v)))}))
  `;

export async function up(db: Kysely<unknown>): Promise<void> {
  await constraint(ALL).execute(db);
}

export async function down(db: Kysely<unknown>): Promise<void> {
  // Anything specific collapses back to what the old constraint allowed,
  // or the constraint could not be added at all.
  await sql`
    update witness set relationship = case
      when relationship in ('mother', 'father') then 'parent'
      when relationship in ('brother', 'sister') then 'sibling'
      when relationship in ('husband', 'wife') then 'spouse'
      else relationship
    end
  `.execute(db);
  await constraint(LEGACY).execute(db);
}
