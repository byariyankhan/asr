import { toNextJsHandler } from "better-auth/next-js";
import { NextResponse } from "next/server";
import { clientIpFromHeaders } from "@/lib/client-ip";
import { auth } from "@/server/auth";
import { checkRateLimit, RATE_LIMITS } from "@/server/rate-limit";

const handler = toNextJsHandler(auth);

// Credentials endpoints get the tight per-IP limit; everything else under
// /api/auth (session reads, sign-out) the loose one.
const CREDENTIALS = /\/(sign-up|sign-in|forget-password|reset-password|change-password)(\/|$)/;

async function limited(request: Request): Promise<Response | null> {
  const path = new URL(request.url).pathname;
  const policy = CREDENTIALS.test(path) ? RATE_LIMITS.authCredentials : RATE_LIMITS.authOther;
  const ip = clientIpFromHeaders(request.headers) ?? "unknown";
  const result = await checkRateLimit(policy, ip);
  if (result.allowed) return null;
  return NextResponse.json(
    { error: "rate_limited", message: "Too many requests." },
    { status: 429, headers: { "Retry-After": String(result.resetSeconds) } },
  );
}

export async function POST(request: Request) {
  return (await limited(request)) ?? handler.POST(request);
}

export async function GET(request: Request) {
  return (await limited(request)) ?? handler.GET(request);
}
