import { json, notFound, route } from "@/lib/http";
import { getCurrentCommitment } from "@/server/commitments";
import { requireCaller } from "@/server/session";

export const GET = route(async (request) => {
  const caller = await requireCaller(request);
  const current = await getCurrentCommitment(caller.userId);
  if (!current) throw notFound("Active commitment");
  return json(current);
});
