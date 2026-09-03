import { noContent, readJson, route } from "@/lib/http";
import { summaryCreate } from "@/lib/schemas";
import { requireCaller } from "@/server/session";
import { upsertDailySummary } from "@/server/summary";

export const POST = route<{ id: string }>(async (request, { params }) => {
  const caller = await requireCaller(request);
  const { id } = await params;
  const input = summaryCreate.parse(await readJson(request));
  await upsertDailySummary(caller.userId, id, input);
  return noContent();
});
