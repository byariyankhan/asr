import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { newId } from "@/lib/uuid";

const DATABASE_URL = process.env.DATABASE_URL;

/**
 * Giving up openly.
 *
 * There has to be a way out of a challenge, or the only way out is to
 * uninstall -- which costs the person their history, costs the product the
 * person, and tells their witnesses the harshest thing this app can say
 * about somebody who was merely tired.
 *
 * It is not a quiet exit, though. It closes the pact as broken and the
 * witnesses hear about it, in the same breath they would have heard about a
 * limit that failed. The one thing it must not do is describe the person who
 * was honest as somebody who ran.
 */
describe.skipIf(!DATABASE_URL)("giving up", async () => {
  const { db } = await import("@/server/db/client");
  const { registerDevice } = await import("@/server/devices");
  const { createPact } = await import("@/server/pacts");
  const { createInvite, acceptInvite } = await import("@/server/witnesses");
  const { recordDeviceEvent } = await import("@/server/events");

  const quitter = newId();
  const mother = newId();
  const mate = newId();
  const snapshot = {
    apps: [{ package: "com.instagram.android", label: "Instagram", daily_limit_min: 30 }],
    reset_time: "00:00",
    activities: {},
  };
  let pactId = "";

  beforeAll(async () => {
    const now = new Date();
    await db
      .insertInto("user")
      .values(
        [
          // The gender the profile actually holds. Sign-up asks for it and
          // the profile is not complete without it, so the messages below
          // have no reason to guess.
          { id: quitter, name: "Ariyan", gender: "male" as const },
          { id: mother, name: "Rehana", gender: null },
          { id: mate, name: "Sabbir", gender: null },
        ].map((u) => ({ ...u, email: `${u.id}@test.local`, emailVerified: false, createdAt: now, updatedAt: now })),
      )
      .execute();
    const device = await registerDevice(quitter, { install_id: "quitter-phone", app_version: "1.0.0", fcm_token: "tok-quitter" });
    const pact = await createPact(quitter, { device_id: device.id, duration_days: 14, timezone: "UTC", snapshot });
    pactId = pact.id;
    const invite = await createInvite(quitter, { relationship: "mother" });
    await acceptInvite(mother, invite.invite_code);
    const mates = await createInvite(quitter, { relationship: "friend" });
    await acceptInvite(mate, mates.invite_code);
  });

  afterAll(async () => {
    await db.deleteFrom("user").where("id", "in", [quitter, mother, mate]).execute();
  });

  it("closes the challenge and tells the witness", async () => {
    const { event } = await recordDeviceEvent(quitter, pactId, {
      id: newId(),
      type: "broken",
      reason: "user_gave_up",
      occurred_at: new Date().toISOString(),
    });
    expect(event.reason).toBe("user_gave_up");

    const pact = await db.selectFrom("pact").select("status").where("id", "=", pactId).executeTakeFirst();
    expect(pact?.status).toBe("broken");

    const queued = await db
      .selectFrom("notification")
      .select(["kind", "title", "body"])
      .where("recipient_id", "=", mother)
      .execute();
    expect(queued).toHaveLength(1);
    expect(queued[0]!.kind).toBe("pact_broken");

    // And the friend's, which is where the pronoun shows: the profile says
    // male, so it is "himself", not a hedge.
    const toMate = await db
      .selectFrom("notification")
      .select(["title", "body"])
      .where("recipient_id", "=", mate)
      .executeTakeFirstOrThrow();
    expect(`${toMate.title} ${toMate.body}`).toContain("himself");

    // The whole point of having a door. Every line of the abandoned copy
    // says the person removed the app; this person opened it and pressed
    // Give up, and their mother must not be told otherwise.
    const said = `${queued[0]!.title} ${queued[0]!.body}`;
    expect(said).not.toMatch(/remov|delet|uninstall/i);
    expect(said).toContain("Ariyan");
    // And in her own voice, not the plain fallback: this is the mother of
    // somebody who stopped honestly.
    expect(said).toContain("Hey Mom,");
  });

  it("refuses a second ending", async () => {
    await expect(
      recordDeviceEvent(quitter, pactId, {
        id: newId(),
        type: "broken",
        reason: "user_gave_up",
        occurred_at: new Date().toISOString(),
      }),
    ).rejects.toThrow();
  });
});
