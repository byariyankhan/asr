import { imagePath } from "./avatar";
import { db } from "./db/client";
import { subscriptionStateFor } from "./subscriptions";
import { HttpError, notFound } from "@/lib/http";
import type { MeUpdate } from "@/lib/schemas";

const meColumns = [
  "id",
  "name",
  "first_name",
  "last_name",
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
    first_name: user.first_name,
    last_name: user.last_name,
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

/** The one name everything shows: both parts, or the one there is. */
export function displayName(first: string | null | undefined, last: string | null | undefined): string {
  return [first, last]
    .map((part) => part?.trim() ?? "")
    .filter((part) => part.length > 0)
    .join(" ");
}

export async function updateMe(userId: string, input: MeUpdate) {
  const { first_name, last_name, ...rest } = input;
  const patch: Record<string, unknown> = { ...rest, updatedAt: new Date() };

  // Either half of the name arriving recomposes the display name from both
  // halves, the one sent and the one on file, so `name` can never disagree
  // with the parts it is made of.
  if (first_name !== undefined || last_name !== undefined) {
    const current = await db
      .selectFrom("user")
      .select(["first_name", "last_name"])
      .where("id", "=", userId)
      .where("deleted_at", "is", null)
      .executeTakeFirst();
    if (!current) throw notFound("User");
    const first = first_name ?? current.first_name;
    const last = last_name === undefined ? current.last_name : last_name;
    if (!first) throw new HttpError(400, "first_name_required", "A first name is needed.");
    patch.first_name = first;
    patch.last_name = last && last.length > 0 ? last : null;
    patch.name = displayName(first, last);
  }

  const result = await db
    .updateTable("user")
    .set(patch)
    .where("id", "=", userId)
    .where("deleted_at", "is", null)
    .executeTakeFirst();
  if (result.numUpdatedRows === 0n) throw notFound("User");
  return getMe(userId);
}
