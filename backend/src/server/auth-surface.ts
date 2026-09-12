/**
 * How much of Better Auth is offered, and how hard each part is limited.
 *
 * Kept apart from the route that applies it so that it can be read -- by a
 * person or by a test -- without standing up a database. The route is three
 * lines of plumbing; this is the decision.
 */
// Anything that proves or changes a credential gets the tight per-IP limit;
// everything else under /api/auth (session reads, sign-out) the loose one.
// Prefixes, so every shape of sign-in is covered by naming sign-in once.
const CREDENTIALS = [
  "/sign-up",
  "/sign-in",
  "/change-password",
  "/email-otp/request-password-reset",
  "/email-otp/reset-password",
];

// Better Auth's surface is much wider than what this app offers of it, and
// the email-OTP plugin -- added for the reset code -- widened it again. Every
// endpoint left open is a way in, so the ones not used are closed here rather
// than merely left unmentioned:
//
// - send-verification-email and email-otp/send-verification-otp take any
//   address and mail it, on nothing but the loose per-IP limit: a way for
//   anybody to spend the email budget on other people's inboxes. The app asks
//   for its own link through /v1/me/email/verify, which is per account.
// - sign-in/email-otp is passwordless sign-in by emailed code. Asr signs in
//   with a password; a second way in is a second thing to get right.
// - email-otp/check-verification-otp answers whether a code is right without
//   spending it, which is a brute-force oracle with the attempt count left
//   out of it.
// - change-email, email-otp/change-email and email-otp/request-email-change
//   would each change the address behind this app's own /v1/me/email, which
//   is the one door, behind the password.
// - email-otp/verify-email confirms an address by code; the app's links do it.
// - reset-password is the old link flow, and forget-password/email-otp is the
//   plugin's deprecated spelling of the code request. Nothing issues a reset
//   token any more and one door per job is the point, so both are shut.
const NOT_OFFERED = new Set([
  "/send-verification-email",
  "/change-email",
  "/reset-password",
  "/sign-in/email-otp",
  "/forget-password/email-otp",
  "/email-otp/send-verification-otp",
  "/email-otp/check-verification-otp",
  "/email-otp/verify-email",
  "/email-otp/request-email-change",
  "/email-otp/change-email",
]);

/** The endpoint as Better Auth knows it: the path with this route's own prefix off. */
export function endpointOf(pathname: string): string {
  return pathname.replace(/^\/api\/auth/, "");
}

export function offered(endpoint: string): boolean {
  return !NOT_OFFERED.has(endpoint);
}

export function isCredential(endpoint: string): boolean {
  return CREDENTIALS.some((p) => endpoint === p || endpoint.startsWith(`${p}/`));
}
