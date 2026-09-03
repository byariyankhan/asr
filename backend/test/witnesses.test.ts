import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { newId } from "@/lib/uuid";

const DATABASE_URL = process.env.DATABASE_URL;

describe.skipIf(!DATABASE_URL)("witnesses", async () => {
  const { db } = await import("@/server/db/client");
  const { registerDevice } = await import("@/server/devices");
  const { createPact, getCurrentPact, getPactWithEvents } = await import("@/server/pacts");
  const { recordDeviceEvent } = await import("@/server/events");
  const { createInvite, peekInvite, acceptInvite, declineInvite, listWitnesses, updateWitness, removeWitness, requireWitnessView } =
    await import("@/server/witnesses");
  const { react, unreact, listMyReactions } = await import("@/server/reactions");
  const { progressFor } = await import("@/server/progress");

  const alice = newId(); // makes the pact
  const bob = newId(); // witness
  const carol = newId(); // second witness who declines
  let deviceId = "";
  let inviteCode = "";
  let witnessRowId = "";

  beforeAll(async () => {
    const now = new Date();
    await db
      .insertInto("user")
      .values(
        [
          [alice, "Alice"],
          [bob, "Bob"],
          [carol, "Carol"],
        ].map(([id, name]) => ({ id: id!, name: name!, email: `${id}@test.local`, emailVerified: false, createdAt: now, updatedAt: now })),
      )
      .execute();
    deviceId = (await registerDevice(alice, { install_id: "alice-phone", app_version: "1.0.0" })).id;
  });

  afterAll(async () => {
    await db.deleteFrom("user").where("id", "in", [alice, bob, carol]).execute();
    await db.destroy();
  });

  it("creates an invite with a shareable code", async () => {
    const invite = await createInvite(alice, { relationship: "sibling" });
    expect(invite.invite_code).toMatch(/^[A-HJ-NP-Z2-9]{10}$/);
    expect(invite.url).toBe(`https://joinasr.io/w/${invite.invite_code}`);
    inviteCode = invite.invite_code;
    witnessRowId = invite.id;
  });

  it("lets anyone peek at who is asking, and nothing else", async () => {
    // The name, the photo and the relationship. Nothing about the pact, the
    // apps, or the email the invite went to -- whoever holds this code is a
    // stranger until they accept. toEqual rather than toMatchObject on
    // purpose: this is the boundary where a field added carelessly leaks.
    expect(await peekInvite(inviteCode)).toEqual({
      inviter_name: "Alice",
      inviter_image: null,
      relationship: "sibling",
    });
    await expect(peekInvite("NOPE")).rejects.toMatchObject({ status: 404 });
  });

  it("refuses self-acceptance, accepts once, then reports the invite used", async () => {
    await expect(acceptInvite(alice, inviteCode)).rejects.toMatchObject({ code: "own_invite" });
    const row = await acceptInvite(bob, inviteCode);
    expect(row.status).toBe("accepted");
    expect(row.witness_user_id).toBe(bob);
    await expect(acceptInvite(carol, inviteCode)).rejects.toMatchObject({ code: "invite_used" });
    await expect(peekInvite(inviteCode)).rejects.toMatchObject({ status: 404 });

    const told = await db.selectFrom("notification").select(["recipient_id", "kind"]).where("about_user_id", "=", bob).execute();
    expect(told).toEqual([{ recipient_id: alice, kind: "witness_accepted" }]);
  });

  it("a declined invite is gone", async () => {
    const second = await createInvite(alice, { relationship: "friend", email: "carol@example.com" });
    await declineInvite(carol, second.invite_code);
    await expect(peekInvite(second.invite_code)).rejects.toMatchObject({ status: 404 });
  });

  it("lists both directions with a mutual flag", async () => {
    const forAlice = await listWitnesses(alice);
    expect(forAlice.my_witnesses.map((w) => [w.user?.name, w.status, w.mutual])).toEqual([["Bob", "accepted", false]]);
    expect(forAlice.i_witness).toEqual([]);

    const forBob = await listWitnesses(bob);
    expect(forBob.i_witness.map((w) => [w.user.name, w.relationship, w.mutual])).toEqual([["Alice", "sibling", false]]);

    // Bob invites Alice back: now mutual.
    const back = await createInvite(bob, { relationship: "sibling" });
    await acceptInvite(alice, back.invite_code);
    expect((await listWitnesses(alice)).my_witnesses[0]?.mutual).toBe(true);
    expect((await listWitnesses(bob)).i_witness[0]?.mutual).toBe(true);
  });

  it("each side edits only its own fields", async () => {
    const byWitness = await updateWitness(bob, witnessRowId, { roast_mode: true, notify_start: false });
    expect(byWitness.roast_mode).toBe(true);
    await expect(updateWitness(bob, witnessRowId, { views_progress: false })).rejects.toMatchObject({ status: 403 });
    const byUser = await updateWitness(alice, witnessRowId, { relationship: "friend" });
    expect(byUser.relationship).toBe("friend");
    await expect(updateWitness(alice, witnessRowId, { roast_mode: false })).rejects.toMatchObject({ status: 403 });
    await expect(updateWitness(carol, witnessRowId, { roast_mode: false })).rejects.toMatchObject({ status: 404 });
  });

  it("witness sees progress and pact detail only while allowed", async () => {
    await createPact(alice, {
      device_id: deviceId,
      duration_days: 14,
      timezone: "Asia/Dhaka",
      snapshot: { apps: [{ package: "com.instagram.android", label: "Instagram", daily_limit_min: 30 }], reset_time: "04:00", activities: {} },
    });
    const pact = (await getCurrentPact(alice))!;

    const view = await requireWitnessView(bob, witnessRowId);
    const progress = await progressFor(view.user_id);
    expect(progress.user.name).toBe("Alice");
    expect(progress.current).toMatchObject({ pact_id: pact.id, day: 1, of: 14, apps_within_limits_today: { within: 1, total: 1 } });
    expect(progress.streak_days).toBe(1);

    const detail = await getPactWithEvents(bob, pact.id);
    expect(detail.events.map((e) => e.type)).toEqual(["started"]);
    await expect(getPactWithEvents(carol, pact.id)).rejects.toMatchObject({ status: 404 });

    await updateWitness(alice, witnessRowId, { views_progress: false });
    await expect(requireWitnessView(bob, witnessRowId)).rejects.toMatchObject({ status: 403 });
    await expect(getPactWithEvents(bob, pact.id)).rejects.toMatchObject({ status: 404 });
    await updateWitness(alice, witnessRowId, { views_progress: true });
  });

  it("witness reacts to a breach; the user sees it; roast copy was used", async () => {
    const pact = (await getCurrentPact(alice))!;
    const breach = await recordDeviceEvent(alice, pact.id, {
      id: newId(),
      type: "broken",
      reason: "limit_exceeded",
      app_package: "com.instagram.android",
      occurred_at: new Date().toISOString(),
    });
    const told = await db
      .selectFrom("notification")
      .select(["recipient_id", "kind", "title"])
      .where("event_id", "=", breach.event.id)
      .execute();
    expect(told).toEqual([{ recipient_id: bob, kind: "pact_broken", title: "Alice folded" }]);

    const r1 = await react(bob, witnessRowId, breach.event.id, "tomato");
    expect(r1.emoji).toBe("tomato");
    const r2 = await react(bob, witnessRowId, breach.event.id, "shoe");
    expect(r2.id).toBe(r1.id);
    expect(r2.emoji).toBe("shoe");

    const mine = await listMyReactions(alice, 10);
    expect(mine.map((r) => [r.witness_name, r.emoji, r.event_type])).toEqual([["Bob", "shoe", "broken"]]);

    await expect(react(carol, witnessRowId, breach.event.id, "clap")).rejects.toMatchObject({ status: 404 });
    await unreact(bob, witnessRowId, breach.event.id);
    expect(await listMyReactions(alice, 10)).toEqual([]);

    const after = await progressFor(alice);
    expect(after.current).toBeNull();
    expect(after.broken).toBe(1);
    expect(after.longest_streak_days).toBe(0);
  });

  it("removing ends the relationship for both sides", async () => {
    await removeWitness(bob, witnessRowId);
    expect((await listWitnesses(alice)).my_witnesses).toEqual([]);
    await expect(requireWitnessView(bob, witnessRowId)).rejects.toMatchObject({ status: 404 });
  });
});
