import { db } from "./db/client";

/**
 * Writes down the zone the phone holding a challenge is living in.
 *
 * Called from the three requests the phone makes anyway -- registering,
 * the heartbeat, the daily summary -- so a person who lands in another
 * country has their challenge's calendar move with them within the half
 * hour, whether or not they open a limited app. Only the active pact on
 * that device (or the named pact) moves; the zone the challenge was locked
 * in stays where it is, because completion is judged against that one.
 *
 * A no-op when nothing was reported or nothing changed, which is every
 * call but the first and the rare one after a flight.
 */
export async function followPhoneZone(
  where: { userId: string; deviceId?: string; pactId?: string },
  timezone: string | undefined,
  now = new Date(),
): Promise<void> {
  if (!timezone) return;
  let query = db
    .updateTable("pact")
    .set({ phone_timezone: timezone, updated_at: now })
    .where("user_id", "=", where.userId)
    .where("status", "=", "active")
    .where((eb) => eb.or([eb("phone_timezone", "is", null), eb("phone_timezone", "!=", timezone)]));
  if (where.deviceId) query = query.where("device_id", "=", where.deviceId);
  if (where.pactId) query = query.where("id", "=", where.pactId);
  await query.execute();
}
