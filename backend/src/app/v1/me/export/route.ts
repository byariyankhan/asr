import { NextResponse } from "next/server";
import { route } from "@/lib/http";
import { exportAccount } from "@/server/account";
import { RATE_LIMITS } from "@/server/rate-limit";
import { requireCaller } from "@/server/session";

// The user's whole ledger as one JSON document, served directly: nothing to
// store, nothing to expire.
export const GET = route(async (request) => {
  const caller = await requireCaller(request, RATE_LIMITS.export);
  const data = await exportAccount(caller.userId);
  return new NextResponse(JSON.stringify(data, null, 2), {
    headers: {
      "content-type": "application/json",
      "content-disposition": `attachment; filename="asr-export-${data.exported_at.slice(0, 10)}.json"`,
    },
  });
});
