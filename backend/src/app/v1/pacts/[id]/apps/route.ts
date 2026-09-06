import { json, readJson, route } from "@/lib/http";
import { pactAppAdd } from "@/lib/schemas";
import { addAppToPact } from "@/server/pacts";
import { assertRateLimit, RATE_LIMITS } from "@/server/rate-limit";
import { requireCaller } from "@/server/session";

// One more app under a limit, on a challenge that is running. 200 with the
// pact as GET /pacts/current returns it. 409 pact_closed, app_already_in_pact
// or too_many_apps; nothing here ever loosens a limit or removes an app.
export const POST = route<{ id: string }>(async (request, { params }) => {
  const caller = await requireCaller(request);
  const { id } = await params;
  const input = pactAppAdd.parse(await readJson(request));
  await assertRateLimit(RATE_LIMITS.pactApps, caller.userId);
  return json(await addAppToPact(caller.userId, id, input));
});
