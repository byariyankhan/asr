import { z } from "zod";
import { json, readJson, route } from "@/lib/http";
import { uuid } from "@/lib/schemas";
import { claimPact } from "@/server/pacts";
import { requireCaller } from "@/server/session";

const claim = z.object({ device_id: uuid });

// This phone is the one enforcing the challenge from now on. Sent by a phone
// that restored a pact it did not create: a reinstall, a replacement handset,
// a second phone signed into the same account.
export const POST = route<{ id: string }>(async (request, { params }) => {
  const caller = await requireCaller(request);
  const { id } = await params;
  const input = claim.parse(await readJson(request));
  return json(await claimPact(caller.userId, id, input.device_id));
});
