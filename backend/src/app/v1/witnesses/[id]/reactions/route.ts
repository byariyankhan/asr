import { json, noContent, readJson, route } from "@/lib/http";
import { reactionCreate, reactionDelete } from "@/lib/schemas";
import { react, unreact } from "@/server/reactions";
import { requireCaller } from "@/server/session";

// POST sets (or replaces) this witness's reaction to one event; DELETE
// removes it. Body carries the event id in both cases.
export const POST = route<{ id: string }>(async (request, { params }) => {
  const caller = await requireCaller(request);
  const { id } = await params;
  const input = reactionCreate.parse(await readJson(request));
  return json(await react(caller.userId, id, input.event_id, input.emoji));
});

export const DELETE = route<{ id: string }>(async (request, { params }) => {
  const caller = await requireCaller(request);
  const { id } = await params;
  const input = reactionDelete.parse(await readJson(request));
  await unreact(caller.userId, id, input.event_id);
  return noContent();
});
