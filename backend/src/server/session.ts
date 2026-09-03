import { unauthorized } from "@/lib/http";
import { auth } from "./auth";
import { assertRateLimit, RATE_LIMITS, type RateLimitPolicy } from "./rate-limit";

export type Caller = { userId: string; name: string; email: string };

// Every /v1 route starts here: a valid bearer session, then the per-user
// rate limit for the route's policy (default: the general API limit).
export async function requireCaller(
  request: Request,
  policy: RateLimitPolicy = RATE_LIMITS.api,
): Promise<Caller> {
  const session = await auth.api.getSession({ headers: request.headers });
  if (!session) throw unauthorized();
  await assertRateLimit(policy, session.user.id);
  return { userId: session.user.id, name: session.user.name, email: session.user.email };
}
