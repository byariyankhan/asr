import { noContent, route } from "@/lib/http";
import { requireCaller } from "@/server/session";
import { declineInvite } from "@/server/witnesses";

export const POST = route<{ code: string }>(async (request, { params }) => {
  const caller = await requireCaller(request);
  const { code } = await params;
  await declineInvite(caller.userId, code);
  return noContent();
});
