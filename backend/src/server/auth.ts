import { betterAuth } from "better-auth";
import { bearer } from "better-auth/plugins";
import { cancelPendingDeletion } from "./account";
import { db } from "./db/client";
import { resetPasswordEmail, sendEmail, verifyEmail } from "./email";
import { newId } from "@/lib/uuid";

// Mobile-only auth: the Android app signs in through /api/auth/* and gets a
// session token back in the `set-auth-token` header (bearer plugin). Every
// /v1 request carries it as `Authorization: Bearer <token>`. No cookies are
// relied on; nothing is shared with Bookween's auth.
//
// Reset and verification links point at joinasr.com/reset/<token> and
// /verify/<token>, which are Android App Links: they open the app, which
// then calls the matching /api/auth endpoint with the token.
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
    minPasswordLength: 8,
    resetPasswordTokenExpiresIn: 60 * 60,
    async sendResetPassword({ user, token }) {
      const mail = resetPasswordEmail(token);
      await sendEmail(user.email, mail.subject, mail.text);
    },
  },
  emailVerification: {
    sendOnSignUp: true,
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
