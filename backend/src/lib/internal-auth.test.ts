import { describe, expect, it } from "vitest";
import { internalCaller, MIN_INTERNAL_SECRET_LENGTH } from "./internal-auth";

const SECRET = "s".repeat(44);
const headers = (init: Record<string, string>) => new Headers(init);

describe("who may run an internal job", () => {
  it("a caller on the box with the secret", () => {
    expect(internalCaller(headers({ "x-internal-secret": SECRET }), SECRET)).toBe("ok");
  });

  it("nobody who came through nginx, secret or not", () => {
    expect(internalCaller(headers({ "x-internal-secret": SECRET, "x-real-ip": "203.0.113.9" }), SECRET)).toBe("proxied");
    expect(internalCaller(headers({ "x-internal-secret": SECRET, "x-forwarded-for": "203.0.113.9" }), SECRET)).toBe("proxied");
    expect(internalCaller(headers({ "x-real-ip": "127.0.0.1" }), SECRET)).toBe("proxied");
  });

  it("nobody with the wrong secret, a partial one, or none", () => {
    expect(internalCaller(headers({}), SECRET)).toBe("unauthorized");
    expect(internalCaller(headers({ "x-internal-secret": "t".repeat(44) }), SECRET)).toBe("unauthorized");
    expect(internalCaller(headers({ "x-internal-secret": SECRET.slice(0, 43) }), SECRET)).toBe("unauthorized");
    expect(internalCaller(headers({ "x-internal-secret": `${SECRET}x` }), SECRET)).toBe("unauthorized");
  });

  it("nobody at all while the secret is unset or too short to be one", () => {
    expect(internalCaller(headers({ "x-internal-secret": "" }), undefined)).toBe("unconfigured");
    expect(internalCaller(headers({ "x-internal-secret": "" }), "")).toBe("unconfigured");
    const short = "s".repeat(MIN_INTERNAL_SECRET_LENGTH - 1);
    expect(internalCaller(headers({ "x-internal-secret": short }), short)).toBe("unconfigured");
  });
});
