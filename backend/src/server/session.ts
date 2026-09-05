import { unauthorized } from "@/lib/http";
import { markCaller } from "@/lib/request-log";
import { auth } from "./auth";
import { assertRateLimit, RATE_LIMITS, type RateLimitPolicy } from "./rate-limit";

export type Caller = { userId: string; name: string; email: string; sessionId: string };

// Every /v1 route starts here: a valid bearer session, then the per-user
// rate limit for the route's policy (default: the general API limit).
export async function requireCaller(
  request: Request,
  policy: RateLimitPolicy = RATE_LIMITS.api,
): Promise<Caller> {
  const session = await auth.api.getSession({ headers: request.headers });
  if (!session) throw unauthorized();
  markCaller(request, session.user.id);
  await assertRateLimit(policy, session.user.id);
  return { userId: session.user.id, name: session.user.name, email: session.user.email, sessionId: session.session.id };
}

/**
 * The caller, when there is one, and null when there is not.
 *
 * For a route that is genuinely public but answers a little differently to
 * somebody signed in. The witness invite is the one: anybody with the code
 * may read who is asking -- that is the point, they have no account yet --
 * but the person who *sent* it should be told so rather than offered a
 * button that acceptInvite will refuse.
 *
 * No rate limit here. The route has already applied its own, by IP, which
 * is the only key available when most callers are anonymous.
 */
export async function optionalCaller(request: Request): Promise<Caller | null> {
  try {
    const session = await auth.api.getSession({ headers: request.headers });
    if (!session) return null;
    markCaller(request, session.user.id);
    return { userId: session.user.id, name: session.user.name, email: session.user.email, sessionId: session.session.id };
  } catch {
    // A malformed or expired token on a public route is not an error, it is
    // an anonymous reader.
    return null;
  }
}
