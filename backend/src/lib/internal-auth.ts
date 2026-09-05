import { timingSafeEqual } from "node:crypto";

/** Shorter than this and the secret is treated as not set at all. The
 *  bootstrap writes `openssl rand -base64 32`, which is 44 characters. */
export const MIN_INTERNAL_SECRET_LENGTH = 32;

export type InternalCaller = "ok" | "proxied" | "unauthorized" | "unconfigured";

/**
 * Whether a request may run an internal job.
 *
 * Two things, and both are required. The shared secret, compared in
 * constant time, and refused outright when INTERNAL_SECRET is unset or too
 * short to be one. And that the request did not come through nginx: nginx
 * puts X-Real-IP and X-Forwarded-For on every request it proxies, and the
 * API's port is bound to 127.0.0.1, so a request carrying neither can only
 * have been made on the box itself. A secret that leaks is then still not
 * enough to run the job from the internet, and a public caller is refused
 * before the secret is so much as looked at.
 *
 * Returns why, for the log; the route answers 404 to everything but "ok".
 */
export function internalCaller(headers: Headers, expected: string | undefined = process.env.INTERNAL_SECRET): InternalCaller {
  if (headers.has("x-real-ip") || headers.has("x-forwarded-for")) return "proxied";
  if (!expected || expected.length < MIN_INTERNAL_SECRET_LENGTH) return "unconfigured";
  const given = headers.get("x-internal-secret");
  if (!given) return "unauthorized";
  const a = Buffer.from(expected);
  const b = Buffer.from(given);
  return a.length === b.length && timingSafeEqual(a, b) ? "ok" : "unauthorized";
}
