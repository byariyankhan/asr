import { json, route } from "@/lib/http";
import { requireCaller } from "@/server/session";
import { listWitnesses } from "@/server/witnesses";

export const GET = route(async (request) => {
  const caller = await requireCaller(request);
  return json(await listWitnesses(caller.userId));
});
