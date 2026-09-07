import { db } from "./db/client";
import { registerDevice } from "./devices";
import { sendPush, type PushSender } from "./fcm";
import { movePactToDevice } from "./pacts";
import { followPhoneZone } from "./phone-zone";
import type { DeviceRegister } from "@/lib/schemas";

/**
 * Signing in here signs out everywhere else.
 *
 * One account, one phone. Not a licensing rule dressed up as a feature -- it
 * is the only arrangement that can be honest. A phone can measure its own
 * screen and nothing else's, so two phones enforcing the same thirty minutes
 * is an hour, and two reporting the same day is a witness watching one
 * number overwrite the other all day long. There is no version of "both
 * phones" that adds up.
 *
 * So the newest phone is the phone. The one before it is signed out --
 * really signed out, its sessions deleted -- and the challenge comes across
 * with everything attached to it.
 */
export async function takeOverOnPhone(
  userId: string,
  sessionId: string,
  input: DeviceRegister,
  push: PushSender = sendPush,
) {
  const device = await registerDevice(userId, input);
  const now = new Date();

  // Every other phone this account has. Told first, while its token still
  // works: a phone that finds out by getting a 401 finds out whenever it
  // next has a reason to ask, which for a phone sitting in a pocket
  // enforcing limits is half an hour away.
  const others = await db
    .selectFrom("device")
    .select(["id", "fcm_token"])
    .where("user_id", "=", userId)
    .where("id", "!=", device.id)
    .where("fcm_token", "is not", null)
    .where("fcm_token_invalid", "=", false)
    .execute();

  for (const other of others) {
    await push(other.fcm_token!, {
      title: "Signed in on another phone",
      body: "Asr now runs on your new phone. This one has been signed out.",
      // The app acts on this rather than only showing it: sign out, stop
      // enforcing, and stop pretending to hold a challenge it no longer has.
      data: { kind: "signed_out" },
    });
  }

  // The tokens go whether or not the push landed. Keeping them would send
  // this person's breaches to a handset that is no longer theirs to reach.
  if (others.length > 0) {
    await db
      .updateTable("device")
      .set({ fcm_token: null, updated_at: now })
      .where(
        "id",
        "in",
        others.map((o) => o.id),
      )
      .execute();
  }

  // Every session but the one that just made this request. This is the part
  // that cannot be worked around from the phone: an app that ignores the
  // push still cannot ask the server anything.
  await db.deleteFrom("session").where("userId", "=", userId).where("id", "!=", sessionId).execute();

  // And the challenge follows the person. Nobody is asked to move it: there
  // is nowhere else it could be running.
  const pact = await db
    .selectFrom("pact")
    .select(["id", "device_id"])
    .where("user_id", "=", userId)
    .where("status", "=", "active")
    .executeTakeFirst();
  if (pact && pact.device_id !== device.id) {
    await db.transaction().execute((trx) => movePactToDevice(trx, pact.id, userId, device, now));
  }
  // And the challenge's "today" is this phone's today from here on -- before
  // it asks what the day already holds, which it does next.
  await followPhoneZone({ userId, deviceId: device.id }, input.timezone, now);

  return device;
}
