import { json, readJson, route } from "@/lib/http";
import { activityComplete } from "@/lib/schemas";
import { completeActivity } from "@/server/activities";
import { assertRateLimit, RATE_LIMITS } from "@/server/rate-limit";
import { requireCaller } from "@/server/session";

export const POST = route<{ id: string }>(async (request, { params }) => {
  const caller = await requireCaller(request);
  const { id } = await params;
  const input = activityComplete.parse(await readJson(request));
  await assertRateLimit(RATE_LIMITS.events, caller.userId);
  const result = await completeActivity(caller.userId, id, input);
  return json({ activity: result.activity, event: result.event }, result.created ? 201 : 200);
});
