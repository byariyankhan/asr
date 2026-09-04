import { sql } from "kysely";
import { db } from "./db/client";
import { deleteObject, putObject, r2Config, R2Error } from "./r2";
import { key, redis } from "./redis";

export const WATCHDOG_LAST_RUN_KEY = key("watchdog", "last_run");
const WATCHDOG_STALE_MS = 30 * 60 * 1000;

/**
 * Whether photo uploads can actually work, answered by doing one.
 *
 * This exists because an avatar upload failed for days as a bare 500 and
 * nothing outside the server's log could say why. The environment this
 * project is developed in cannot reach the production host at all, so a
 * report of "R2 is configured" was never the question — the question was
 * whether the bucket accepts a write, and the only honest way to answer it
 * is to write.
 *
 * A real put and a delete, of eleven bytes, under a key nothing else uses.
 * Read permission is not enough to know: a token can list and read a bucket
 * and still refuse every PUT, which is exactly the failure that hides.
 *
 * Never run by the plain health check. Uptime monitors poll that every
 * minute and this costs two round trips to Cloudflare.
 */
export type StorageProbe = {
  configured: boolean;
  writable: boolean;
  /** The status R2 gave back, when it refused. 403 is permissions, 404 is a
   *  bucket that is not there under that name. */
  status?: number;
  error?: string;
};

export async function probeStorage(): Promise<StorageProbe> {
  const config = r2Config();
  if (!config) return { configured: false, writable: false, error: "not_configured" };

  const probeKey = "health/probe.txt";
  try {
    await putObject(config, probeKey, Buffer.from("probe\n"), "text/plain");
  } catch (error) {
    if (error instanceof R2Error) {
      console.error(`[health] storage probe refused with ${error.status}: ${error.message}`);
      return { configured: true, writable: false, status: error.status, error: "refused" };
    }
    console.error("[health] storage probe threw", error);
    return {
      configured: true,
      writable: false,
      error: error instanceof Error ? error.name : "unknown",
    };
  }
  // Best effort: a probe object left behind costs nothing and its key is
  // reused, so a failed delete cannot accumulate.
  await deleteObject(config, probeKey).catch(() => undefined);
  return { configured: true, writable: true };
}

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
