import { HttpError, json, route } from "@/lib/http";
import { internalCaller } from "@/lib/internal-auth";
import { runWatchdog } from "@/server/watchdog";

// Manual trigger for the 15-minute job: ops on the box, and a cron fallback
// if the in-process loop is ever replaced. Only a caller on the VPS itself
// with the shared secret gets past `internalCaller` (lib/internal-auth.ts);
// everything else, the public internet included, is a 404 before the secret
// is looked at, so the route is not advertised to anybody it refuses. The
// run itself is single-flight (a Redis lock and a process mutex), so two
// triggers at once are one run and one "skipped".
export const POST = route(async (request) => {
  const caller = internalCaller(request.headers);
  if (caller !== "ok") {
    console.warn(`[internal] watchdog trigger refused: ${caller}`);
    throw new HttpError(404, "not_found", "Not found.");
  }
  const report = await runWatchdog();
  return json(report ?? { skipped: "another run holds the lock" });
});
