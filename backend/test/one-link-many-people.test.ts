import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { newId } from "@/lib/uuid";

const DATABASE_URL = process.env.DATABASE_URL;

/**
 * One link, any number of people — except the ones only one person can be.
 *
 * The link is meant to be forwarded: sent to a group, posted in a family
 * thread, handed to whoever will watch. It was one-shot, because the code
 * lived on the witness row and accepting flipped that row, so the second
 * person to open it was told the invitation had already been answered.
 */
describe.skipIf(!DATABASE_URL)("one link, many witnesses", async () => {
  const { db } = await import("@/server/db/client");
  const { registerDevice } = await import("@/server/devices");
  const { createPact } = await import("@/server/pacts");
  const { createInvite, peekInvite, acceptInvite, declineInvite, listWitnesses } = await import("@/server/witnesses");

  const owner = newId();
  const friends = [newId(), newId(), newId()];
  const mum = newId();
  const impostor = newId();
  // One person is one witness on a challenge, whichever link they open, so
  // each case below needs somebody who has not already said yes.
  const spare = [newId(), newId(), newId()];
  const snapshot = {
    apps: [{ package: "com.instagram.android", label: "Instagram", daily_limit_min: 30 }],
    reset_time: "00:00",
    activities: {},
  };

  beforeAll(async () => {
    const now = new Date();
    const people = [
      { id: owner, name: "Ariyan" },
      { id: friends[0]!, name: "Sabbir" },
      { id: friends[1]!, name: "Tanvir" },
      { id: friends[2]!, name: "Rafi" },
      { id: mum, name: "Rehana" },
      { id: impostor, name: "Nobody" },
      { id: spare[0]!, name: "Imran" },
      { id: spare[1]!, name: "Nadia" },
      { id: spare[2]!, name: "Farhan" },
    ];
    await db
      .insertInto("user")
      .values(people.map((u) => ({ ...u, email: `${u.id}@test.local`, emailVerified: false, createdAt: now, updatedAt: now })))
      .execute();
    const device = await registerDevice(owner, { install_id: "owner-phone", app_version: "1.0.0", fcm_token: "tok" });
    await createPact(owner, { device_id: device.id, duration_days: 14, timezone: "UTC", snapshot });
  });

  afterAll(async () => {
    await db.deleteFrom("user").where("id", "in", [owner, ...friends, ...spare, mum, impostor]).execute();
  });

  it("a friend link stays open for everybody it reaches", async () => {
    const invite = await createInvite(owner, { relationship: "friend" });
    for (const friend of friends) await acceptInvite(friend, invite.invite_code);

    const { my_witnesses } = await listWitnesses(owner);
    const accepted = my_witnesses.filter((w) => w.status === "accepted");
    expect(accepted.map((w) => w.user?.name).sort()).toEqual(["Rafi", "Sabbir", "Tanvir"]);

    // Still open, and still readable by the next person to be sent it.
    expect(await peekInvite(invite.invite_code)).toMatchObject({ relationship: "friend" });
  });

  it("the same person cannot take the same link twice", async () => {
    const invite = await createInvite(owner, { relationship: "colleague" });
    await acceptInvite(spare[0]!, invite.invite_code);
    await expect(acceptInvite(spare[0]!, invite.invite_code)).rejects.toThrow();
    // Nor through a different link on the same challenge: one person is one
    // witness, however many relationships were offered.
    const other = await createInvite(owner, { relationship: "mentor" });
    await expect(acceptInvite(spare[0]!, other.invite_code)).rejects.toThrow();
  });

  it("declining leaves the link open for the rest", async () => {
    const invite = await createInvite(owner, { relationship: "mentor" });
    await declineInvite(impostor, invite.invite_code);
    await acceptInvite(spare[1]!, invite.invite_code);
    const { my_witnesses } = await listWitnesses(owner);
    expect(my_witnesses.some((w) => w.relationship === "mentor" && w.user?.id === spare[1])).toBe(true);
  });

  it("a mother's link closes when a mother accepts", async () => {
    const invite = await createInvite(owner, { relationship: "mother" });
    await acceptInvite(mum, invite.invite_code);

    // Nobody has two mothers, and the code travels through group chats --
    // "whoever opens it first" is exactly how a stranger ends up listed as
    // somebody's mother. The invitation is closed, not merely refusing.
    await expect(acceptInvite(impostor, invite.invite_code)).rejects.toThrow();
    await expect(peekInvite(invite.invite_code)).rejects.toThrow();
  });

  it("tells somebody who already took it, so they are not asked twice", async () => {
    // The link stays open for everybody else it reaches, so somebody who
    // accepted an hour ago and taps it again in the same chat must not be
    // handed the whole "will you be a witness" page with an error under the
    // button. The app reads this and takes them to their circle instead.
    const invite = await createInvite(owner, { relationship: "colleague" });
    expect(await peekInvite(invite.invite_code, spare[2]!)).toMatchObject({ already: false });
    await acceptInvite(spare[2]!, invite.invite_code);
    expect(await peekInvite(invite.invite_code, spare[2]!)).toMatchObject({ already: true });
    // Still an open question for anybody else, and for a stranger with no
    // account at all.
    expect(await peekInvite(invite.invite_code, friends[0]!)).toMatchObject({ already: true });
    expect(await peekInvite(invite.invite_code)).toMatchObject({ already: false });
  });

  it("nobody witnesses themselves, however open the link is", async () => {
    const invite = await createInvite(owner, { relationship: "friend" });
    await expect(acceptInvite(owner, invite.invite_code)).rejects.toThrow(/yourself/);
  });
});
