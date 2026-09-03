import { json, noContent, readJson, route } from "@/lib/http";
import { witnessPatch } from "@/lib/schemas";
import { requireCaller } from "@/server/session";
import { removeWitness, updateWitness } from "@/server/witnesses";

export const PATCH = route<{ id: string }>(async (request, { params }) => {
  const caller = await requireCaller(request);
  const { id } = await params;
  const patch = witnessPatch.parse(await readJson(request));
  return json(await updateWitness(caller.userId, id, patch));
});

export const DELETE = route<{ id: string }>(async (request, { params }) => {
  const caller = await requireCaller(request);
  const { id } = await params;
  await removeWitness(caller.userId, id);
  return noContent();
});
