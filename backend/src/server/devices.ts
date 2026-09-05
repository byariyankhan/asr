import { db } from "./db/client";
import { notFound } from "@/lib/http";
import type { DeviceRegister, Heartbeat } from "@/lib/schemas";
import { newId } from "@/lib/uuid";

const deviceColumns = [
  "id",
  "user_id",
  "install_id",
  "model",
  "os_version",
  "app_version",
  "fcm_token",
  "fcm_token_invalid",
  "protection_enabled",
  "last_heartbeat_at",
  "removal_suspected_at",
  "created_at",
  "updated_at",
] as const;

// Register-or-update on (user, install_id). Called on every app start, so it
// must be cheap and idempotent. A fresh FCM token clears the invalid flag:
// the app is evidently back.
export async function registerDevice(userId: string, input: DeviceRegister) {
  const now = new Date();
  return db
    .insertInto("device")
    .values({
      id: newId(),
      user_id: userId,
      install_id: input.install_id,
      model: input.model ?? null,
      os_version: input.os_version ?? null,
      app_version: input.app_version,
      fcm_token: input.fcm_token ?? null,
      last_heartbeat_at: now,
      protection_enabled: false,
    })
    .onConflict((oc) =>
      oc.columns(["user_id", "install_id"]).doUpdateSet((eb) => ({
        model: input.model ?? null,
        os_version: input.os_version ?? null,
        app_version: input.app_version,
        fcm_token: input.fcm_token ?? eb.ref("device.fcm_token"),
        fcm_token_invalid: input.fcm_token ? false : eb.ref("device.fcm_token_invalid"),
        // Registering at all means the app is running here. Whatever
        // Firebase said about this installation, it was wrong or is out of
        // date, and nobody should be accused over it.
        removal_suspected_at: null,
        last_heartbeat_at: now,
        updated_at: now,
      })),
    )
    .returning(deviceColumns)
    .executeTakeFirstOrThrow();
}

export async function requireOwnedDevice(userId: string, deviceId: string) {
  const device = await db
    .selectFrom("device")
    .select(deviceColumns)
    .where("id", "=", deviceId)
    .where("user_id", "=", userId)
    .executeTakeFirst();
  if (!device) throw notFound("Device");
  return device;
}

export async function recordHeartbeat(userId: string, deviceId: string, input: Heartbeat): Promise<void> {
  const now = new Date();
  const result = await db
    .updateTable("device")
    .set((eb) => ({
      protection_enabled: input.protection_enabled,
      app_version: input.app_version,
      fcm_token: input.fcm_token ?? eb.ref("device.fcm_token"),
      fcm_token_invalid: input.fcm_token ? false : eb.ref("device.fcm_token_invalid"),
      last_heartbeat_at: now,
      // Same reasoning: a heartbeat is the app saying it is here.
      removal_suspected_at: null,
      updated_at: now,
    }))
    .where("id", "=", deviceId)
    .where("user_id", "=", userId)
    .executeTakeFirst();
  if (result.numUpdatedRows === 0n) throw notFound("Device");

  // A heartbeat that says protection is on is the only proof there is that a
  // phone which just took a challenge over can actually enforce it: usage
  // access and the overlay grant are both per install, and this field is
  // measured on the phone rather than assumed. So it is what stops the
  // two-hour clock the handover started.
  if (input.protection_enabled) {
    await db
      .updateTable("pact")
      .set({ protection_pending_since: null, updated_at: now })
      .where("user_id", "=", userId)
      .where("device_id", "=", deviceId)
      .where("status", "=", "active")
      .where("protection_pending_since", "is not", null)
      .execute();
  }
}

// Logout from a device: forget its push token so nothing is sent to a phone
// that no longer has a session. The row stays (pacts reference it).
export async function forgetDevice(userId: string, deviceId: string): Promise<void> {
  const result = await db
    .updateTable("device")
    .set({ fcm_token: null, protection_enabled: false, updated_at: new Date() })
    .where("id", "=", deviceId)
    .where("user_id", "=", userId)
    .executeTakeFirst();
  if (result.numUpdatedRows === 0n) throw notFound("Device");
}
