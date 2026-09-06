import { json, readJson, route } from "@/lib/http";
import { emailChange } from "@/lib/schemas";
import { changeEmail, signInCheck } from "@/server/account";
import { auth } from "@/server/auth";
import { emailChangedNotice, sendEmail } from "@/server/email";
import { RATE_LIMITS } from "@/server/rate-limit";
import { requireCaller } from "@/server/session";

// The address the account signs in and recovers with, changed in one step
// behind a password check (server/account.ts). Nothing is mailed to the new
// address: the person asks for the confirmation link from Email & password
// when they want it. Answers the profile, with the new address unconfirmed.
export const POST = route(async (request) => {
  const caller = await requireCaller(request, RATE_LIMITS.emailChange);
  const input = emailChange.parse(await readJson(request));
  const me = await changeEmail(caller.userId, input.new_email, input.password, signInCheck(auth.api), async (oldEmail, newEmail) => {
    const mail = emailChangedNotice(newEmail);
    await sendEmail(oldEmail, mail.subject, mail.text);
  });
  return json(me);
});
