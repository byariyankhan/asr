import { clientIpFromHeaders } from "@/lib/client-ip";
import { notFound, route } from "@/lib/http";
import { readAvatar } from "@/server/avatar";
import { assertRateLimit, RATE_LIMITS } from "@/server/rate-limit";

/**
 * A profile photo, to anybody who has the URL.
 *
 * Public on purpose. The case that settles it is the witness invite: the
 * person opening joinasr.io/w/<code> has no account yet, and the preview has
 * to show them who is asking. That endpoint already gives the inviter's name
 * without a session, so the photo belongs in the same place. A profile
 * picture is a profile picture.
 *
 * Two things still hold. The key has to be that owner's *current* photo, so
 * replacing it stops the old URL resolving and nothing can serve a face
 * somebody took down; and the owner has to still exist, so a deleted account
 * goes dark immediately rather than waiting for the purge.
 *
 * Rate limited per IP because it is now unauthenticated, which otherwise
 * makes it a free image host paid for out of the VPS's bandwidth.
 */
export const GET = route<{ key: string[] }>(async (request, { params }) => {
  await assertRateLimit(RATE_LIMITS.media, clientIpFromHeaders(request.headers) ?? "unknown");
  const { key } = await params;
  const path = key.join("/");
  if (!path) throw notFound("Image");

  const object = await readAvatar(path);
  const headers: Record<string, string> = {
    "content-type": object.contentType,
    // A key never changes contents, so this can be cached hard and shared.
    "cache-control": "public, max-age=604800, immutable",
    // The bytes were validated as a JPEG and stripped on the way in, but a
    // browser deciding for itself what a file is remains the cheapest way to
    // turn an image host into an XSS host.
    "x-content-type-options": "nosniff",
    "content-disposition": "inline",
  };
  if (object.etag) headers.etag = object.etag;
  return new Response(object.body, { status: 200, headers });
});
