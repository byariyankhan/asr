import { NextResponse } from "next/server";
import { healthReport, probeStorage } from "@/server/health";

export const dynamic = "force-dynamic";

/**
 * `?probe=storage` additionally writes and deletes a small object in R2.
 *
 * Opt-in because it costs two round trips to Cloudflare and this route is
 * what uptime monitors poll. It reports the status R2 gave back and nothing
 * else — no bucket name, no account id, no R2 body — so it is safe on a
 * route that takes no session, and it answers in one URL the question that
 * otherwise needs a shell on the server.
 */
export async function GET(request: Request) {
  const report = await healthReport();
  const wants = new URL(request.url).searchParams.get("probe");
  const storage = wants === "storage" ? await probeStorage() : undefined;
  return NextResponse.json(
    storage ? { ...report, storage } : report,
    { status: report.ok ? 200 : 503 },
  );
}
