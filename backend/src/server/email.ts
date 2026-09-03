import { Resend } from "resend";

export type EmailResult = { ok: true; id: string } | { ok: false; error: string };

let client: Resend | null | undefined;

function resend(): Resend | null {
  if (client !== undefined) return client;
  const key = process.env.RESEND_API_KEY;
  client = key ? new Resend(key) : null;
  return client;
}

const FROM = () => process.env.EMAIL_FROM ?? "Asr <noreply@joinasr.com>";
const SITE = () => process.env.PUBLIC_SITE_URL ?? "https://joinasr.com";

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

export function inviteEmail(inviterName: string, relationship: string | null, url: string) {
  const who = relationship ? `${inviterName} (your ${relationship})` : inviterName;
  return {
    subject: `${inviterName} wants you as a witness`,
    text: [
      `${who} is making a pact to use their phone less, and asked you to be a witness.`,
      ``,
      `If they keep it, you'll hear. If they break it, you'll hear that too.`,
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

export function verifyEmail(token: string) {
  const url = `${SITE()}/verify/${token}`;
  return {
    subject: "Confirm your email for Asr",
    text: [`Tap to confirm this address:`, ``, url, ``, `If you didn't create an Asr account, ignore this.`].join("\n"),
  };
}
