import { sql } from "kysely";
import { db } from "./db/client";
import { notFound } from "@/lib/http";
import { isUuidLike } from "@/lib/uuid";

const inboxColumns = ["id", "about_user_id", "event_id", "kind", "title", "body", "deep_link", "status", "sent_at", "read_at", "created_at"] as const;

// Notifications addressed to me (as a witness, or about my own account),
// newest first, cursor on (created_at, id) compared inside SQL.
export async function listInbox(userId: string, cursor: string | undefined, limit: number) {
  let query = db
    .selectFrom("notification")
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
  const items = hasMore ? rows.slice(0, limit) : rows;
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
