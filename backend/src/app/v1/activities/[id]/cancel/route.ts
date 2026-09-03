import { noContent, route } from "@/lib/http";
import { cancelActivity } from "@/server/activities";
import { requireCaller } from "@/server/session";

export const POST = route<{ id: string }>(async (request, { params }) => {
  const caller = await requireCaller(request);
  const { id } = await params;
  await cancelActivity(caller.userId, id);
  return noContent();
});
