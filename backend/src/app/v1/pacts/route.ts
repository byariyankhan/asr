import { json, readJson, route } from "@/lib/http";
import { pactCreate, listQuery } from "@/lib/schemas";
import { createPact, listPacts } from "@/server/pacts";
import { requireCaller } from "@/server/session";

export const POST = route(async (request) => {
  const caller = await requireCaller(request);
  const input = pactCreate.parse(await readJson(request));
  return json(await createPact(caller.userId, input), 201);
});

export const GET = route(async (request) => {
  const caller = await requireCaller(request);
  const q = listQuery.parse(Object.fromEntries(new URL(request.url).searchParams));
  return json(await listPacts(caller.userId, q.cursor, q.limit));
});
