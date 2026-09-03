import { sql } from "kysely";
import { db } from "./db/client";
import { key, redis } from "./redis";

export const WATCHDOG_LAST_RUN_KEY = key("watchdog", "last_run");
const WATCHDOG_STALE_MS = 30 * 60 * 1000;

export async function healthReport() {
  const [dbOk, redisOk, lastRun] = await Promise.all([
    sql`select 1`
      .execute(db)
      .then(() => true)
      .catch(() => false),
    redis()
      ?.ping()
      .then((r) => r === "PONG")
      .catch(() => false) ?? Promise.resolve(false),
    redis()
      ?.get(WATCHDOG_LAST_RUN_KEY)
      .catch(() => null) ?? Promise.resolve(null),
  ]);
  const lastRunMs = lastRun ? Number(lastRun) : NaN;
  return {
    ok: dbOk && redisOk,
    db: dbOk,
    redis: redisOk,
    watchdog_stale: !Number.isFinite(lastRunMs) || Date.now() - lastRunMs > WATCHDOG_STALE_MS,
  };
}
