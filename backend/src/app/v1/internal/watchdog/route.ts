import { timingSafeEqual } from "node:crypto";
import { HttpError, json, route } from "@/lib/http";
import { runWatchdog } from "@/server/watchdog";

// Manual trigger for the 15-minute job (ops, and a cron fallback if the
// in-process loop is ever replaced). Guarded by a shared secret, not a user
// session.
function authorized(request: Request): boolean {
  const expected = process.env.INTERNAL_SECRET;
  const given = request.headers.get("x-internal-secret");
  if (!expected || !given) return false;
  const a = Buffer.from(expected);
  const b = Buffer.from(given);
  return a.length === b.length && timingSafeEqual(a, b);
}

export const POST = route(async (request) => {
  if (!authorized(request)) throw new HttpError(404, "not_found", "Not found.");
  const report = await runWatchdog();
  return json(report ?? { skipped: "another run holds the lock" });
});
