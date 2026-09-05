import type { Metadata } from "next";
import { MIN_PASSWORD } from "@/lib/password";
import { button, field, Page } from "../../page-shell";
import { resetPassword } from "./actions";

export const metadata: Metadata = {
  title: "Reset your password · Asr",
  robots: { index: false, follow: false },
};

export const dynamic = "force-dynamic";

/**
 * The page the password-reset email opens when the app does not.
 *
 * On a phone with Asr installed the link is an App Link and never reaches
 * here. Everywhere else -- a laptop, a phone that has not installed the app
 * yet, an App Link that did not verify -- it used to be a 404, which for
 * somebody locked out of their account is the worst page there is. The form
 * posts to a server action, so it works without a script.
 */
export default async function ResetPage({
  params,
  searchParams,
}: {
  params: Promise<{ token: string }>;
  searchParams: Promise<{ o?: string }>;
}) {
  const { token } = await params;
  const { o } = await searchParams;

  if (o === "done") {
    return <Page title="Password changed" lead="You can sign in with the new password now. Every other session has been signed out." />;
  }

  const note =
    o === "short"
      ? `Use at least ${MIN_PASSWORD} characters.`
      : o === "mismatch"
        ? "The two passwords did not match."
        : o === "invalid"
          ? "This link has expired or was already used. Ask for a new one from the app's sign-in screen."
          : null;

  return (
    <Page title="Choose a new password" lead="The link works for one hour, once.">
      <form action={resetPassword}>
        <input type="hidden" name="token" value={token} />
        <input
          style={field}
          type="password"
          name="password"
          placeholder={`At least ${MIN_PASSWORD} characters`}
          autoComplete="new-password"
          minLength={MIN_PASSWORD}
          required
        />
        <input style={field} type="password" name="again" placeholder="Re-enter password" autoComplete="new-password" required />
        {note ? <p style={{ color: "#F2B8B5", fontSize: 14, margin: "0 0 16px" }}>{note}</p> : null}
        <button style={button} type="submit">
          Set password
        </button>
      </form>
    </Page>
  );
}
