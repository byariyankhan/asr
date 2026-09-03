import { json, readJson, route } from "@/lib/http";
import { activityCreate } from "@/lib/schemas";
import { createActivity, listActivities } from "@/server/activities";
import { requireCaller } from "@/server/session";

export const POST = route<{ id: string }>(async (request, { params }) => {
  const caller = await requireCaller(request);
  const { id } = await params;
  const input = activityCreate.parse(await readJson(request));
  const result = await createActivity(caller.userId, id, input);
  return json(result.activity, result.created ? 201 : 200);
});

export const GET = route<{ id: string }>(async (request, { params }) => {
  const caller = await requireCaller(request);
  const { id } = await params;
  return json({ items: await listActivities(caller.userId, id) });
});
