import { NextResponse } from "next/server";
import { clientIpFromHeaders } from "@/lib/client-ip";
import { probeStorage } from "@/server/health";
import { checkRateLimit, RATE_LIMITS } from "@/server/rate-limit";

export const dynamic = "force-dynamic";

/**
 * Whether photo uploads can work, on a path rather than behind a query
 * string.
 *
 * `/v1/health?probe=storage` existed first and was reported as answering
 * without the storage field — a phone's address bar had eaten the parameter,
 * which is exactly the kind of thing a diagnostic must not be vulnerable to.
 * A URL you can type from memory and cannot half-type is worth its own file.
 *
 * It writes and deletes a small object, because read permission is not
 * evidence that a PUT will be accepted: a token can list and read a bucket
 * and refuse every upload, which is precisely the failure that hides. What
 * comes back is the status R2 gave and nothing else — no bucket name, no
 * account id, no R2 body — so it is safe on a route that takes no session.
 *
 * Rate-limited per IP all the same: each hit is a paid write to the bucket
 * on behalf of whoever asked, and "safe to answer" is not "free to answer".
 */
export async function GET(request: Request) {
  const limit = await checkRateLimit(RATE_LIMITS.storageProbe, clientIpFromHeaders(request.headers) ?? "unknown");
  if (!limit.allowed) {
    return NextResponse.json(
      { error: "rate_limited", message: "Too many probes." },
      { status: 429, headers: { "Retry-After": String(limit.resetSeconds) } },
    );
  }
  const storage = await probeStorage();
  return NextResponse.json(storage, { status: storage.writable ? 200 : 503 });
}
