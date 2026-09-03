import { imagePath } from "./avatar";
import { db } from "./db/client";
import { subscriptionStateFor } from "./subscriptions";
import { notFound } from "@/lib/http";
import type { MeUpdate } from "@/lib/schemas";

const meColumns = [
  "id",
  "name",
  "email",
  "image",
  "emailVerified",
  "timezone",
  "notify_email",
  "notify_push",
  "date_of_birth",
  "country",
  "gender",
  "createdAt",
] as const;

// The signed-in user's own profile. Subscription status joins this once Play
// Billing lands; witnesses never see date_of_birth, country or gender.
export async function getMe(userId: string) {
  const user = await db
    .selectFrom("user")
    .select(meColumns)
    .where("id", "=", userId)
    .where("deleted_at", "is", null)
    .executeTakeFirst();
  if (!user) throw notFound("User");
  const [{ count }, subscription] = await Promise.all([
    db
      .selectFrom("device")
      .select((eb) => eb.fn.countAll<string>().as("count"))
      .where("user_id", "=", userId)
      .executeTakeFirstOrThrow(),
    subscriptionStateFor(userId),
  ]);
  return {
    id: user.id,
    name: user.name,
    email: user.email,
    image: imagePath(user.image),
    email_verified: user.emailVerified,
    timezone: user.timezone,
    notify_email: user.notify_email,
    notify_push: user.notify_push,
    date_of_birth: user.date_of_birth,
    country: user.country,
    gender: user.gender,
    created_at: user.createdAt,
    device_count: Number(count),
    subscription,
  };
}

export async function updateMe(userId: string, input: MeUpdate) {
  const result = await db
    .updateTable("user")
    .set({ ...input, updatedAt: new Date() })
    .where("id", "=", userId)
    .where("deleted_at", "is", null)
    .executeTakeFirst();
  if (result.numUpdatedRows === 0n) throw notFound("User");
  return getMe(userId);
}
