import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";
import { newId } from "@/lib/uuid";

const DATABASE_URL = process.env.DATABASE_URL;
process.env.BETTER_AUTH_SECRET ??= "test-secret-test-secret-test-secret-1234";
process.env.BETTER_AUTH_URL ??= "http://localhost:3001";

/**
 * The address an account signs in and recovers with: stored at sign-up
 * without a confirmation email, confirmed when the person asks, changed in
 * one step behind the password.
 */
describe.skipIf(!DATABASE_URL)("the email address", async () => {
  const { db } = await import("@/server/db/client");
  const { auth } = await import("@/server/auth");
  const { changeEmail, requestEmailVerification, signInCheck } = await import("@/server/account");

  const email = `${newId()}@test.local`;
  const password = "correct horse battery";
  const taken = `${newId()}@test.local`;
  let userId = "";
  let otherId = "";

  beforeAll(async () => {
    userId = (await auth.api.signUpEmail({ body: { email, password, name: "Mover" } })).user.id;
    otherId = (await auth.api.signUpEmail({ body: { email: taken, password, name: "Other" } })).user.id;
  });

  afterAll(async () => {
    await db.deleteFrom("user").where("id", "in", [userId, otherId]).execute();
    await db.destroy();
  });

  it("stores the address at sign-up without mailing it", async () => {
    expect(auth.options.emailVerification?.sendOnSignUp).toBe(false);
    const row = await db.selectFrom("user").select("emailVerified").where("id", "=", userId).executeTakeFirstOrThrow();
    expect(row.emailVerified).toBe(false);
  });

  it("refuses the wrong password, another account's address, and the address it already has", async () => {
    const notify = vi.fn(async () => {});
    await expect(changeEmail(userId, `${newId()}@test.local`, "not it", signInCheck(auth.api), notify)).rejects.toMatchObject({ status: 403, code: "invalid_password" });
    await expect(changeEmail(userId, taken.toUpperCase(), password, signInCheck(auth.api), notify)).rejects.toMatchObject({ status: 409, code: "email_taken" });
    await expect(changeEmail(userId, email, password, signInCheck(auth.api), notify)).rejects.toMatchObject({ status: 400, code: "same_email" });
    expect(notify).not.toHaveBeenCalled();
    const row = await db.selectFrom("user").select("email").where("id", "=", userId).executeTakeFirstOrThrow();
    expect(row.email).toBe(email);
  });

  it("changes it in one step, lowercased and unconfirmed, and signs in with it afterwards", async () => {
    const notify = vi.fn(async () => {});
    const next = `${newId()}@Test.Local`;
    const sessionsBefore = await db.selectFrom("session").select("id").where("userId", "=", userId).execute();

    const me = await changeEmail(userId, ` ${next} `, password, signInCheck(auth.api), notify);
    expect(me.email).toBe(next.toLowerCase());
    expect(me.email_verified).toBe(false);
    // The old address was never confirmed, so there is nobody to tell.
    expect(notify).not.toHaveBeenCalled();
    // The password check signed in and cleaned up after itself.
    const sessionsAfter = await db.selectFrom("session").select("id").where("userId", "=", userId).execute();
    expect(sessionsAfter).toHaveLength(sessionsBefore.length);

    const signedIn = await auth.api.signInEmail({ body: { email: next.toLowerCase(), password } });
    expect(signedIn.user.id).toBe(userId);
    await db.deleteFrom("session").where("token", "=", signedIn.token).execute();
  });

  it("tells the old address once, if it had been confirmed", async () => {
    await db.updateTable("user").set({ emailVerified: true }).where("id", "=", userId).execute();
    const before = (await db.selectFrom("user").select("email").where("id", "=", userId).executeTakeFirstOrThrow()).email;
    const notify = vi.fn(async () => {});
    const next = `${newId()}@test.local`;

    const me = await changeEmail(userId, next, password, signInCheck(auth.api), notify);
    expect(me.email).toBe(next);
    expect(me.email_verified).toBe(false);
    expect(notify).toHaveBeenCalledTimes(1);
    expect(notify).toHaveBeenCalledWith(before, next);
  });

  it("sends the confirmation link only while the address is unconfirmed", async () => {
    const send = vi.fn(async () => {});
    const current = (await db.selectFrom("user").select("email").where("id", "=", userId).executeTakeFirstOrThrow()).email;
    expect(await requestEmailVerification(userId, send)).toEqual({ sent_to: current });
    expect(send).toHaveBeenCalledWith(current);

    await db.updateTable("user").set({ emailVerified: true }).where("id", "=", userId).execute();
    await expect(requestEmailVerification(userId, send)).rejects.toMatchObject({ status: 409, code: "already_verified" });
    expect(send).toHaveBeenCalledTimes(1);
  });
});
