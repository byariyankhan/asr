import { lookup } from "node:dns/promises";
import { sql } from "kysely";
import { db } from "./db/client";
import { configProblem, deleteObject, putObject, r2Config, R2Error, type R2Config } from "./r2";
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
  /** A short machine-readable word for what went wrong. */
  error?: string;
  /** Whether the endpoint's name resolves at all, filled in only when the
   *  write failed without R2 ever answering. */
  dns?: string;
  /** The error's own words and those of whatever it wrapped, with every
   *  configured value removed. */
  detail?: string[];
};

/**
 * Everything an error and its causes say, flattened, with the account id,
 * bucket, key id and secret cut out of it.
 *
 * "TypeError" alone was the first answer this probe gave, and it named the
 * class of a failure that could equally have been DNS, TLS, or a header the
 * runtime refused to build. The message and the `cause` chain underneath it
 * are where undici puts the actual reason — `fetch failed` wrapping
 * `getaddrinfo ENOTFOUND`, or an invalid header value naming the header.
 *
 * Redaction is by exact substring against the four configured values rather
 * than by pattern, so it cannot be defeated by an error message quoting one
 * of them in an unexpected shape. This route takes no session.
 */
function explain(error: unknown, config: R2Config): string[] {
  const secrets: Array<[string, string]> = [
    [config.secretAccessKey, "<secret>"],
    [config.accessKeyId, "<key-id>"],
    [config.accountId, "<account>"],
    [config.bucket, "<bucket>"],
  ];
  const redact = (text: string) =>
    secrets.reduce((acc, [value, mask]) => (value ? acc.split(value).join(mask) : acc), text);

  const lines: string[] = [];
  let current: unknown = error;
  for (let depth = 0; current && depth < 4; depth += 1) {
    if (!(current instanceof Error)) {
      lines.push(redact(String(current)));
      break;
    }
    const code = (current as NodeJS.ErrnoException).code;
    lines.push(redact(`${current.name}: ${current.message}${code ? ` (${code})` : ""}`));
    current = current.cause;
  }
  return lines;
}

/** Whether the endpoint's name resolves from inside this container. Asked
 *  only after a write has already failed, to separate "the network never
 *  carried the request" from "R2 said no". */
async function resolves(host: string): Promise<string> {
  try {
    const { address } = await lookup(host);
    return address ? "resolves" : "no_address";
  } catch (error) {
    const code = (error as NodeJS.ErrnoException).code;
    return code ? `fails_${code}` : "fails";
  }
}

export async function probeStorage(): Promise<StorageProbe> {
  const config = r2Config();
  if (!config) return { configured: false, writable: false, error: "not_configured" };

  // Before the network: a value with whitespace in it cannot become a
  // hostname or a header, and the failure it produces deep inside fetch
  // names nothing.
  const problem = configProblem(config);
  if (problem) {
    console.error(`[health] storage configuration rejected: ${problem}`);
    return { configured: true, writable: false, error: problem };
  }

  const probeKey = "health/probe.txt";
  try {
    await putObject(config, probeKey, Buffer.from("probe\n"), "text/plain");
  } catch (error) {
    if (error instanceof R2Error) {
      console.error(`[health] storage probe refused with ${error.status}: ${error.message}`);
      return { configured: true, writable: false, status: error.status, error: "refused" };
    }
    const detail = explain(error, config);
    console.error("[health] storage probe threw", error);
    return {
      configured: true,
      writable: false,
      error: error instanceof Error ? error.name : "unknown",
      dns: await resolves(`${config.accountId}.r2.cloudflarestorage.com`),
      detail,
    };
  }
  // Best effort: a probe object left behind costs nothing and its key is
  // reused, so a failed delete cannot accumulate.
  await deleteObject(config, probeKey).catch(() => undefined);
  return { configured: true, writable: true };
}

/**
 * The commit this container was started from.
 *
 * Set by the deploy at `up -d` time, which makes it evidence rather than
 * decoration: a deploy that builds an image and never recreates the
 * container leaves the old value here, and the deploy's own health check
 * refuses to pass. That exact failure ran silently for several releases —
 * `docker compose run migrate` was reading the deploy script off stdin and
 * swallowing every line after itself, so the API was never restarted while
 * the workflow went green.
 */
const COMMIT = process.env.ASR_COMMIT?.trim() || null;

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
    commit: COMMIT,
    db: dbOk,
    redis: redisOk,
    watchdog_stale: !Number.isFinite(lastRunMs) || Date.now() - lastRunMs > WATCHDOG_STALE_MS,
  };
}
