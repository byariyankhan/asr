import { json, readJson, route } from "@/lib/http";
import { witnessInvite } from "@/lib/schemas";
import { assertRateLimitRoom, consumeRateLimit, RATE_LIMITS } from "@/server/rate-limit";
import { requireCaller } from "@/server/session";
import { createInvite } from "@/server/witnesses";

// Twenty invitations a day, counted as invitations rather than as attempts.
// The general per-user API limit still applies to the request itself, which
// is what stops somebody hammering this route to no effect; this one is
// about how many people can be asked to watch, and an ask the server
// refused was never made.
export const POST = route(async (request) => {
  const caller = await requireCaller(request);
  await assertRateLimitRoom(RATE_LIMITS.invites, caller.userId);
  const input = witnessInvite.parse(await readJson(request));
  const invite = await createInvite(caller.userId, input);
  await consumeRateLimit(RATE_LIMITS.invites, caller.userId);
  return json(invite, 201);
});
