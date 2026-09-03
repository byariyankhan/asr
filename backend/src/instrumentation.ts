// Next.js calls register() once when the server process starts. The
// watchdog loop runs inside the API container; there is no separate worker
// to deploy, and the Redis lock keeps a second replica from doubling up.
export async function register() {
  if (process.env.NEXT_RUNTIME !== "nodejs") return;
  if (process.env.NODE_ENV !== "production" && process.env.ASR_WATCHDOG !== "1") return;
  const { startWatchdogLoop } = await import("./server/watchdog");
  startWatchdogLoop();
}
