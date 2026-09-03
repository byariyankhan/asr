import { json, route } from "@/lib/http";
import { listQuery } from "@/lib/schemas";
import { listMyReactions } from "@/server/reactions";
import { requireCaller } from "@/server/session";

export const GET = route(async (request) => {
  const caller = await requireCaller(request);
  const q = listQuery.parse(Object.fromEntries(new URL(request.url).searchParams));
  return json({ items: await listMyReactions(caller.userId, q.limit) });
});
