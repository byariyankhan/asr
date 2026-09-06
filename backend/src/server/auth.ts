import { betterAuth } from "better-auth";
import { bearer } from "better-auth/plugins";
import { cancelPendingDeletion } from "./account";
import { db } from "./db/client";
import { resetPasswordEmail, sendEmail, verifyEmail } from "./email";
import { MIN_PASSWORD } from "@/lib/password";
import { newId } from "@/lib/uuid";

// Mobile-only auth: the Android app signs in through /api/auth/* and gets a
// session token back in the `set-auth-token` header (bearer plugin). Every
// /v1 request carries it as `Authorization: Bearer <token>`. No cookies are
// relied on; nothing is shared with Bookween's auth.
//
// Reset and verification links point at joinasr.io/reset/<token> and
// /verify/<token>. The reset link is an Android App Link, so on a phone with
// the app it opens the app, which calls /api/auth/reset-password with the
// token; anywhere else it is a web page (src/app/reset) that does the same.
// The verification link is always the web page (src/app/verify): opening
// it is the confirmation, and there is nothing for the app to add.
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
    revokeSessionsOnPasswordReset: true,
    minPasswordLength: MIN_PASSWORD,
    resetPasswordTokenExpiresIn: 60 * 60,
    async sendResetPassword({ user, token }) {
      const mail = resetPasswordEmail(token);
      await sendEmail(user.email, mail.subject, mail.text);
    },
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
      const mail = verifyEmail(token);
      await sendEmail(user.email, mail.subject, mail.text);
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
  plugins: [bearer()],
});

export type Session = NonNullable<Awaited<ReturnType<typeof auth.api.getSession>>>;
