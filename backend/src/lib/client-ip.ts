import net from "node:net";

// The caller's real IP as seen by nginx. nginx overwrites X-Real-IP on every
// proxied request, so it is trustworthy; X-Forwarded-For is client-supplied
// except for the LAST entry, which our proxy appended. Returns null when
// neither parses, so callers decide what an unidentifiable client means.
export function clientIpFromHeaders(headers: Headers): string | null {
  const realIp = headers.get("x-real-ip")?.trim();
  if (realIp && net.isIP(realIp)) return realIp;
  const forwarded = headers.get("x-forwarded-for");
  if (forwarded) {
    const last = forwarded.split(",").at(-1)?.trim();
    if (last && net.isIP(last)) return last;
  }
  return null;
}
