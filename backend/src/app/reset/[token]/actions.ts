"use server";

import { redirect } from "next/navigation";
import { MIN_PASSWORD } from "@/lib/password";
import { auth } from "@/server/auth";

/**
 * Sets the new password, then sends the browser back to the same page with
 * the answer in the query string.
 *
 * A server action so the form works with no script on the page: the reset
 * link is opened from an email, often in a mail client's own browser, and
 * a page that needs JavaScript to submit is a page that fails silently
 * there. `redirect` throws by design and must stay outside the try, or the
 * catch would swallow the redirect along with the errors.
 */
export async function resetPassword(formData: FormData): Promise<void> {
  const token = String(formData.get("token") ?? "");
  const password = String(formData.get("password") ?? "");
  const again = String(formData.get("again") ?? "");

  let outcome: "done" | "short" | "mismatch" | "invalid";
  if (password.length < MIN_PASSWORD) outcome = "short";
  else if (password !== again) outcome = "mismatch";
  else {
    try {
      await auth.api.resetPassword({ body: { newPassword: password, token } });
      outcome = "done";
    } catch {
      outcome = "invalid";
    }
  }
  redirect(`/reset/${encodeURIComponent(token)}?o=${outcome}`);
}
