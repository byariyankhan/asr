import { json, route } from "@/lib/http";
import { progressFor } from "@/server/progress";
import { requireCaller } from "@/server/session";
import { requireWitnessView } from "@/server/witnesses";

export const GET = route<{ id: string }>(async (request, { params }) => {
  const caller = await requireCaller(request);
  const { id } = await params;
  const row = await requireWitnessView(caller.userId, id);
  // Scoped to the challenge this person agreed to watch. Not "their current
  // challenge" and not their history.
  return json(await progressFor(row.user_id, row.pact_id ?? undefined));
});
