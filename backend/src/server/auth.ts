import { betterAuth } from "better-auth";
import { bearer } from "better-auth/plugins";
import { db } from "./db/client";
import { newId } from "@/lib/uuid";

// Mobile-only auth: the Android app signs in through /api/auth/* and gets a
// session token back in the `set-auth-token` header (bearer plugin). Every
// /v1 request carries it as `Authorization: Bearer <token>`. No cookies are
// relied on; nothing is shared with Bookween's auth.
export const auth = betterAuth({
  database: { db, type: "postgres" },
  advanced: {
    database: { generateId: () => newId() },
  },
  emailAndPassword: {
    enabled: true,
    // Verification is a post-signup step (email code, wired with Resend in
    // the notification phase), not a sign-in gate: a witness who installed
    // the app from a WhatsApp link should reach the accept screen in one go.
    requireEmailVerification: false,
    revokeSessionsOnPasswordReset: true,
    minPasswordLength: 8,
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
  plugins: [bearer()],
});

export type Session = NonNullable<Awaited<ReturnType<typeof auth.api.getSession>>>;
