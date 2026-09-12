import { Resend } from "resend";
import type { Gender } from "./db/schema";
import { pronounsFor } from "./witness-copy";

export type EmailResult = { ok: true; id: string } | { ok: false; error: string };

/**
 * What a message is for. It travels with the message so that the line a
 * failed send leaves can say which kind failed without quoting the subject:
 * an invitation's subject is the inviter's name.
 */
export type MailKind = "reset" | "verify" | "invite" | "email-changed";

export type Mail = { kind: MailKind; subject: string; text: string };

let client: Resend | null | undefined;

function resend(): Resend | null {
  if (client !== undefined) return client;
  const key = process.env.RESEND_API_KEY;
  client = key ? new Resend(key) : null;
  return client;
}

const FROM = () => process.env.EMAIL_FROM ?? "Asr <noreply@joinasr.com>";
const SITE = () => process.env.PUBLIC_SITE_URL ?? "https://joinasr.com";

/**
 * Hides the person in every address it can find and keeps the domain, which
 * is the half that explains a bounce. Used on the recipient and on whatever
 * the provider said back, because a provider's complaint quotes the address
 * it is complaining about often enough not to rely on it not doing so.
 */
export function maskAddresses(text: string): string {
  return text.replace(
    /[^\s<>@,;"']+@[^\s<>@,;"']+/g,
    (address) => `***@${address.slice(address.indexOf("@") + 1)}`,
  );
}

/**
 * The one line a failed send leaves: JSON, one object per line, shaped like
 * the request log so the same filters find it.
 *
 * Deliberately not in it: the subject, the body, and the token inside the
 * body. A reset link in a log file is the reset -- it is the whole of what
 * the email proves, and anybody who can read the logs could use it. What is
 * in it is what a failure is diagnosed from: which kind of mail, which
 * domain it was going to, and what the provider said.
 */
export function emailFailureLine(kind: MailKind, to: string, error: string): string {
  return JSON.stringify({
    at: new Date().toISOString(),
    event: "email_failed",
    kind,
    to: maskAddresses(to),
    error: maskAddresses(error),
  });
}

/**
 * Sends, and never throws.
 *
 * A failure now leaves a line in the log -- for months it left nothing, and
 * a reset that Resend refused looked exactly like one it accepted: the API
 * answered 200, the app said "check your email", and no record anywhere
 * said otherwise. The `ok: false` is still there for a caller with
 * something better to do than ignore it.
 *
 * What a failure must not become is an error the caller has to handle, and
 * above all not a 500. Password reset answers the same way for an address
 * that has an account and one that does not, on purpose. If a failed send
 * threw, only the addresses with accounts would get the 500 -- and the
 * answer would no longer be the same.
 */
export async function sendEmail(to: string, mail: Mail): Promise<EmailResult> {
  const api = resend();
  if (!api) {
    // No key. In development that is ordinary and the message is printed
    // rather than lost -- it is how a reset link is opened locally. In
    // production it is a misconfiguration, and the body must not go to a
    // log: the link in it is the reset.
    if (process.env.NODE_ENV === "production") {
      console.error(emailFailureLine(mail.kind, to, "email_not_configured"));
    } else {
      console.info(
        `[email] (not configured) to=${to} subject=${JSON.stringify(mail.subject)}\n${mail.text}`,
      );
    }
    return { ok: false, error: "email_not_configured" };
  }
  try {
    const { data, error } = await api.emails.send({
      from: FROM(),
      to,
      subject: mail.subject,
      text: mail.text,
    });
    if (error || !data) {
      const message = error?.message ?? "unknown";
      console.error(emailFailureLine(mail.kind, to, message));
      return { ok: false, error: message };
    }
    return { ok: true, id: data.id };
  } catch (e) {
    // The SDK itself failing -- DNS, a socket, a bad key shape. Same
    // outcome as a refusal: written down, not thrown.
    const message = e instanceof Error ? e.message : String(e);
    console.error(emailFailureLine(mail.kind, to, message));
    return { ok: false, error: message };
  }
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
): Mail {
  const p = pronounsFor(gender);
  const who = relationship ? `${inviterName} (your ${relationship})` : inviterName;
  return {
    kind: "invite",
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

/**
 * The reset code.
 *
 * On its own line and nowhere else in the message, so that the phone's
 * "copy code" offer has one thing to find and the person reading it has one
 * thing to look for. No link: the code is typed into the app that asked for
 * it, which is also what stops a reset from being finished by anybody who
 * merely intercepted the mail in a browser somewhere.
 */
export function resetCodeEmail(code: string): Mail {
  return {
    kind: "reset",
    subject: "Your Asr password reset code",
    text: [
      `${code}`,
      ``,
      `Type this into Asr to choose a new password. It works for ten minutes and three tries.`,
      ``,
      `If you didn't ask for this, ignore it. Nothing has changed, and nobody can use the code without your inbox.`,
    ].join("\n"),
  };
}

export function emailChangedNotice(newEmail: string): Mail {
  return {
    kind: "email-changed",
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

export function verifyEmail(token: string): Mail {
  const url = `${SITE()}/verify/${token}`;
  return {
    kind: "verify",
    subject: "Confirm your email for Asr",
    text: [`Tap to confirm this address:`, ``, url, ``, `If you didn't create an Asr account, ignore this.`].join("\n"),
  };
}
