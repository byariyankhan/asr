import { json, readJson, route } from "@/lib/http";
import { deviceRegister } from "@/lib/schemas";
import { takeOverOnPhone } from "@/server/one-device";
import { requireCaller } from "@/server/session";

export const POST = route(async (request) => {
  const caller = await requireCaller(request);
  const input = deviceRegister.parse(await readJson(request));
  // Registering is signing in here, and signing in here signs out
  // everywhere else. One account, one phone.
  return json(await takeOverOnPhone(caller.userId, caller.sessionId, input), 201);
});
