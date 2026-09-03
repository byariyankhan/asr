import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { newId } from "@/lib/uuid";

// Integration tests against a real Postgres. Skipped unless DATABASE_URL is
// set (docs/DEVELOPMENT.md shows how to run the dev database). They cover
// the rules that make the ledger trustworthy: one active commitment per
// user, idempotent event ingestion, and no writes to a closed commitment.

const DATABASE_URL = process.env.DATABASE_URL;

describe.skipIf(!DATABASE_URL)("commitment ledger", async () => {
  const { db } = await import("@/server/db/client");
  const { registerDevice } = await import("@/server/devices");
  const { createCommitment, getCurrentCommitment, listCommitments } = await import("@/server/commitments");
  const { recordDeviceEvent } = await import("@/server/events");
  const { HttpError } = await import("@/lib/http");

  const userId = newId();
  const witnessId = newId();
  let deviceId = "";

  beforeAll(async () => {
    const now = new Date();
    await db
      .insertInto("user")
      .values([
        { id: userId, name: "Test User", email: `${userId}@test.local`, emailVerified: false, createdAt: now, updatedAt: now },
        { id: witnessId, name: "Witness", email: `${witnessId}@test.local`, emailVerified: false, createdAt: now, updatedAt: now },
      ])
      .execute();
    await db
      .insertInto("witness")
      .values({
        id: newId(),
        user_id: userId,
        witness_user_id: witnessId,
        invite_code: newId().slice(0, 10),
        status: "accepted",
        responded_at: now,
      })
      .execute();
    const device = await registerDevice(userId, { install_id: "install-test-1", app_version: "1.0.0" });
    deviceId = device.id;
  });

  afterAll(async () => {
    await db.deleteFrom("user").where("id", "in", [userId, witnessId]).execute();
    await db.destroy();
  });

  const snapshot = {
    apps: [{ package: "com.instagram.android", label: "Instagram", daily_limit_min: 30 }],
    reset_time: "04:00",
    challenges: {},
  };

  it("registers a device idempotently on (user, install_id)", async () => {
    const again = await registerDevice(userId, { install_id: "install-test-1", app_version: "1.0.1", fcm_token: "tok" });
    expect(again.id).toBe(deviceId);
    expect(again.app_version).toBe("1.0.1");
    expect(again.fcm_token).toBe("tok");
  });

  it("locks one commitment and refuses a second while it is active", async () => {
    const c = await createCommitment(userId, { device_id: deviceId, duration_days: 7, timezone: "Asia/Dhaka", snapshot });
    expect(c.status).toBe("active");
    expect(c.ends_at.getTime() - c.starts_at.getTime()).toBe(7 * 86_400_000);
    expect(c.snapshot.apps[0]?.package).toBe("com.instagram.android");

    await expect(
      createCommitment(userId, { device_id: deviceId, duration_days: 1, timezone: "UTC", snapshot }),
    ).rejects.toMatchObject({ status: 409, code: "commitment_active" });

    expect((await getCurrentCommitment(userId))?.id).toBe(c.id);
  });

  it("ingests an event once, returns the same row on retry, closes the commitment, queues witness rows", async () => {
    const current = (await getCurrentCommitment(userId))!;
    const eventId = newId();
    const body = { id: eventId, type: "broken" as const, reason: "limit_exceeded" as const, app_package: "com.instagram.android", occurred_at: "2026-09-03T14:02:11+06:00" };

    const first = await recordDeviceEvent(userId, current.id, body);
    expect(first.created).toBe(true);
    expect(first.event.type).toBe("broken");

    const retry = await recordDeviceEvent(userId, current.id, body);
    expect(retry.created).toBe(false);
    expect(retry.event.id).toBe(eventId);

    const closed = await db.selectFrom("commitment").select(["status", "ended_at"]).where("id", "=", current.id).executeTakeFirstOrThrow();
    expect(closed.status).toBe("broken");
    expect(closed.ended_at).not.toBeNull();

    const events = await db.selectFrom("commitment_event").select("type").where("commitment_id", "=", current.id).execute();
    expect(events.map((e) => e.type).sort()).toEqual(["broken", "started"]);

    const queued = await db.selectFrom("notification").select(["recipient_id", "kind", "status"]).where("event_id", "=", eventId).execute();
    expect(queued).toEqual([{ recipient_id: witnessId, kind: "commitment_broken", status: "queued" }]);
    expect(await getCurrentCommitment(userId)).toBeUndefined();
  });

  it("refuses new events on a closed commitment but still answers retries", async () => {
    const closed = (await listCommitments(userId, undefined, 10)).items[0]!;
    await expect(
      recordDeviceEvent(userId, closed.id, { id: newId(), type: "limit_hit", app_package: "com.instagram.android", occurred_at: "2026-09-03T15:00:00+06:00" }),
    ).rejects.toMatchObject({ status: 409, code: "commitment_closed" });
  });

  it("refuses an event id that belongs to another commitment", async () => {
    const closed = (await listCommitments(userId, undefined, 10)).items[0]!;
    const usedId = (await db.selectFrom("commitment_event").select("id").where("commitment_id", "=", closed.id).where("type", "=", "broken").executeTakeFirstOrThrow()).id;
    const next = await createCommitment(userId, { device_id: deviceId, duration_days: 1, timezone: "UTC", snapshot });
    await expect(
      recordDeviceEvent(userId, next.id, { id: usedId, type: "completed", occurred_at: "2026-09-03T16:00:00+06:00" }),
    ).rejects.toMatchObject({ status: 409, code: "event_id_reused" });
  });

  it("does not let another user read or write the commitment", async () => {
    const other = newId();
    const current = (await getCurrentCommitment(userId))!;
    await expect(
      recordDeviceEvent(other, current.id, { id: newId(), type: "completed", occurred_at: "2026-09-03T16:00:00+06:00" }),
    ).rejects.toBeInstanceOf(HttpError);
  });

  it("pages history newest first with a cursor", async () => {
    const page1 = await listCommitments(userId, undefined, 1);
    expect(page1.items).toHaveLength(1);
    expect(page1.next_cursor).toBe(page1.items[0]!.id);
    const page2 = await listCommitments(userId, page1.next_cursor!, 1);
    expect(page2.items).toHaveLength(1);
    expect(page2.items[0]!.id).not.toBe(page1.items[0]!.id);
    expect(page2.items[0]!.created_at.getTime()).toBeLessThanOrEqual(page1.items[0]!.created_at.getTime());
  });
});
