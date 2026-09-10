import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { newId } from "@/lib/uuid";

const DATABASE_URL = process.env.DATABASE_URL;
describe.skipIf(!DATABASE_URL)("the activities added after push-ups", async () => {
  const { db } = await import("@/server/db/client");
  const { registerDevice } = await import("@/server/devices");
  const { createPact } = await import("@/server/pacts");
  const { createActivity, completeActivity } = await import("@/server/activities");

  const userId = newId();
  let pactId = "";

  beforeAll(async () => {
    const now = new Date();
    await db
      .insertInto("user")
      .values({ id: userId, name: "Planker", email: `${userId}@test.local`, emailVerified: false, createdAt: now, updatedAt: now })
      .execute();
    const device = await registerDevice(userId, { install_id: "planker-phone", app_version: "1.0.0" });
    const pact = await createPact(userId, {
      device_id: device.id,
      duration_days: 7,
      timezone: "Asia/Dhaka",
      snapshot: {
        apps: [
          { package: "com.instagram.android", label: "Instagram", daily_limit_min: 30 },
          { package: "com.google.android.youtube", label: "YouTube", daily_limit_min: 45 },
        ],
        reset_time: "00:00",
        activities: {
          walk_steps: { target: 2500, reward_min: 10, daily_cap_min: 30 },
          plank: { target: 45, reward_min: 10, daily_cap_min: 30 },
          wall_sit: { target: 45, reward_min: 10, daily_cap_min: 30 },
          run_steps: { target: 1000, reward_min: 10, daily_cap_min: 30 },
          stairs: { target: 10, reward_min: 10, daily_cap_min: 30 },
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

  it("each takes its target from the locked rule and completes onto the same ledger", async () => {
    const start = new Date();
    const expected: Array<[type: "plank" | "wall_sit" | "run_steps" | "stairs", target: number, app: string | undefined]> = [
      ["plank", 45, "com.instagram.android"],
      ["wall_sit", 45, "com.instagram.android"],
      ["run_steps", 1000, "com.google.android.youtube"],
      ["stairs", 10, undefined],
    ];
    for (const [type, target, app] of expected) {
      const { activity, created } = await createActivity(userId, pactId, {
        id: newId(),
        type,
        started_at: iso(start),
        deadline_at: iso(new Date(start.getTime() + 3_600_000)),
        app_package: app,
      });
      expect(created).toBe(true);
      expect(activity).toMatchObject({ type, target, reward_min: 10, status: "pending" });
      const done = await completeActivity(userId, activity.id, { event_id: newId(), occurred_at: iso(new Date()) });
      expect(done.event).toMatchObject({ type: "activity_completed", minutes: 10 });
    }
  });

  it("a pact from a phone that sent only the old rules gets the new ones, priced like its walk", async () => {
    const other = newId();
    const now = new Date();
    await db
      .insertInto("user")
      .values({ id: other, name: "Walker", email: `${other}@test.local`, emailVerified: false, createdAt: now, updatedAt: now })
      .execute();
    const theirs = await registerDevice(other, { install_id: "old-build", app_version: "0.9.0" });
    const pact = await createPact(other, {
      device_id: theirs.id,
      duration_days: 7,
      timezone: "Asia/Dhaka",
      snapshot: {
        apps: [{ package: "com.instagram.android", label: "Instagram", daily_limit_min: 30 }],
        reset_time: "00:00",
        activities: {
          walk_steps: { target: 2500, reward_min: 15, daily_cap_min: 45 },
          focus_session: { target_min: 20, reward_min: 10, daily_cap_min: 30 },
        },
      },
    });
    expect(pact.snapshot.activities).toMatchObject({
      walk_steps: { target: 2500, reward_min: 15, daily_cap_min: 45 },
      plank: { target: 45, reward_min: 15, daily_cap_min: 45 },
      wall_sit: { target: 45, reward_min: 15, daily_cap_min: 45 },
      run_steps: { target: 1000, reward_min: 15, daily_cap_min: 45 },
      stairs: { target: 10, reward_min: 15, daily_cap_min: 45 },
    });
    const { activity } = await createActivity(other, pact.id, {
      id: newId(),
      type: "plank",
      started_at: iso(now),
      deadline_at: iso(new Date(now.getTime() + 3_600_000)),
    });
    expect(activity).toMatchObject({ type: "plank", target: 45, reward_min: 15 });
    await db.deleteFrom("user").where("id", "=", other).execute();
  });

  it("a pact with no priced rule at all gets none, and refuses them like any other type", async () => {
    const other = newId();
    const now = new Date();
    await db
      .insertInto("user")
      .values({ id: other, name: "Walker", email: `${other}@test.local`, emailVerified: false, createdAt: now, updatedAt: now })
      .execute();
    const theirs = await registerDevice(other, { install_id: "walker-only", app_version: "1.0.0" });
    const pact = await createPact(other, {
      device_id: theirs.id,
      duration_days: 7,
      timezone: "Asia/Dhaka",
      snapshot: {
        apps: [{ package: "com.instagram.android", label: "Instagram", daily_limit_min: 30 }],
        reset_time: "00:00",
        activities: {},
      },
    });
    expect(pact.snapshot.activities).toEqual({});
    await expect(
      createActivity(other, pact.id, {
        id: newId(),
        type: "plank",
        started_at: iso(now),
        deadline_at: iso(new Date(now.getTime() + 3_600_000)),
      }),
    ).rejects.toMatchObject({ code: "activity_not_allowed" });
    await db.deleteFrom("user").where("id", "=", other).execute();
  });
});

