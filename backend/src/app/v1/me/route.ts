import { json, readJson, route } from "@/lib/http";
import { accountDelete, meUpdate } from "@/lib/schemas";
import { requestAccountDeletion, signInCheck } from "@/server/account";
import { auth } from "@/server/auth";
import { getMe, updateMe } from "@/server/me";
import { requireCaller } from "@/server/session";

// Schedules a hard delete 7 days out; signing in again before then cancels
// it (see the session hook in server/auth.ts).
export const DELETE = route(async (request) => {
  const caller = await requireCaller(request);
  const input = accountDelete.parse(await readJson(request));
  return json(await requestAccountDeletion(caller.userId, input.password, signInCheck(auth.api)));
});

export const GET = route(async (request) => {
  const caller = await requireCaller(request);
  return json(await getMe(caller.userId));
});

export const PATCH = route(async (request) => {
  const caller = await requireCaller(request);
  const input = meUpdate.parse(await readJson(request));
  return json(await updateMe(caller.userId, input));
});
