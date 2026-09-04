import { describe, expect, it } from "vitest";
import {
  RELATIONSHIPS_WITH_COPY,
  relationshipCopy,
  type CopyVars,
  type WitnessEvent,
} from "./witness-copy";

const vars: CopyVars = { userName: "Ariyan", appName: "TikTok", extraMinutes: 10 };
const EVENTS: WitnessEvent[] = ["time_earned", "challenge_abandoned"];
const EXPECTED = [
  "mother",
  "father",
  "brother",
  "sister",
  "husband",
  "wife",
  "friend",
  "mentor",
  "colleague",
] as const;

describe("relationshipCopy", () => {
  it("covers exactly the nine relationships the app offers", () => {
    expect(RELATIONSHIPS_WITH_COPY).toEqual([...EXPECTED]);
  });

  it.each(EXPECTED)("%s has both events, filled in and free of placeholders", (relationship) => {
    for (const event of EVENTS) {
      const copy = relationshipCopy(event, relationship, vars);
      expect(copy.title.length).toBeGreaterThan(0);
      expect(copy.body.length).toBeGreaterThan(0);
      // The failure this guards against is a template shipping with a
      // placeholder in it, which reaches somebody's mother as
      // "{userName} reached the {appName} limit".
      expect(copy.title).not.toMatch(/\{|\}/);
      expect(copy.body).not.toMatch(/\{|\}/);
      expect(copy.body).toContain("Ariyan");
    }
  });

  it.each(EXPECTED)("%s names the app and the minutes when time was earned", (relationship) => {
    const copy = relationshipCopy("time_earned", relationship, vars);
    expect(copy.body).toContain("TikTok");
    expect(copy.body).toContain("10");
  });

  it.each(EXPECTED)("%s says the challenge is over when it was abandoned", (relationship) => {
    const copy = relationshipCopy("challenge_abandoned", relationship, vars);
    // Never the app or the minutes: neither has anything to do with somebody
    // deleting the app, and a stray {appName} would render as a lie.
    expect(copy.body).not.toContain("TikTok");
    expect(copy.body.toLowerCase()).toMatch(/broken|over|abandoned|deleted|removed|ended/);
  });

  it("gives every relationship its own words", () => {
    for (const event of EVENTS) {
      const bodies = EXPECTED.map((r) => relationshipCopy(event, r, vars).body);
      expect(new Set(bodies).size).toBe(EXPECTED.length);
    }
  });

  it("keeps the tone the relationship asks for", () => {
    const earned = (r: (typeof EXPECTED)[number]) => relationshipCopy("time_earned", r, vars).body;
    // Earning time is not a failure, so the warm relationships say so.
    expect(earned("mother")).toContain("still playing by the rules");
    expect(earned("husband")).toContain("still keeping his commitment");
    expect(earned("wife")).toContain("still keeping her commitment");
    expect(earned("mentor")).toContain("commitment is still intact");
    // And the roast ones do not.
    expect(relationshipCopy("time_earned", "brother", vars).title).toContain("💀");
    expect(relationshipCopy("challenge_abandoned", "friend", vars).title).toContain("💀");
    // Colleague is the restrained one, and carries no emoji at all.
    const colleague = relationshipCopy("challenge_abandoned", "colleague", vars);
    expect(colleague.title + colleague.body).not.toMatch(/\p{Extended_Pictographic}/u);
  });

  it("falls back to the restrained voice for a relationship it does not know", () => {
    const colleague = relationshipCopy("time_earned", "colleague", vars);
    // Rows written before the relationship list was split still carry these.
    for (const legacy of ["parent", "sibling", "spouse", "partner", "other"]) {
      expect(relationshipCopy("time_earned", legacy as never, vars)).toEqual(colleague);
    }
    // And a witness invited without one at all.
    expect(relationshipCopy("time_earned", null, vars)).toEqual(colleague);
  });

  it("substitutes every occurrence, not just the first", () => {
    const copy = relationshipCopy("time_earned", "husband", {
      userName: "Jonny",
      appName: "Instagram",
      extraMinutes: 25,
    });
    expect(copy.body).toContain("Jonny");
    expect(copy.body).toContain("Instagram");
    expect(copy.body).toContain("25");
  });
});
