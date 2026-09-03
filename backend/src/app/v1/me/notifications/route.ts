import { json, route } from "@/lib/http";
import { listQuery } from "@/lib/schemas";
import { listInbox } from "@/server/inbox";
import { requireCaller } from "@/server/session";

export const GET = route(async (request) => {
  const caller = await requireCaller(request);
  const q = listQuery.parse(Object.fromEntries(new URL(request.url).searchParams));
  return json(await listInbox(caller.userId, q.cursor, q.limit));
});
