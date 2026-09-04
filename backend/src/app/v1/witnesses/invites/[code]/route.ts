import { clientIpFromHeaders } from "@/lib/client-ip";
import { json, route } from "@/lib/http";
import { assertRateLimit, RATE_LIMITS } from "@/server/rate-limit";
import { optionalCaller } from "@/server/session";
import { peekInvite } from "@/server/witnesses";

// Public: the accept screen and the joinasr.io/w/<code> page show who is
// asking before the witness has an account.
//
// The session is read when there is one, and required never. It only decides
// `own` -- whether the reader is the person who sent this -- so that the app
// can say so instead of offering a button that will be refused.
export const GET = route<{ code: string }>(async (request, { params }) => {
  await assertRateLimit(RATE_LIMITS.invitePeek, clientIpFromHeaders(request.headers) ?? "unknown");
  const { code } = await params;
  const caller = await optionalCaller(request);
  return json(await peekInvite(code, caller?.userId));
});
