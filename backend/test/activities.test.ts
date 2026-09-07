import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { newId } from "@/lib/uuid";

const DATABASE_URL = process.env.DATABASE_URL;

describe.skipIf(!DATABASE_URL)("activities and daily summary", async () => {
  const { db } = await import("@/server/db/client");
  const { registerDevice } = await import("@/server/devices");
  const { createPact, getCurrentPact } = await import("@/server/pacts");
  const { createActivity, completeActivity, cancelActivity, listActivities } = await import("@/server/activities");
  const { upsertDailySummary } = await import("@/server/summary");
  const { progressFor } = await import("@/server/progress");
  const { dayInZone } = await import("@/lib/time");

  const userId = newId();
  let pactId = "";

  beforeAll(async () => {
    const now = new Date();
    await db
      .insertInto("user")
      .values({ id: userId, name: "Walker", email: `${userId}@test.local`, emailVerified: false, createdAt: now, updatedAt: now })
      .execute();
    const device = await registerDevice(userId, { install_id: "walker-phone", app_version: "1.0.0" });
    const pact = await createPact(userId, {
      device_id: device.id,
      duration_days: 7,
      timezone: "Asia/Dhaka",
      snapshot: {
        apps: [
          { package: "com.instagram.android", label: "Instagram", daily_limit_min: 30 },
          { package: "com.google.android.youtube", label: "YouTube", daily_limit_min: 45 },
        ],
        reset_time: "04:00",
        activities: {
          walk_steps: { target: 3000, reward_min: 10, daily_cap_min: 20 },
          focus_session: { target_min: 20, reward_min: 10, daily_cap_min: 20 },
        },
      },
    });
    pactId = pact.id;
  });

  afterAll(async () => {
    await db.deleteFrom("user").where("id", "=", userId).execute();
    await db.destroy();
  });

  const iso = (d: Date) => d.toISOString();

  it("takes target and reward from the locked rules, not the request", async () => {
    const start = new Date();
    const { activity, created } = await createActivity(userId, pactId, {
      id: newId(),
      type: "walk_steps",
      started_at: iso(start),
      deadline_at: iso(new Date(start.getTime() + 3_600_000)),
    });
    expect(created).toBe(true);
    expect(activity).toMatchObject({ type: "walk_steps", target: 3000, reward_min: 10, status: "pending" });
  });

  it("refuses activity types the pact did not include", async () => {
    const start = new Date();
    await expect(
      createActivity(userId, pactId, { id: newId(), type: "waiting_period", started_at: iso(start), deadline_at: iso(new Date(start.getTime() + 60_000)) }),
    ).rejects.toMatchObject({ code: "activity_not_allowed" });
  });

  it("enforces the daily cap across pending and completed activities", async () => {
    const start = new Date();
    const second = await createActivity(userId, pactId, {
      id: newId(),
      type: "walk_steps",
      started_at: iso(start),
      deadline_at: iso(new Date(start.getTime() + 3_600_000)),
    });
    expect(second.created).toBe(true);
    await expect(
      createActivity(userId, pactId, { id: newId(), type: "walk_steps", started_at: iso(start), deadline_at: iso(new Date(start.getTime() + 3_600_000)) }),
    ).rejects.toMatchObject({ code: "daily_cap_reached" });

    // Cancelling one frees its share of the cap.
    await cancelActivity(userId, second.activity.id);
    const third = await createActivity(userId, pactId, {
      id: newId(),
      type: "walk_steps",
      started_at: iso(start),
      deadline_at: iso(new Date(start.getTime() + 3_600_000)),
    });
    expect(third.created).toBe(true);
  });

  it("completes idempotently and writes the reward to the ledger", async () => {
    const pending = (await listActivities(userId, pactId)).filter((a) => a.status === "pending");
    expect(pending).toHaveLength(2);
    const target = pending[0]!;
    const eventId = newId();
    const first = await completeActivity(userId, target.id, { event_id: eventId, occurred_at: iso(new Date()) });
    expect(first.created).toBe(true);
    expect(first.activity.status).toBe("completed");
    expect(first.event).toMatchObject({ type: "activity_completed", minutes: 10 });

    const retry = await completeActivity(userId, target.id, { event_id: eventId, occurred_at: iso(new Date()) });
    expect(retry.created).toBe(false);
    expect(retry.event.id).toBe(eventId);

    await expect(completeActivity(userId, target.id, { event_id: newId(), occurred_at: iso(new Date()) })).rejects.toMatchObject({
      code: "activity_closed",
    });
    await expect(cancelActivity(userId, target.id)).rejects.toMatchObject({ code: "activity_closed" });
  });

  it("stores the daily summary and reflects it in progress", async () => {
    const pact = (await getCurrentPact(userId))!;
    const today = dayInZone(new Date(), pact.timezone);
    await upsertDailySummary(userId, pactId, {
      day: today,
      apps: [
        { package: "com.instagram.android", minutes_used: 42, limit_min: 30, earned_min: 10 },
        { package: "com.google.android.youtube", minutes_used: 20, limit_min: 45, earned_min: 0 },
      ],
    });
    let progress = await progressFor(userId);
    expect(progress.current?.apps_within_limits_today).toEqual({ within: 1, total: 2 });

    // Re-sending the day updates in place, as the day goes on.
    await upsertDailySummary(userId, pactId, {
      day: today,
      apps: [{ package: "com.google.android.youtube", minutes_used: 50, limit_min: 45, earned_min: 0 }],
    });
    progress = await progressFor(userId);
    expect(progress.current?.apps_within_limits_today).toEqual({ within: 0, total: 2 });

    // But it never lowers one. Foreground time is not spent backwards, so a
    // smaller figure is a day that lost its memory -- an app uninstalled and
    // installed again wipes its usage events on Android -- or a client
    // asking for a clean slate. Either way the minutes stand.
    await upsertDailySummary(userId, pactId, {
      day: today,
      apps: [
        { package: "com.instagram.android", minutes_used: 0, limit_min: 30, earned_min: 10 },
        { package: "com.google.android.youtube", minutes_used: 3, limit_min: 45, earned_min: 0 },
      ],
    });
    const kept = await db
      .selectFrom("daily_summary")
      .select(["app_package", "minutes_used"])
      .where("pact_id", "=", pactId)
      .where("day", "=", today)
      .orderBy("app_package")
      .execute();
    expect(kept).toEqual([
      { app_package: "com.google.android.youtube", minutes_used: 50 },
      { app_package: "com.instagram.android", minutes_used: 42 },
    ]);
    progress = await progressFor(userId);
    expect(progress.current?.apps_within_limits_today).toEqual({ within: 0, total: 2 });

    await expect(
      upsertDailySummary(userId, pactId, { day: "2020-01-01", apps: [{ package: "com.instagram.android", minutes_used: 1, limit_min: 30, earned_min: 0 }] }),
    ).rejects.toMatchObject({ code: "day_out_of_range" });
    await expect(
      upsertDailySummary(userId, pactId, { day: today, apps: [{ package: "com.reddit.frontpage", minutes_used: 1, limit_min: 30, earned_min: 0 }] }),
    ).rejects.toMatchObject({ code: "app_not_in_pact" });
  });

  it("the cap is per app, across both kinds of activity", async () => {
    // The rule the phone enforces and every screen states: the most bonus
    // time one app can have in a day. A walk and a focus session for
    // Instagram fill its cap of 20; a third activity for Instagram is
    // refused whichever kind it is, and YouTube still has its own.
    const start = new Date();
    const activity = (type: "walk_steps" | "focus_session", app: string) =>
      createActivity(userId, pactId, {
        id: newId(),
        type,
        started_at: iso(start),
        deadline_at: iso(new Date(start.getTime() + 3_600_000)),
        app_package: app,
      });

    expect((await activity("walk_steps", "com.instagram.android")).created).toBe(true);
    expect((await activity("focus_session", "com.instagram.android")).created).toBe(true);
    await expect(activity("walk_steps", "com.instagram.android")).rejects.toMatchObject({ code: "daily_cap_reached" });
    await expect(activity("focus_session", "com.instagram.android")).rejects.toMatchObject({
      code: "daily_cap_reached",
      message: "You have earned all the bonus time Instagram can have today.",
    });
    expect((await activity("walk_steps", "com.google.android.youtube")).created).toBe(true);
  });
});
