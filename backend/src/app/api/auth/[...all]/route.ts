import { toNextJsHandler } from "better-auth/next-js";
import { NextResponse } from "next/server";
import { clientIpFromHeaders } from "@/lib/client-ip";
import { logRequest } from "@/lib/request-log";
import { auth } from "@/server/auth";
import { checkRateLimit, RATE_LIMITS } from "@/server/rate-limit";

const handler = toNextJsHandler(auth);

// Credentials endpoints get the tight per-IP limit; everything else under
// /api/auth (session reads, sign-out) the loose one.
const CREDENTIALS = /\/(sign-up|sign-in|forget-password|reset-password|change-password)(\/|$)/;

// Two of Better Auth's own endpoints are not offered. send-verification-email
// takes any address and mails it, and the only limit on it here would be the
// loose per-IP one: a way for anybody to spend the email budget on other
// people's inboxes. The app asks for its own link through /v1/me/email/verify,
// which is per account and tight. change-email is off in the auth config and
// would 404 anyway; it is listed so the two read together, and so the app's
// own /v1/me/email is plainly the only way an address changes.
const NOT_OFFERED = /\/(send-verification-email|change-email)(\/|$)/;

async function limited(request: Request): Promise<Response | null> {
  const path = new URL(request.url).pathname;
  if (NOT_OFFERED.test(path)) {
    return NextResponse.json({ error: "not_found", message: "Not found." }, { status: 404 });
  }
  const policy = CREDENTIALS.test(path) ? RATE_LIMITS.authCredentials : RATE_LIMITS.authOther;
  const ip = clientIpFromHeaders(request.headers) ?? "unknown";
  const result = await checkRateLimit(policy, ip);
  if (result.allowed) return null;
  return NextResponse.json(
    { error: "rate_limited", message: "Too many requests." },
    { status: 429, headers: { "Retry-After": String(result.resetSeconds) } },
  );
}

// The same one line per request the /v1 routes write. No user id: Better
// Auth resolves the session inside its own handler, and the sign-in and
// sign-up calls that matter most here have none yet by definition.
async function logged(request: Request, run: () => Promise<Response>): Promise<Response> {
  const startedAt = Date.now();
  const response = await run();
  logRequest(request, response.status, startedAt);
  return response;
}

export async function POST(request: Request) {
  return logged(request, async () => (await limited(request)) ?? handler.POST(request));
}

export async function GET(request: Request) {
  return logged(request, async () => (await limited(request)) ?? handler.GET(request));
}
