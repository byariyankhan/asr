import { betterAuth } from "better-auth";
import { bearer, emailOTP } from "better-auth/plugins";
import { cancelPendingDeletion } from "./account";
import { db } from "./db/client";
import { resetCodeEmail, sendEmail, verifyEmail } from "./email";
import { MIN_PASSWORD, RESET_CODE_LENGTH } from "@/lib/password";
import { newId } from "@/lib/uuid";

// Mobile-only auth: the Android app signs in through /api/auth/* and gets a
// session token back in the `set-auth-token` header (bearer plugin). Every
// /v1 request carries it as `Authorization: Bearer <token>`. No cookies are
// relied on; nothing is shared with Bookween's auth.
//
// A password reset is a code, not a link. The email carries seven digits,
// they are typed into the app, and the app sends them back with the new
// password in one request -- so the reset is finished on the phone that
// asked for it, by somebody who can read the inbox. A link would have had
// to leave the app, open a browser, and come back.
//
// Verification is still a link (joinasr.com/verify/<token>, src/app/verify):
// opening it is the whole of the confirmation, and there is nothing for the
// app to add.
export const auth = betterAuth({
  database: { db, type: "postgres" },
  advanced: {
    database: { generateId: () => newId() },
  },
  emailAndPassword: {
    enabled: true,
    // Verification is a post-signup step, not a sign-in gate: a witness who
    // installed the app from a WhatsApp link reaches the accept screen in
    // one go and confirms their address afterwards.
    requireEmailVerification: false,
    // Honoured by the code path too: better-auth's own OTP reset deletes
    // every session of the account after it changes the password.
    revokeSessionsOnPasswordReset: true,
    minPasswordLength: MIN_PASSWORD,
  },
  emailVerification: {
    // Not at sign-up. Every new account used to be mailed a confirmation
    // link the moment it was created, and most were never opened -- a paid
    // email per sign-up for a step that is not required to use the app.
    // The address is stored, and confirmed when the person asks for the
    // link from Email & password (POST /v1/me/email/verify), which is the
    // one place a confirmation is sent from and is rate-limited per account.
    sendOnSignUp: false,
    autoSignInAfterVerification: true,
    async sendVerificationEmail({ user, token }) {
      await sendEmail(user.email, verifyEmail(token));
    },
  },
  session: {
    expiresIn: 60 * 60 * 24 * 30, // 30 days
    updateAge: 60 * 60 * 24, // refreshed at most once a day
  },
  user: {
    additionalFields: {
      timezone: { type: "string", required: false, defaultValue: "UTC", input: true },
    },
  },
  databaseHooks: {
    session: {
      create: {
        // Signing in during the 7-day grace window cancels a pending
        // account deletion (docs/API.md, DELETE /me).
        async before(session) {
          await cancelPendingDeletion(session.userId);
        },
      },
    },
  },
  plugins: [
    bearer(),
    // The reset code. Only one of this plugin's types is ever asked for,
    // and the route in front of /api/auth offers only the two endpoints
    // that ask for it -- the plugin also brings passwordless sign-in, a
    // send-to-any-address verification mail and its own change-email, none
    // of which this app uses and all of which would otherwise be open.
    emailOTP({
      otpLength: RESET_CODE_LENGTH,
      // Ten minutes, not the five it defaults to. An email is not an SMS:
      // it can sit behind a spam filter, a sync interval, or somebody
      // walking to their laptop.
      expiresIn: 10 * 60,
      // Three tries per code, which is what turns seven digits from
      // guessable-in-bulk into not worth trying.
      allowedAttempts: 3,
      // Stored as a hash, not as itself. A code sitting in plaintext in the
      // verification table is a password reset for whoever reads that table
      // -- the same reason the token never went into a log line.
      storeOTP: "hashed",
      async sendVerificationOTP({ email, otp, type }) {
        // The plugin can send codes for signing in and for confirming an
        // address as well. Neither is offered, and a code that was never
        // asked for must not be mailed even if some future call asks.
        if (type !== "forget-password") return;
        await sendEmail(email, resetCodeEmail(otp));
      },
    }),
  ],
});

export type Session = NonNullable<Awaited<ReturnType<typeof auth.api.getSession>>>;
