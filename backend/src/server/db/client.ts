import { Kysely, PostgresDialect } from "kysely";
import { Pool, types } from "pg";
import type { Database } from "./schema";

// `date` columns come back as plain strings, not JS Dates: a Date for
// 2026-09-03 would be midnight UTC and shift a day in most timezones.
types.setTypeParser(types.builtins.DATE, (v) => v);

const globalForDb = globalThis as unknown as { __asrPool?: Pool };

const pool =
  globalForDb.__asrPool ??
  new Pool({
    connectionString: process.env.DATABASE_URL,
    max: 10,
  });
globalForDb.__asrPool = pool;

export const db = new Kysely<Database>({
  dialect: new PostgresDialect({ pool }),
});

// Postgres unique_violation, used to turn "one active pact per user"
// and "event id already seen" into 409/200 instead of 500.
export function isUniqueViolation(error: unknown, constraint?: string): boolean {
  if (typeof error !== "object" || error === null) return false;
  const e = error as { code?: string; constraint?: string };
  return e.code === "23505" && (constraint === undefined || e.constraint === constraint);
}
