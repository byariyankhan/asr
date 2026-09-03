import { noContent, notFound, route } from "@/lib/http";
import { isUuidLike } from "@/lib/uuid";
import { forgetDevice } from "@/server/devices";
import { requireCaller } from "@/server/session";

export const DELETE = route<{ id: string }>(async (request, { params }) => {
  const caller = await requireCaller(request);
  const { id } = await params;
  if (!isUuidLike(id)) throw notFound("Device");
  await forgetDevice(caller.userId, id);
  return noContent();
});
