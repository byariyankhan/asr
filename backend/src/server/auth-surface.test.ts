import { describe, expect, it } from "vitest";
import { endpointOf, isCredential, offered } from "./auth-surface";

/**
 * Adding the email-OTP plugin for the reset code brought nine endpoints with
 * it, of which two are wanted. The other seven are the kind of thing that is
 * only ever noticed by whoever finds them first, so what is shut is asserted
 * here by name rather than left to a reading of the regex.
 */
describe("what /api/auth offers", () => {
  it("reads the endpoint out of the path", () => {
    expect(endpointOf("/api/auth/sign-in/email")).toBe("/sign-in/email");
    expect(endpointOf("/api/auth/email-otp/reset-password")).toBe("/email-otp/reset-password");
  });

  it("offers the two halves of the reset, and signing in and up", () => {
    for (const endpoint of [
      "/email-otp/request-password-reset",
      "/email-otp/reset-password",
      "/sign-in/email",
      "/sign-up/email",
      "/change-password",
      "/revoke-other-sessions",
      "/get-session",
      "/sign-out",
    ]) {
      expect(offered(endpoint), endpoint).toBe(true);
    }
  });

  it("shuts every door the plugin opened that nothing here walks through", () => {
    for (const endpoint of [
      "/sign-in/email-otp",
      "/email-otp/send-verification-otp",
      "/email-otp/check-verification-otp",
      "/email-otp/verify-email",
      "/email-otp/request-email-change",
      "/email-otp/change-email",
      "/forget-password/email-otp",
      "/send-verification-email",
      "/change-email",
      "/reset-password",
    ]) {
      expect(offered(endpoint), endpoint).toBe(false);
    }
  });

  it("closing /reset-password does not close /email-otp/reset-password", () => {
    // The two differ by a prefix, and the shut list is matched whole for
    // exactly this reason: a substring rule here would have taken the reset
    // down with the thing it replaced.
    expect(offered("/reset-password")).toBe(false);
    expect(offered("/email-otp/reset-password")).toBe(true);
  });

  it("puts both halves of the reset under the tight limit", () => {
    expect(isCredential("/email-otp/request-password-reset")).toBe(true);
    expect(isCredential("/email-otp/reset-password")).toBe(true);
    // The endpoint that mails the code is the one worth limiting hardest;
    // it was the old name that sat in this list while the real one did not.
    expect(isCredential("/sign-in/email")).toBe(true);
    expect(isCredential("/sign-up/email")).toBe(true);
    expect(isCredential("/change-password")).toBe(true);
  });

  it("leaves session reads on the loose limit", () => {
    expect(isCredential("/get-session")).toBe(false);
    expect(isCredential("/sign-out")).toBe(false);
    expect(isCredential("/revoke-other-sessions")).toBe(false);
  });
});
