import { json, route } from "@/lib/http";
import { getCommitmentWithEvents } from "@/server/commitments";
import { requireCaller } from "@/server/session";

export const GET = route<{ id: string }>(async (request, { params }) => {
  const caller = await requireCaller(request);
  const { id } = await params;
  return json(await getCommitmentWithEvents(caller.userId, id));
});
