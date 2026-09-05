import { NextResponse } from "next/server";
import { clientIpFromHeaders } from "@/lib/client-ip";
import { healthReport, probeStorage } from "@/server/health";
import { checkRateLimit, RATE_LIMITS } from "@/server/rate-limit";

export const dynamic = "force-dynamic";

/**
 * `?probe=storage` additionally writes and deletes a small object in R2.
 *
 * Opt-in because it costs two round trips to Cloudflare and this route is
 * what uptime monitors poll. It reports the status R2 gave back and nothing
 * else — no bucket name, no account id, no R2 body — so it is safe on a
 * route that takes no session, and it answers in one URL the question that
 * otherwise needs a shell on the server. The probe is rate-limited per IP
 * because each one is a paid write to the bucket from an unauthenticated
 * request; the report itself is not.
 */
export async function GET(request: Request) {
  const report = await healthReport();
  const wants = new URL(request.url).searchParams.get("probe");
  if (wants !== "storage") return NextResponse.json(report, { status: report.ok ? 200 : 503 });
  const limit = await checkRateLimit(RATE_LIMITS.storageProbe, clientIpFromHeaders(request.headers) ?? "unknown");
  if (!limit.allowed) {
    return NextResponse.json(
      { ...report, storage: { error: "rate_limited" } },
      { status: 429, headers: { "Retry-After": String(limit.resetSeconds) } },
    );
  }
  const storage = await probeStorage();
  return NextResponse.json({ ...report, storage }, { status: report.ok ? 200 : 503 });
}
