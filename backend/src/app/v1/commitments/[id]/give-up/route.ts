import { json, readJson, route } from "@/lib/http";
import { giveUp } from "@/lib/schemas";
import { recordDeviceEvent } from "@/server/events";
import { requireCaller } from "@/server/session";

// A deliberate early exit from the app's own UI: the same ledger row as a
// device-detected break, with reason user_gave_up.
export const POST = route<{ id: string }>(async (request, { params }) => {
  const caller = await requireCaller(request);
  const { id } = await params;
  const input = giveUp.parse(await readJson(request));
  const result = await recordDeviceEvent(caller.userId, id, {
    id: input.id,
    type: "broken",
    reason: "user_gave_up",
    occurred_at: new Date().toISOString(),
  });
  return json(result.event, result.created ? 201 : 200);
});
