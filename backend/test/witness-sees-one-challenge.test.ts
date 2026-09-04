import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { newId } from "@/lib/uuid";

const DATABASE_URL = process.env.DATABASE_URL;

/**
 * A witness agreed to watch a challenge, not to be handed a life.
 *
 * The witness view called the same function as the owner's own Progress tab,
 * so it answered with the last three challenges' events, a lifetime count of
 * how many had been completed and broken, and a longest streak measured
 * across all of them -- to somebody who joined on Tuesday.
 */
describe.skipIf(!DATABASE_URL)("what a witness can see", async () => {
  const { db } = await import("@/server/db/client");
  const { registerDevice } = await import("@/server/devices");
  const { createPact, getPactWithEvents } = await import("@/server/pacts");
  const { recordDeviceEvent } = await import("@/server/events");
  const { createInvite, acceptInvite, requireWitnessView } = await import("@/server/witnesses");
  const { progressFor } = await import("@/server/progress");

  const owner = newId();
  const friend = newId();
  const snapshot = {
    apps: [{ package: "com.instagram.android", label: "Instagram", daily_limit_min: 30 }],
    reset_time: "00:00",
    activities: {},
  };
  let oldPactId = "";
  let watchedPactId = "";
  let witnessRowId = "";

  beforeAll(async () => {
    const now = new Date();
    await db
      .insertInto("user")
      .values(
        [
          { id: owner, name: "Ariyan" },
          { id: friend, name: "Sabbir" },
        ].map((u) => ({ ...u, email: `${u.id}@test.local`, emailVerified: false, createdAt: now, updatedAt: now })),
      )
      .execute();
    const device = (await registerDevice(owner, { install_id: "p", app_version: "1.0.0" })).id;

    // A challenge from before this witness existed, which they lost.
    const old = await createPact(owner, { device_id: device, duration_days: 7, timezone: "UTC", snapshot });
    oldPactId = old.id;
    await recordDeviceEvent(owner, old.id, {
      id: newId(),
      type: "broken",
      reason: "user_gave_up",
      occurred_at: new Date().toISOString(),
    });

    // The one they were invited to.
    const watched = await createPact(owner, { device_id: device, duration_days: 50, timezone: "UTC", snapshot });
    watchedPactId = watched.id;
    const invite = await createInvite(owner, { relationship: "friend" });
    witnessRowId = (await acceptInvite(friend, invite.invite_code)).id;
  });

  afterAll(async () => {
    await db.deleteFrom("user").where("id", "in", [owner, friend]).execute();
  });

  it("sees the challenge they are watching, and no history behind it", async () => {
    const row = await requireWitnessView(friend, witnessRowId);
    const seen = await progressFor(row.user_id, row.pact_id ?? undefined);

    expect(seen.current?.pact_id).toBe(watchedPactId);
    // Every event is from that challenge. The give-up on the previous one is
    // the owner's business.
    expect(seen.recent_events.every((e) => e.pact_id === watchedPactId)).toBe(true);
    expect(seen.recent_events.some((e) => e.type === "broken")).toBe(false);
    // And no scoreboard of everything that came before.
    expect(seen.broken).toBe(0);
    expect(seen.completed).toBe(0);
    expect(seen.longest_streak_days).toBe(0);
  });

  it("the owner still sees their own history", async () => {
    const mine = await progressFor(owner);
    expect(mine.broken).toBe(1);
    expect(mine.recent_events.some((e) => e.pact_id === oldPactId)).toBe(true);
  });

  it("cannot read the challenge they were never invited to", async () => {
    // By id, which is the only way to ask: being a witness to one challenge
    // used to be enough to read every other one the person had.
    await expect(getPactWithEvents(friend, oldPactId)).rejects.toMatchObject({ status: 404 });
    await expect(getPactWithEvents(friend, watchedPactId)).resolves.toMatchObject({ id: watchedPactId });
  });

  it("cannot react to an event from a challenge they never watched", async () => {
    const { react } = await import("@/server/reactions");
    const old = await db
      .selectFrom("pact_event")
      .select("id")
      .where("pact_id", "=", oldPactId)
      .executeTakeFirstOrThrow();
    await expect(react(friend, witnessRowId, old.id, "clap")).rejects.toMatchObject({ status: 404 });

    const mine = await db
      .selectFrom("pact_event")
      .select("id")
      .where("pact_id", "=", watchedPactId)
      .executeTakeFirstOrThrow();
    await expect(react(friend, witnessRowId, mine.id, "clap")).resolves.toMatchObject({ emoji: "clap" });
  });

  it("counts a streak in days kept, not days elapsed", async () => {
    const fresh = await progressFor(owner);
    // Day one, nothing reported: no day has been kept yet, and a challenge
    // that started this morning has not earned a streak of one.
    expect(fresh.streak_days).toBe(0);

    // The challenge has to have been running, or the days below are before
    // it began and rightly count for nothing.
    await db
      .updateTable("pact")
      .set({ starts_at: new Date(Date.now() - 5 * 86_400_000) })
      .where("id", "=", watchedPactId)
      .execute();

    const day = (back: number) => new Date(Date.now() - back * 86_400_000).toISOString().slice(0, 10);
    await db
      .insertInto("daily_summary")
      .values([1, 2].map((back) => ({
        pact_id: watchedPactId,
        day: day(back),
        app_package: "com.instagram.android",
        minutes_used: 12,
        limit_min: 30,
        earned_min: 0,
      })))
      .execute();
    expect((await progressFor(owner)).streak_days).toBe(2);

    // A day over the limit ends it, however many good ones came before.
    await db
      .updateTable("daily_summary")
      .set({ minutes_used: 90 })
      .where("pact_id", "=", watchedPactId)
      .where("day", "=", day(1))
      .execute();
    expect((await progressFor(owner)).streak_days).toBe(0);
  });
});
