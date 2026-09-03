import { json, readJson, route } from "@/lib/http";
import { meUpdate } from "@/lib/schemas";
import { getMe, updateMe } from "@/server/me";
import { requireCaller } from "@/server/session";

export const GET = route(async (request) => {
  const caller = await requireCaller(request);
  return json(await getMe(caller.userId));
});

export const PATCH = route(async (request) => {
  const caller = await requireCaller(request);
  const input = meUpdate.parse(await readJson(request));
  return json(await updateMe(caller.userId, input));
});
