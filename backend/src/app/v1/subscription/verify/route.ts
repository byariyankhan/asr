import { json, readJson, route } from "@/lib/http";
import { subscriptionVerify } from "@/lib/schemas";
import { requireCaller } from "@/server/session";
import { verifyAndStore } from "@/server/subscriptions";

export const POST = route(async (request) => {
  const caller = await requireCaller(request);
  const input = subscriptionVerify.parse(await readJson(request));
  return json(await verifyAndStore(caller.userId, input.purchase_token));
});
