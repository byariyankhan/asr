import { describe, expect, it } from "vitest";
import { meUpdate, pactAppAdd, pactCreate } from "@/lib/schemas";

/**
 * The Android app's request bodies, exactly as it puts them on the wire,
 * fed to the schemas this server validates with.
 *
 * The strings here are repeated character for character in
 * `android/app/src/test/java/io/joinasr/app/data/WireShapeTest.kt`, which
 * asserts that its serializer produces them. Neither half can see the other
 * language, so the literal is the contract: change the shape on one side
 * and the other side goes red.
 *
 * Written after every POST /v1/pacts this app had ever sent turned out to be
 * a 400. kotlinx leaves a property at its default out of the JSON unless
 * told otherwise; `reset_time` defaults to "00:00" on the phone and is
 * required here, so the server never got a copy of anybody's challenge --
 * silently, because that request is best-effort and nothing watched it fail.
 * Nothing depended on it until witnesses had to be invited to a challenge,
 * and then the invite endpoint refused, correctly and unhelpfully, about a
 * challenge that had been running on the phone for days.
 */
describe("what the Android app sends", () => {
  const PACT_CREATE_WIRE =
    '{"device_id":"8f14e45f-ea9e-4c3b-9d1a-2b6c7d8e9f01","duration_days":14,"timezone":"Asia/Dhaka","snapshot":{"apps":[{"package":"com.instagram.android","label":"Instagram","daily_limit_min":30}],"reset_time":"00:00","activities":{"walk_steps":{"reward_min":15,"daily_cap_min":60,"target":6000},"focus_session":{"reward_min":15,"daily_cap_min":60,"target_min":25}}}}';

  it("a pact it starts is a pact this server accepts", () => {
    const parsed = pactCreate.parse(JSON.parse(PACT_CREATE_WIRE));
    expect(parsed.snapshot.reset_time).toBe("00:00");
    // The rules are the point of sending the snapshot at all: the server
    // reads the target and the reward from here rather than from the
    // request that starts a walk, so the price of earned time cannot be
    // renegotiated mid-challenge.
    expect(parsed.snapshot.activities.walk_steps?.target).toBe(6000);
    expect(parsed.snapshot.activities.focus_session?.target_min).toBe(25);
  });

  it("an app it adds to a running challenge is an app this server accepts", () => {
    // Also verbatim in WireShapeTest.kt. No added_on: that is the server's
    // to stamp, from the pact's own calendar.
    const PACT_APP_ADD_WIRE = '{"package":"com.zhiliaoapp.musically","label":"TikTok","daily_limit_min":20}';
    expect(pactAppAdd.parse(JSON.parse(PACT_APP_ADD_WIRE))).toEqual({
      package: "com.zhiliaoapp.musically",
      label: "TikTok",
      daily_limit_min: 20,
    });
  });

  it("an optional it has nothing for is absent, not null", () => {
    expect(meUpdate.parse(JSON.parse('{"country":"BD"}'))).toEqual({ country: "BD" });
    // Which is why the app must not send one. `first_name` is optional but
    // not nullable -- there is no such thing as clearing your name -- so a
    // null in the body is a rejected request, not a no-op.
    expect(() => meUpdate.parse({ first_name: null })).toThrow();
    // And a body with every field left out has nothing to say at all.
    expect(() => meUpdate.parse({})).toThrow();
  });
});
