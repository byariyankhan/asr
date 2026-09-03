import { config } from "dotenv";
import * as path from "node:path";

config({ path: path.join(__dirname, "..", "..", "..", ".env") });

import { promises as fs } from "node:fs";
import { pathToFileURL } from "node:url";
import { Kysely, PostgresDialect } from "kysely";
import { FileMigrationProvider, Migrator } from "kysely/migration";
import { Pool } from "pg";

// `pnpm db:migrate` (up, to latest) or `pnpm db:migrate:down` (one step).
const direction = process.argv[2] === "down" ? "down" : "up";

if (!process.env.DATABASE_URL) {
  console.error("DATABASE_URL is not set");
  process.exit(1);
}

const db = new Kysely<unknown>({
  dialect: new PostgresDialect({
    pool: new Pool({ connectionString: process.env.DATABASE_URL }),
  }),
});

const migrator = new Migrator({
  db,
  provider: new FileMigrationProvider({
    fs,
    path,
    migrationFolder: path.join(__dirname, "migrations"),
    import: (filePath) => import(pathToFileURL(filePath).href),
  }),
});

async function run() {
  const { error, results } =
    direction === "up" ? await migrator.migrateToLatest() : await migrator.migrateDown();

  for (const result of results ?? []) {
    if (result.status === "Success") console.log(`✓ ${result.direction} ${result.migrationName}`);
    else if (result.status === "Error") console.error(`✗ ${result.direction} ${result.migrationName}`);
  }

  if (error) {
    console.error(error);
    process.exit(1);
  }
  await db.destroy();
}

run();
