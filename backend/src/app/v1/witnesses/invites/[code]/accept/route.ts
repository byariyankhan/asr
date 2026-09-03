import { json, route } from "@/lib/http";
import { requireCaller } from "@/server/session";
import { acceptInvite } from "@/server/witnesses";

export const POST = route<{ code: string }>(async (request, { params }) => {
  const caller = await requireCaller(request);
  const { code } = await params;
  return json(await acceptInvite(caller.userId, code));
});
