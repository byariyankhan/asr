import { json, readJson, route } from "@/lib/http";
import { deviceRegister } from "@/lib/schemas";
import { registerDevice } from "@/server/devices";
import { requireCaller } from "@/server/session";

export const POST = route(async (request) => {
  const caller = await requireCaller(request);
  const input = deviceRegister.parse(await readJson(request));
  return json(await registerDevice(caller.userId, input), 201);
});
