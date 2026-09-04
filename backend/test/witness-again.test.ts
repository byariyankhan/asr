import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { newId } from "@/lib/uuid";

const DATABASE_URL = process.env.DATABASE_URL;

/**
 * The same person, watching the next challenge too.
 *
 * Witnesses do not carry over -- that is what "a witness belongs to a
 * challenge" means -- so the only way the friend who watched your last one
 * watches your next one is by being invited again. `witness_pair_idx` said
 * otherwise: one accepted row per pair of people, ever, from back when a
 * witness joined two people. The friend pressed Accept and was told "You
 * already witness this person", about a challenge that had finished.
 */
describe.skipIf(!DATABASE_URL)("witnessing the same person twice", async () => {
  const { db } = await import("@/server/db/client");
  const { registerDevice } = await import("@/server/devices");
  const { createPact } = await import("@/server/pacts");
  const { recordDeviceEvent } = await import("@/server/events");
  const { createInvite, acceptInvite, listWitnesses } = await import("@/server/witnesses");

  const owner = newId();
  const friend = newId();
  const snapshot = {
    apps: [{ package: "com.instagram.android", label: "Instagram", daily_limit_min: 30 }],
    reset_time: "00:00",
    activities: {},
  };
  let device = "";

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
    device = (await registerDevice(owner, { install_id: "owner-phone", app_version: "1.0.0", fcm_token: "tok-owner" })).id;
  });

  afterAll(async () => {
    await db.deleteFrom("user").where("id", "in", [owner, friend]).execute();
  });

  it("accepts again on the next challenge", async () => {
    const first = await createPact(owner, { device_id: device, duration_days: 7, timezone: "UTC", snapshot });
    const firstInvite = await createInvite(owner, { relationship: "friend" });
    await acceptInvite(friend, firstInvite.invite_code);

    // The first challenge ends. Whatever happened to it, it is over.
    await recordDeviceEvent(owner, first.id, {
      id: newId(),
      type: "broken",
      reason: "user_gave_up",
      occurred_at: new Date().toISOString(),
    });

    const second = await createPact(owner, { device_id: device, duration_days: 14, timezone: "UTC", snapshot });
    const secondInvite = await createInvite(owner, { relationship: "friend" });
    const accepted = await acceptInvite(friend, secondInvite.invite_code);

    expect(accepted.pact_id).toBe(second.id);

    // And the list is about the challenge that is running, not both.
    const { my_witnesses: mine } = await listWitnesses(owner);
    expect(mine.filter((w) => w.status === "accepted").map((w) => w.id)).toEqual([accepted.id]);
  });

  it("still refuses twice on the same challenge", async () => {
    const extra = await createInvite(owner, { relationship: "mentor" });
    // The pair rule survives, scoped to the challenge: this is the same two
    // people and the same running pact, which is one witness, not two.
    await expect(acceptInvite(friend, extra.invite_code)).rejects.toThrow();
  });
});
