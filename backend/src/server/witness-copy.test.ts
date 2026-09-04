import { describe, expect, it } from "vitest";
import {
  RELATIONSHIPS_WITH_COPY,
  relationshipCopy,
  type CopyVars,
  type WitnessEvent,
} from "./witness-copy";

const vars: CopyVars = { userName: "Ariyan", appName: "TikTok", extraMinutes: 10 };
const EVENTS: WitnessEvent[] = ["time_earned", "challenge_abandoned", "challenge_given_up"];
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
    expect(earned("husband")).toContain("Still keeping the commitment");
    expect(earned("wife")).toContain("The commitment still stands");
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

  it("addresses the witness, never talks about them in the third person", () => {
    // `relationship` is what the *witness* is, so "Your brother deleted
    // Asr" sent to the brother is about himself. Four of these were written
    // that way round. Each of them opens the way that relationship is
    // actually spoken to instead.
    const opener: Record<string, string> = {
      mother: "Hey Mom,",
      father: "Hey Dad,",
      brother: "Hey bro,",
      sister: "Hey sis,",
      husband: "Hey love,",
      wife: "Hey love,",
    };
    for (const [relationship, hello] of Object.entries(opener)) {
      for (const event of EVENTS) {
        const copy = relationshipCopy(event, relationship as never, vars);
        expect(copy.body.startsWith(hello)).toBe(true);
        expect(`${copy.title} ${copy.body}`).not.toMatch(/Your (brother|sister|husband|wife|sibling)/);
      }
    }
    // Friend, mentor and colleague address nobody by title and never did.
    for (const relationship of ["friend", "mentor", "colleague"] as const) {
      for (const event of EVENTS) {
        const copy = relationshipCopy(event, relationship, vars);
        expect(copy.body.startsWith("Ariyan")).toBe(true);
      }
    }
  });
});

/**
 * Giving up openly is not deleting the app, and must not be reported as it.
 *
 * The abandoned copy was written about somebody removing Asr mid-challenge:
 * every line of it says "removed", "deleted", "the referee". Somebody who
 * opened the app and pressed Give up did the opposite thing, and got the
 * same message until this event existed.
 */
describe("giving up, in nine voices", () => {
  const vars = { userName: "Ariyan", appName: "Instagram", extraMinutes: 0 };
  const said = (relationship: string) => {
    const copy = relationshipCopy("challenge_given_up", relationship as never, vars);
    return `${copy.title} ${copy.body}`;
  };

  it("never says they removed or deleted anything", () => {
    for (const relationship of RELATIONSHIPS_WITH_COPY) {
      expect(said(relationship)).not.toMatch(/remov|delet|uninstall/i);
    }
  });

  it("says what they actually did, in every voice", () => {
    for (const relationship of RELATIONSHIPS_WITH_COPY) {
      expect(said(relationship)).toMatch(/end(ed)? the challenge|gave up|Give up|quit|called it off|surrender/i);
    }
  });

  it("keeps each relationship's own greeting", () => {
    expect(said("mother")).toContain("Hey Mom,");
    expect(said("father")).toContain("Hey Dad,");
    expect(said("brother")).toContain("Hey bro,");
    expect(said("sister")).toContain("Hey sis,");
    expect(said("husband")).toContain("Hey love,");
    expect(said("wife")).toContain("Hey love,");
    // Friend, mentor and colleague open with the name, as they always have.
    for (const relationship of ["friend", "mentor", "colleague"]) {
      expect(said(relationship)).not.toMatch(/^Hey /);
    }
  });

  it("the roast lands hardest on a brother or sister and softest on a colleague", () => {
    expect(said("brother")).toMatch(/thumb|on purpose/i);
    expect(said("sister")).toMatch(/worse|quit/i);
    // Restrained: a statement of fact and nothing else.
    expect(said("colleague")).not.toMatch(/🏳️|💀|😅/);
    expect(said("mentor")).not.toMatch(/🏳️|💀/);
  });

  it("a relationship with no entry gets the restrained voice, not the abandoned one", () => {
    const legacy = relationshipCopy("challenge_given_up", "parent" as never, vars);
    expect(`${legacy.title} ${legacy.body}`).not.toMatch(/remov|delet/i);
  });
});
