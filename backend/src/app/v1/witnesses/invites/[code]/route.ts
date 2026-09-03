import { clientIpFromHeaders } from "@/lib/client-ip";
import { json, route } from "@/lib/http";
import { assertRateLimit, RATE_LIMITS } from "@/server/rate-limit";
import { peekInvite } from "@/server/witnesses";

// Public: the accept screen and the joinasr.io/w/<code> fallback page show
// who is asking before the witness has an account.
export const GET = route<{ code: string }>(async (request, { params }) => {
  await assertRateLimit(RATE_LIMITS.invitePeek, clientIpFromHeaders(request.headers) ?? "unknown");
  const { code } = await params;
  return json(await peekInvite(code));
});
