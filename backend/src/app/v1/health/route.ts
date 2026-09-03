import { NextResponse } from "next/server";
import { healthReport } from "@/server/health";

export const dynamic = "force-dynamic";

export async function GET() {
  const report = await healthReport();
  return NextResponse.json(report, { status: report.ok ? 200 : 503 });
}
