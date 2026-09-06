import { json, route } from "@/lib/http";
import { requestEmailVerification } from "@/server/account";
import { auth } from "@/server/auth";
import { assertRateLimit, RATE_LIMITS } from "@/server/rate-limit";
import { requireCaller } from "@/server/session";

// The confirmation link, on request. Sign-up does not send one and Better
// Auth's own send-verification-email is not offered, so this is the only
// place one is sent from -- per account, three a day and one every five
// minutes, because each is a paid email for a step that is not required.
// Better Auth signs the token and calls the sender in server/auth.ts; the
// link is joinasr.io/verify/<token>, and opening it is the confirmation.
export const POST = route(async (request) => {
  const caller = await requireCaller(request, RATE_LIMITS.emailVerify);
  await assertRateLimit(RATE_LIMITS.emailVerifyBurst, caller.userId);
  const result = await requestEmailVerification(caller.userId, async (email) => {
    await auth.api.sendVerificationEmail({ body: { email }, headers: request.headers });
  });
  return json(result);
});
