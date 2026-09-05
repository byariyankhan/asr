import { randomUUID } from "node:crypto";
import { NextResponse } from "next/server";
import { ZodError } from "zod";
import { logRequest } from "./request-log";

// Error envelope shared by every /v1 route (see docs/API.md):
//   { "error": "<code>", "message": "<human text>", ...extra }
export class HttpError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly extra?: Record<string, unknown>,
  ) {
    super(message);
  }
}

export const unauthorized = () => new HttpError(401, "unauthorized", "Sign in required.");
export const forbidden = () => new HttpError(403, "forbidden", "Not yours.");
export const notFound = (what = "Resource") => new HttpError(404, "not_found", `${what} not found.`);
export const conflict = (code: string, message: string) => new HttpError(409, code, message);

export function json<T>(data: T, status = 200): NextResponse {
  return NextResponse.json(data, { status });
}

export function noContent(): Response {
  return new Response(null, { status: 204 });
}

type Ctx<P> = { params: Promise<P> };
type Handler<P> = (request: Request, ctx: Ctx<P>) => Promise<Response>;

// Wraps a route handler so thrown HttpError / ZodError become the documented
// envelope and anything else becomes a logged 500, and writes the one log
// line every request gets (request-log.ts). Route files stay free of
// try/catch and of business logic.
export function route<P = Record<string, never>>(handler: Handler<P>): Handler<P> {
  return async (request, ctx) => {
    const startedAt = Date.now();
    let response: Response;
    try {
      response = await handler(request, ctx);
    } catch (error) {
      response = errorResponse(request, error);
    }
    logRequest(request, response.status, startedAt);
    return response;
  };
}

function errorResponse(request: Request, error: unknown): Response {
  if (error instanceof HttpError) {
    const body = { error: error.code, message: error.message, ...(error.extra ?? {}) };
    const res = NextResponse.json(body, { status: error.status });
    if (error.status === 429 && typeof error.extra?.retryAfter === "number") {
      res.headers.set("Retry-After", String(error.extra.retryAfter));
    }
    return res;
  }
  if (error instanceof ZodError) {
    return NextResponse.json(
      { error: "invalid_body", message: "Request body failed validation.", issues: error.issues },
      { status: 400 },
    );
  }
  if (error instanceof SyntaxError) {
    return NextResponse.json(
      { error: "invalid_body", message: "Request body is not valid JSON." },
      { status: 400 },
    );
  }
  // The class name and a short id, in the message the client shows.
  //
  // "Something went wrong." was all anybody ever saw, on a phone, with
  // the cause left in a log on a host the people fixing it could not
  // reach — and an avatar upload failed that way for days while three
  // different subsystems were guessed at. A constructor name is not
  // data: R2Error, DatabaseError and RedisError name the layer and
  // nothing about the request, the account or the environment. The id
  // is the same one written to the log, for when the log is reachable.
  const trace = randomUUID().slice(0, 8);
  const name = error instanceof Error ? error.name : "unknown";
  console.error(`[${request.method} ${new URL(request.url).pathname}] ${trace}`, error);
  return NextResponse.json(
    {
      error: "internal_error",
      message: `Something went wrong (${name} · ${trace}).`,
      trace,
    },
    { status: 500 },
  );
}

export async function readJson(request: Request): Promise<unknown> {
  const text = await request.text();
  if (!text) return {};
  return JSON.parse(text) as unknown;
}
