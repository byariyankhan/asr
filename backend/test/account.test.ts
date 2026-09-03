import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { newId } from "@/lib/uuid";

const DATABASE_URL = process.env.DATABASE_URL;
process.env.BETTER_AUTH_SECRET ??= "test-secret-test-secret-test-secret-1234";
process.env.BETTER_AUTH_URL ??= "http://localhost:3001";

describe.skipIf(!DATABASE_URL)("account deletion and export", async () => {
  const { db } = await import("@/server/db/client");
  const { auth } = await import("@/server/auth");
  const { exportAccount, requestAccountDeletion, signInCheck } = await import("@/server/account");
  const { createInvite, acceptInvite, listWitnesses } = await import("@/server/witnesses");
  const { listInbox, markRead } = await import("@/server/inbox");

  const email = `${newId()}@test.local`;
  const password = "correct horse battery";
  const friend = newId();
  let userId = "";

  beforeAll(async () => {
    const res = await auth.api.signUpEmail({ body: { email, password, name: "Leaver" } });
    userId = res.user.id;
    const now = new Date();
    await db.insertInto("user").values({ id: friend, name: "Friend", email: `${friend}@test.local`, emailVerified: false, createdAt: now, updatedAt: now }).execute();
    const invite = await createInvite(userId, { relationship: "friend" });
    await acceptInvite(friend, invite.invite_code);
  });

  afterAll(async () => {
    await db.deleteFrom("user").where("id", "in", [userId, friend]).execute();
    await db.destroy();
  });

  it("exports the ledger as one document", async () => {
    const data = await exportAccount(userId);
    expect(data.user.email).toBe(email);
    expect(data.witnesses).toHaveLength(1);
    expect(data.pacts).toEqual([]);
  });

  it("refuses deletion with the wrong password", async () => {
    await expect(requestAccountDeletion(userId, "nope nope nope", signInCheck(auth.api))).rejects.toMatchObject({ status: 403, code: "invalid_password" });
    const row = await db.selectFrom("user").select("deleted_at").where("id", "=", userId).executeTakeFirstOrThrow();
    expect(row.deleted_at).toBeNull();
  });

  it("schedules deletion, drops sessions and witness links, tells the witness", async () => {
    const { deletes_at } = await requestAccountDeletion(userId, password, signInCheck(auth.api));
    expect(deletes_at.getTime()).toBeGreaterThan(Date.now() + 6 * 86_400_000);
    const row = await db.selectFrom("user").select("deleted_at").where("id", "=", userId).executeTakeFirstOrThrow();
    expect(row.deleted_at).not.toBeNull();
    expect(await db.selectFrom("session").select("id").where("userId", "=", userId).execute()).toEqual([]);
    expect((await listWitnesses(friend)).i_witness).toEqual([]);

    const inbox = await listInbox(friend, undefined, 10);
    expect(inbox.items.map((n) => n.kind)).toEqual(["witness_removed"]);
    expect(inbox.unread_count).toBe(1);
    await markRead(friend, "all");
    expect((await listInbox(friend, undefined, 10)).unread_count).toBe(0);
  });

  it("signing in again cancels the deletion", async () => {
    await auth.api.signInEmail({ body: { email, password } });
    const row = await db.selectFrom("user").select("deleted_at").where("id", "=", userId).executeTakeFirstOrThrow();
    expect(row.deleted_at).toBeNull();
  });
});
