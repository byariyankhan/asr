import { json, readJson, route } from "@/lib/http";
import { eventCreate } from "@/lib/schemas";
import { recordDeviceEvent } from "@/server/events";
import { assertRateLimit, RATE_LIMITS } from "@/server/rate-limit";
import { requireCaller } from "@/server/session";

// 201 when the event is new, 200 with the existing row when the phone is
// retrying an id the server already has.
export const POST = route<{ id: string }>(async (request, { params }) => {
  const caller = await requireCaller(request);
  const { id } = await params;
  const input = eventCreate.parse(await readJson(request));
  await assertRateLimit(RATE_LIMITS.events, `${caller.userId}:${id}`);
  const result = await recordDeviceEvent(caller.userId, id, input);
  return json(result.event, result.created ? 201 : 200);
});
