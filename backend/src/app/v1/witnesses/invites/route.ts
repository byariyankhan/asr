import { json, readJson, route } from "@/lib/http";
import { witnessInvite } from "@/lib/schemas";
import { RATE_LIMITS } from "@/server/rate-limit";
import { requireCaller } from "@/server/session";
import { createInvite } from "@/server/witnesses";

export const POST = route(async (request) => {
  const caller = await requireCaller(request, RATE_LIMITS.invites);
  const input = witnessInvite.parse(await readJson(request));
  return json(await createInvite(caller.userId, input), 201);
});
