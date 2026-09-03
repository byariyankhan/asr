import { json, notFound, route } from "@/lib/http";
import { getCurrentPact } from "@/server/pacts";
import { requireCaller } from "@/server/session";

export const GET = route(async (request) => {
  const caller = await requireCaller(request);
  const current = await getCurrentPact(caller.userId);
  if (!current) throw notFound("Active pact");
  return json(current);
});
