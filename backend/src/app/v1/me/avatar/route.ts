import { HttpError, json, route } from "@/lib/http";
import { clearAvatar, setAvatar, MAX_BYTES } from "@/server/avatar";
import { RATE_LIMITS, assertRateLimit } from "@/server/rate-limit";
import { requireCaller } from "@/server/session";

/**
 * The photo is sent as raw JPEG bytes, not multipart. There is exactly one
 * field, and multipart would mean parsing a format with its own boundary
 * handling and its own decompression-bomb history to carry a single blob.
 */
export const POST = route(async (request) => {
  const caller = await requireCaller(request);
  await assertRateLimit(RATE_LIMITS.avatar, caller.userId);

  const declared = request.headers.get("content-length");
  // Refused before reading a byte when the sender says how big it is. The
  // check after the read is the one that matters, because a chunked upload
  // declares nothing.
  if (declared && Number(declared) > MAX_BYTES) {
    throw new HttpError(413, "image_too_large", "That photo is too large.");
  }

  const bytes = Buffer.from(await request.arrayBuffer());
  return json(await setAvatar(caller.userId, bytes));
});

export const DELETE = route(async (request) => {
  const caller = await requireCaller(request);
  return json(await clearAvatar(caller.userId));
});
