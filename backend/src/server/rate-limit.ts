import { HttpError } from "@/lib/http";
import { key, redis } from "./redis";

// Fixed-window counters in Redis (one INCR + EXPIRE per hit), with an
// in-process fallback while Redis is unreachable: the limiter's dependency
// being down is exactly when limits should not disappear. Ported from
// Bookween's limiter; same trade-offs, same breaker.

export type RateLimitPolicy = {
  name: string;
  limit: number;
  windowSeconds: number;
};

export type RateLimitResult = {
  allowed: boolean;
  limit: number;
  remaining: number;
  resetSeconds: number;
};

type Bucket = { count: number; resetAt: number };
const globalForLimiter = globalThis as unknown as { __asrBuckets?: Map<string, Bucket> };
const localBuckets = (globalForLimiter.__asrBuckets ??= new Map<string, Bucket>());

function localHit(k: string, windowMs: number): Bucket {
  const now = Date.now();
  const existing = localBuckets.get(k);
  if (!existing || existing.resetAt <= now) {
    const fresh = { count: 1, resetAt: now + windowMs };
    localBuckets.set(k, fresh);
    if (localBuckets.size > 10_000) {
      for (const [id, b] of localBuckets) if (b.resetAt <= now) localBuckets.delete(id);
    }
    return fresh;
  }
  existing.count += 1;
  return existing;
}

const BREAKER_MS = 10_000;
let breakerOpenUntil = 0;

function tripBreaker(error: unknown) {
  const wasOpen = Date.now() < breakerOpenUntil;
  breakerOpenUntil = Date.now() + BREAKER_MS;
  if (!wasOpen) {
    console.error(
      `[rate-limit] redis unavailable, counting in-process for ${BREAKER_MS / 1000}s:`,
      error instanceof Error ? error.message : error,
    );
  }
}

export async function checkRateLimit(policy: RateLimitPolicy, identity: string): Promise<RateLimitResult> {
  const windowMs = policy.windowSeconds * 1000;
  const windowIndex = Math.floor(Date.now() / windowMs);
  const k = key("rl", policy.name, identity, String(windowIndex));

  const client = Date.now() >= breakerOpenUntil ? redis() : null;
  if (client) {
    try {
      const replies = await client.multi().incr(k).expire(k, policy.windowSeconds).exec();
      const incr = replies?.[0];
      if (!incr || incr[0]) throw incr?.[0] ?? new Error("empty multi reply");
      const used = Number(incr[1]);
      return {
        allowed: used <= policy.limit,
        limit: policy.limit,
        remaining: Math.max(0, policy.limit - used),
        resetSeconds: Math.ceil(((windowIndex + 1) * windowMs - Date.now()) / 1000),
      };
    } catch (error) {
      tripBreaker(error);
    }
  }

  const hit = localHit(k, windowMs);
  return {
    allowed: hit.count <= policy.limit,
    limit: policy.limit,
    remaining: Math.max(0, policy.limit - hit.count),
    resetSeconds: Math.ceil((hit.resetAt - Date.now()) / 1000),
  };
}

// Throws the documented 429 (Retry-After set by the route wrapper).
export async function assertRateLimit(policy: RateLimitPolicy, identity: string): Promise<void> {
  const result = await checkRateLimit(policy, identity);
  if (!result.allowed) {
    throw new HttpError(429, "rate_limited", "Too many requests.", { retryAfter: result.resetSeconds });
  }
}

// Limits from docs/API.md.
export const RATE_LIMITS = {
  /** sign-up / sign-in / password reset, per IP */
  authCredentials: { name: "auth-cred", limit: 10, windowSeconds: 900 },
  /** everything else under /api/auth, per IP */
  authOther: { name: "auth-other", limit: 120, windowSeconds: 60 },
  /** general /v1 traffic, per user */
  api: { name: "api", limit: 300, windowSeconds: 60 },
  /** event ingestion, per device */
  events: { name: "events", limit: 120, windowSeconds: 3600 },
  /** witness invite creation, per user */
  invites: { name: "invites", limit: 20, windowSeconds: 86_400 },
  /** public invite lookup, per IP */
  invitePeek: { name: "invite-peek", limit: 60, windowSeconds: 60 },
  /** full account export, per user */
  export: { name: "export", limit: 5, windowSeconds: 86_400 },
  /** public profile photo reads, per IP. Generous: a witness list is a
   *  handful of images and browsers cache them for a week, so anything
   *  近 this ceiling is a scraper. */
  media: { name: "media", limit: 600, windowSeconds: 300 },
  /** profile photo upload, per user. Low on purpose: it is the one route
   *  that accepts a megabyte from a client, and nobody changes their face
   *  twenty times an hour. */
  avatar: { name: "avatar", limit: 20, windowSeconds: 3600 },
} as const satisfies Record<string, RateLimitPolicy>;
