import { Resend } from "resend";
import type { Gender } from "./db/schema";
import { pronounsFor } from "./witness-copy";

export type EmailResult = { ok: true; id: string } | { ok: false; error: string };

let client: Resend | null | undefined;

function resend(): Resend | null {
  if (client !== undefined) return client;
  const key = process.env.RESEND_API_KEY;
  client = key ? new Resend(key) : null;
  return client;
}

const FROM = () => process.env.EMAIL_FROM ?? "Asr <noreply@joinasr.io>";
const SITE = () => process.env.PUBLIC_SITE_URL ?? "https://joinasr.io";

export async function sendEmail(to: string, subject: string, text: string): Promise<EmailResult> {
  const api = resend();
  if (!api) {
    // Local development without a key: the message is logged, not lost.
    console.info(`[email] (not configured) to=${to} subject=${JSON.stringify(subject)}\n${text}`);
    return { ok: false, error: "email_not_configured" };
  }
  const { data, error } = await api.emails.send({ from: FROM(), to, subject, text });
  if (error || !data) return { ok: false, error: error?.message ?? "unknown" };
  return { ok: true, id: data.id };
}

// --- templates: plain text, short, no tracking ---

/**
 * The invitation, when it goes by email. The pronoun is the inviter's own:
 * the profile holds the gender, and this is about somebody the reader
 * knows personally -- "their phone" about a woman's own son read as a
 * hedge. The second line names the promise rather than the person, so it
 * needs no verb to agree with anybody.
 */
export function inviteEmail(
  inviterName: string,
  relationship: string | null,
  url: string,
  gender?: Gender | null,
) {
  const p = pronounsFor(gender);
  const who = relationship ? `${inviterName} (your ${relationship})` : inviterName;
  return {
    subject: `${inviterName} wants you as a witness`,
    text: [
      `${who} is making a pact to use ${p.their} phone less, and asked you to be a witness.`,
      ``,
      `If the promise is kept, you'll hear. If it is broken, you'll hear that too.`,
      ``,
      `Accept here: ${url}`,
      ``,
      `Asr · Protect your time. Keep your word.`,
    ].join("\n"),
  };
}

export function resetPasswordEmail(token: string) {
  const url = `${SITE()}/reset/${token}`;
  return {
    subject: "Reset your Asr password",
    text: [`Tap to choose a new password:`, ``, url, ``, `The link works for one hour. If you didn't ask for this, ignore it.`].join("\n"),
  };
}

export function emailChangedNotice(newEmail: string) {
  return {
    subject: "Your Asr email address was changed",
    text: [
      `The email address on your Asr account was just changed to ${newEmail}.`,
      ``,
      `If that was you, there is nothing to do. If it was not, sign in and change your password now, or write to hi@ariyankhan.com.`,
      ``,
      `Asr · Protect your time. Keep your word.`,
    ].join("\n"),
  };
}

export function verifyEmail(token: string) {
  const url = `${SITE()}/verify/${token}`;
  return {
    subject: "Confirm your email for Asr",
    text: [`Tap to confirm this address:`, ``, url, ``, `If you didn't create an Asr account, ignore this.`].join("\n"),
  };
}
