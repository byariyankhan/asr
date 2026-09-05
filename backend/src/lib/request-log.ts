/**
 * One line per request, so what a phone reports can be matched to what the
 * server saw.
 *
 * DEPLOYMENT.md promised this for weeks and nothing wrote it: the only lines
 * the API produced were 500s and the watchdog's report. A beta tester saying
 * "it did not send" could not be answered, because there was no record of
 * whether anything arrived. Now every /v1 and /api/auth request leaves the
 * time, method, path, status, duration and, once a session has been
 * resolved, the user id. JSON, one object per line, because that is what
 * `docker compose logs` and every log tool after it can filter.
 *
 * Not logged: a healthy answer to the health endpoints. Uptime monitors and
 * the deploy poll them every minute, and a line that says the same thing
 * 1,440 times a day is the line everybody learns to scroll past. Query
 * strings are never logged (a reset token or the Play webhook's secret rides
 * in one), and an invite code in a path is masked.
 */
const callers = new WeakMap<Request, string>();

/** Remembers who a request turned out to be from, for the line written after it. */
export function markCaller(request: Request, userId: string): void {
  callers.set(request, userId);
}

export function logRequest(request: Request, status: number, startedAt: number): void {
  const path = new URL(request.url).pathname;
  if (status < 400 && (path === "/v1/health" || path === "/v1/health/storage")) return;
  console.info(
    JSON.stringify({
      at: new Date().toISOString(),
      method: request.method,
      path: path.replace(/(\/witnesses\/invites\/)[^/]+/, "$1<code>"),
      status,
      ms: Date.now() - startedAt,
      user: callers.get(request) ?? null,
    }),
  );
}
