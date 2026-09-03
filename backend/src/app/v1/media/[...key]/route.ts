import { notFound, route } from "@/lib/http";
import { readAvatar } from "@/server/avatar";
import { requireCaller } from "@/server/session";

/**
 * Serves a private object to a caller entitled to see it.
 *
 * Cache-Control is `private`: the object is behind a session, and a shared
 * cache holding somebody's face keyed only by URL is how a private bucket
 * stops being private. The key is immutable — replacing a photo mints a new
 * one — so a long max-age is safe and means a witness list of ten faces is
 * ten requests once, not on every open.
 */
export const GET = route<{ key: string[] }>(async (request, { params }) => {
  const caller = await requireCaller(request);
  const { key } = await params;
  const path = key.join("/");
  if (!path) throw notFound("Image");

  const object = await readAvatar(caller.userId, path);
  const headers: Record<string, string> = {
    "content-type": object.contentType,
    "cache-control": "private, max-age=604800, immutable",
    // The bytes were validated as a JPEG and stripped on the way in, but a
    // browser deciding for itself what a file is remains the cheapest way
    // to turn an image host into an XSS host.
    "x-content-type-options": "nosniff",
    "content-disposition": "inline",
  };
  if (object.etag) headers.etag = object.etag;
  return new Response(object.body, { status: 200, headers });
});
