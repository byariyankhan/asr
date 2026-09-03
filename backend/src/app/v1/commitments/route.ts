import { json, readJson, route } from "@/lib/http";
import { commitmentCreate, listQuery } from "@/lib/schemas";
import { createCommitment, listCommitments } from "@/server/commitments";
import { requireCaller } from "@/server/session";

export const POST = route(async (request) => {
  const caller = await requireCaller(request);
  const input = commitmentCreate.parse(await readJson(request));
  return json(await createCommitment(caller.userId, input), 201);
});

export const GET = route(async (request) => {
  const caller = await requireCaller(request);
  const q = listQuery.parse(Object.fromEntries(new URL(request.url).searchParams));
  return json(await listCommitments(caller.userId, q.cursor, q.limit));
});
