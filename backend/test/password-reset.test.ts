import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";
import { newId } from "@/lib/uuid";

const DATABASE_URL = process.env.DATABASE_URL;
process.env.BETTER_AUTH_SECRET ??= "test-secret-test-secret-test-secret-1234";
process.env.BETTER_AUTH_URL ??= "http://localhost:3001";

/**
 * The whole reset, end to end, against a real database.
 *
 * Written because the reset was broken in production for weeks and nothing
 * here noticed: the app posted to an endpoint Better Auth had renamed, the
 * 404 came back, and the screen rendered it as "That is not there." under
 * whatever address had been typed -- which reads as a verdict on the
 * account. Nothing in this repository exercised the reset at all, so there
 * was nothing to go red. There is now.
 */
describe.skipIf(!DATABASE_URL)("resetting a password with a code", async () => {
  const { db } = await import("@/server/db/client");
  const { auth } = await import("@/server/auth");
  const { RESET_CODE_LENGTH } = await import("@/lib/password");

  const email = `${newId()}@test.local`;
  const password = "correct horse battery";
  let userId = "";

  beforeAll(async () => {
    const res = await auth.api.signUpEmail({ body: { email, password, name: "Forgetful" } });
    userId = res.user.id;
  });

  afterAll(async () => {
    await db.deleteFrom("user").where("id", "=", userId).execute();
    await db.destroy();
  });

  /**
   * Asks for a code and reads it out of the mail that was printed.
   *
   * There is no Resend key in tests, so sendEmail prints the message rather
   * than losing it, and that printing is the only way in to a code Better
   * Auth generated and stored as a hash. Which is the point: a code that
   * did not reach the email is not available here either.
   */
  async function askForCode(to = email): Promise<string | null> {
    const info = vi.spyOn(console, "info").mockImplementation(() => {});
    try {
      await auth.api.requestPasswordResetEmailOTP({ body: { email: to } });
      const printed = info.mock.calls.map((call) => String(call[0])).join("\n");
      return printed.match(new RegExp(`\\b\\d{${RESET_CODE_LENGTH}}\\b`))?.[0] ?? null;
    } finally {
      info.mockRestore();
    }
  }

  const rowFor = (address: string) =>
    db
      .selectFrom("verification")
      .select(["value", "expiresAt"])
      .where("identifier", "=", `forget-password-otp-${address}`)
      .orderBy("createdAt", "desc")
      .executeTakeFirst();

  it("mails a code of the length the app draws boxes for", async () => {
    const code = await askForCode();
    expect(code).not.toBeNull();
    expect(code).toHaveLength(RESET_CODE_LENGTH);
  });

  it("keeps the code as a hash, never as itself", async () => {
    const code = await askForCode();
    const row = await rowFor(email);
    expect(row).toBeDefined();
    // Whoever can read this table would otherwise be able to reset any
    // account that had asked -- the same reason the code stays out of the
    // logs. The attempt count is packed on after a colon, so the stored
    // half is what is compared.
    expect(row?.value.split(":")[0]).not.toBe(code);
    expect(row?.value).not.toContain(code as string);
  });

  it("sets the password, and the old one stops working", async () => {
    const code = await askForCode();
    const next = "a different correct horse";
    await auth.api.resetPasswordEmailOTP({ body: { email, otp: code as string, password: next } });

    await expect(auth.api.signInEmail({ body: { email, password: next } })).resolves.toMatchObject({
      user: { id: userId },
    });
    await expect(auth.api.signInEmail({ body: { email, password } })).rejects.toBeTruthy();

    // Put it back, so the tests after this one read as written.
    const again = await askForCode();
    await auth.api.resetPasswordEmailOTP({ body: { email, otp: again as string, password } });
  });

  it("refuses a code that is not the one that was sent", async () => {
    await askForCode();
    const wrong = "0".repeat(RESET_CODE_LENGTH);
    await expect(
      auth.api.resetPasswordEmailOTP({ body: { email, otp: wrong, password: "never applied ok" } }),
    ).rejects.toBeTruthy();
    // And the password it refused to set really is not set.
    await expect(auth.api.signInEmail({ body: { email, password } })).resolves.toBeTruthy();
  });

  it("answers an address with no account the same way, and mails nothing", async () => {
    const stranger = `${newId()}@test.local`;
    const code = await askForCode(stranger);
    expect(code).toBeNull();
    expect(await rowFor(stranger)).toBeUndefined();
  });

  it("gives the code ten minutes", async () => {
    await askForCode();
    const row = await rowFor(email);
    const minutes = ((row?.expiresAt as Date).getTime() - Date.now()) / 60_000;
    expect(minutes).toBeGreaterThan(9);
    expect(minutes).toBeLessThanOrEqual(10);
  });
});
