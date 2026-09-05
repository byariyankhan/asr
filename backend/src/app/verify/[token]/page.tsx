import type { Metadata } from "next";
import { auth } from "@/server/auth";
import { Page } from "../../page-shell";

export const metadata: Metadata = {
  title: "Confirm your email · Asr",
  robots: { index: false, follow: false },
};

export const dynamic = "force-dynamic";

/**
 * The page the confirmation email opens.
 *
 * Every sign-up sent `joinasr.io/verify/<token>`, and nothing served it: the
 * first thing every new person received from this product was a link to a
 * 404. The token is Better Auth's own, so the page hands it to Better Auth
 * and says what happened -- there is no session to establish and nothing to
 * show but the answer, which is why this is a server component with no
 * script in it.
 *
 * Opening the link is the confirmation. That is how Better Auth's own link
 * works too, and a mail client that pre-fetches links confirms an address
 * its owner has just been sent mail at, which is the same claim.
 */
export default async function VerifyPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  const outcome = await verify(token);

  if (outcome === "ok") {
    return (
      <Page title="Email confirmed" lead="Thanks. Your address is confirmed; you can close this page and go back to the app." />
    );
  }
  return (
    <Page
      title={outcome === "expired" ? "This link has expired" : "This link does not work"}
      lead={
        outcome === "expired"
          ? "Confirmation links work for an hour. Open the app and ask for a new one from your profile."
          : "It may have been used already, or copied incompletely. Open the app and ask for a new one from your profile."
      }
    />
  );
}

async function verify(token: string): Promise<"ok" | "expired" | "invalid"> {
  if (!/^[A-Za-z0-9_.-]{20,2048}$/.test(token)) return "invalid";
  try {
    await auth.api.verifyEmail({ query: { token } });
    return "ok";
  } catch (error) {
    const code = (error as { body?: { code?: string } })?.body?.code ?? "";
    return code === "TOKEN_EXPIRED" ? "expired" : "invalid";
  }
}
