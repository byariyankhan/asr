import { sql } from "kysely";
import { imagePath } from "./avatar";
import { db } from "./db/client";
import { notFound } from "@/lib/http";
import { isUuidLike } from "@/lib/uuid";

const inboxColumns = [
  "notification.id",
  "notification.about_user_id",
  "notification.event_id",
  "notification.kind",
  "notification.title",
  "notification.body",
  "notification.deep_link",
  "notification.status",
  "notification.sent_at",
  "notification.read_at",
  "notification.created_at",
  // Who it is about, so the list shows a face and a name rather than a
  // green tick. Every one of these is a message about a person -- their
  // mother accepted, their friend reacted -- and a row that identifies them
  // only inside a sentence is a row you have to read to know who it is from.
  "u.name as about_name",
  "u.image as about_image",
] as const;

// Notifications addressed to me (as a witness, or about my own account),
// newest first, cursor on (created_at, id) compared inside SQL.
export async function listInbox(userId: string, cursor: string | undefined, limit: number) {
  let query = db
    .selectFrom("notification")
    // Left, because not every notification is about somebody: an account
    // of its own can be told things.
    .leftJoin("user as u", "u.id", "notification.about_user_id")
    .select(inboxColumns)
    .where("recipient_id", "=", userId)
    .where("channel", "=", "push")
    .orderBy("created_at", "desc")
    .orderBy("id", "desc")
    .limit(limit + 1);
  if (cursor) {
    if (!isUuidLike(cursor)) throw notFound("Cursor");
    query = query.where(
      sql<boolean>`(notification.created_at, notification.id) < (select n.created_at, n.id from notification n where n.id = ${cursor} and n.recipient_id = ${userId})`,
    );
  }
  const rows = await query.execute();
  const hasMore = rows.length > limit;
  const page = hasMore ? rows.slice(0, limit) : rows;
  const items = page.map(({ about_name, about_image, ...row }) => ({
    ...row,
    about_user: row.about_user_id
      ? { id: row.about_user_id, name: about_name ?? "", image: imagePath(about_image) }
      : null,
  }));
  const { unread } = await db
    .selectFrom("notification")
    .select((eb) => eb.fn.countAll<string>().as("unread"))
    .where("recipient_id", "=", userId)
    .where("channel", "=", "push")
    .where("read_at", "is", null)
    .executeTakeFirstOrThrow();
  return { items, next_cursor: hasMore ? items.at(-1)!.id : null, unread_count: Number(unread) };
}

export async function markRead(userId: string, ids: string[] | "all"): Promise<void> {
  let query = db
    .updateTable("notification")
    .set({ read_at: new Date() })
    .where("recipient_id", "=", userId)
    .where("read_at", "is", null);
  if (ids !== "all") {
    const valid = ids.filter(isUuidLike);
    if (valid.length === 0) return;
    query = query.where("id", "in", valid);
  }
  await query.execute();
}
