import { noContent, readJson, route } from "@/lib/http";
import { notificationsRead } from "@/lib/schemas";
import { markRead } from "@/server/inbox";
import { requireCaller } from "@/server/session";

export const POST = route(async (request) => {
  const caller = await requireCaller(request);
  const input = notificationsRead.parse(await readJson(request));
  await markRead(caller.userId, "all" in input ? "all" : input.ids);
  return noContent();
});
