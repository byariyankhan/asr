import { json, route } from "@/lib/http";
import { progressFor } from "@/server/progress";
import { requireCaller } from "@/server/session";

export const GET = route(async (request) => {
  const caller = await requireCaller(request);
  return json(await progressFor(caller.userId));
});
