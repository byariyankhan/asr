import { json, route } from "@/lib/http";
import { progressFor } from "@/server/progress";
import { requireCaller } from "@/server/session";
import { requireWitnessView } from "@/server/witnesses";

export const GET = route<{ id: string }>(async (request, { params }) => {
  const caller = await requireCaller(request);
  const { id } = await params;
  const row = await requireWitnessView(caller.userId, id);
  return json(await progressFor(row.user_id));
});
