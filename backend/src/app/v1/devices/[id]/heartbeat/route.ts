import { noContent, notFound, readJson, route } from "@/lib/http";
import { heartbeat } from "@/lib/schemas";
import { isUuidLike } from "@/lib/uuid";
import { recordHeartbeat } from "@/server/devices";
import { requireCaller } from "@/server/session";

export const POST = route<{ id: string }>(async (request, { params }) => {
  const caller = await requireCaller(request);
  const { id } = await params;
  if (!isUuidLike(id)) throw notFound("Device");
  const input = heartbeat.parse(await readJson(request));
  await recordHeartbeat(caller.userId, id, input);
  return noContent();
});
