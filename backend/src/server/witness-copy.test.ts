import { describe, expect, it } from "vitest";
import {
  RELATIONSHIPS_WITH_COPY,
  inviteLead,
  leftAsrCopy,
  relationshipPhrase,
  witnessAcceptedCopy,
  witnessLabel,
  relationshipCopy,
  type CopyVars,
  type WitnessEvent,
} from "./witness-copy";
import { eventForKind, type WitnessKind } from "./notifications";

const vars: CopyVars = { userName: "Ariyan", appName: "TikTok", extraMinutes: 10 };
const EVENTS: WitnessEvent[] = [
  "pact_started",
  "pact_completed",
  "limit_broken",
  "challenge_abandoned",
  "challenge_given_up",
  "pact_moved",
  "protection_off",
  "protection_lost",
  "time_earned",
];
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

  it.each(EXPECTED)("%s has every event, filled in and free of placeholders", (relationship) => {
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

/**
 * Whose challenge this is, said correctly.
 *
 * The profile asks for gender at sign-up and is not complete without it, so
 * there is nothing to guess at here. Half these messages are about somebody's
 * son, husband or sister; a message to a mother that calls her daughter "he"
 * is the whole message ruined.
 */
describe("pronouns", () => {
  const say = (event: WitnessEvent, relationship: string, gender: string | null) => {
    const copy = relationshipCopy(event, relationship as never, {
      userName: "Ariyan",
      gender: gender as never,
      appName: "Instagram",
      extraMinutes: 15,
    });
    return `${copy.title} ${copy.body}`;
  };

  it("a man is he, a woman is she", () => {
    expect(say("time_earned", "mother", "male")).toContain("he is still playing");
    expect(say("time_earned", "mother", "female")).toContain("she is still playing");
    expect(say("challenge_given_up", "friend", "male")).toContain("himself");
    expect(say("challenge_given_up", "friend", "female")).toContain("herself");
  });

  it("anybody who did not say is they, and the verbs follow", () => {
    for (const gender of [null, "other", "prefer_not_to_say"]) {
      expect(say("time_earned", "mother", gender)).toContain("they are still playing");
      expect(say("challenge_given_up", "friend", gender)).toContain("themselves");
    }
  });

  /**
   * Where the name is the subject the verb is singular whatever the pronoun.
   * "Ariyan are using them now" was the first thing this got wrong.
   */
  it("keeps the verb singular after a name", () => {
    for (const gender of [null, "male", "female"]) {
      expect(say("time_earned", "mother", gender)).toContain("Ariyan reached");
      expect(say("time_earned", "mother", gender)).toContain("and is using them now");
    }
  });

  it("leaves no pronoun hardcoded anywhere in the table", () => {
    for (const relationship of RELATIONSHIPS_WITH_COPY) {
      for (const event of EVENTS) {
        // Read as a woman's. Any "he", "his" or "him" left in a template
        // would surface here and nowhere else until somebody's mother read
        // it.
        const asHers = say(event, relationship, "female");
        expect(asHers).not.toMatch(/\b(he|his|him|himself)\b/i);
        const asHis = say(event, relationship, "male");
        expect(asHis).not.toMatch(/\b(she|herself)\b/i);
      }
    }
  });

  it("no placeholder survives into a message", () => {
    for (const relationship of RELATIONSHIPS_WITH_COPY) {
      for (const event of EVENTS) {
        for (const gender of [null, "male", "female"]) {
          expect(say(event, relationship, gender)).not.toMatch(/\{[A-Za-z]+\}/);
        }
      }
    }
  });
});

/**
 * Who a witness is to the person reading, for the line under their name.
 *
 * A reaction notification said the name in the title and again underneath
 * it, with the same emoji twice. The second line is for the one thing the
 * title cannot carry: who this person is to you.
 */
describe("witnessLabel", () => {
  it("a parent is Mom or Dad, because nobody says 'your mother'", () => {
    expect(witnessLabel("mother")).toBe("Mom");
    expect(witnessLabel("father")).toBe("Dad");
  });

  it("everybody else takes 'Your', because 'Brother saw that' is not English", () => {
    expect(witnessLabel("brother")).toBe("Your brother");
    expect(witnessLabel("friend")).toBe("Your friend");
    expect(witnessLabel("colleague")).toBe("Your colleague");
  });

  it("covers every relationship the copy table knows", () => {
    for (const relationship of RELATIONSHIPS_WITH_COPY) {
      expect(witnessLabel(relationship)).not.toBe("Your witness");
    }
  });

  it("a relationship from an older build still reads as something", () => {
    expect(witnessLabel("sibling")).toBe("Your brother or sister");
    expect(witnessLabel("other")).toBe("Your witness");
    expect(witnessLabel(null)).toBe("Your witness");
    expect(witnessLabel("nonsense")).toBe("Your witness");
  });
});

/**
 * The invitation page's one sentence, which said "asked you, as their
 * brother, to be his witness" on a real invitation. The relationship half
 * came from a map with "their" written into it and the rest from the
 * pronoun table. These read the whole sentence, so a hedge cannot creep back
 * into one half of it.
 */
describe("inviteLead", () => {
  it("says his throughout for a man, her for a woman, their for anybody else", () => {
    expect(inviteLead({ relationship: "brother", gender: "male", days: 14 })).toBe(
      "asked you, as his brother, to be his witness for a 14-day challenge.",
    );
    expect(inviteLead({ relationship: "brother", gender: "female", days: 14 })).toBe(
      "asked you, as her brother, to be her witness for a 14-day challenge.",
    );
    expect(inviteLead({ relationship: "brother", gender: null, days: 14 })).toBe(
      "asked you, as their brother, to be their witness for a 14-day challenge.",
    );
  });

  it("never mixes one person's pronouns, whatever the relationship", () => {
    const relationships: (string | null)[] = [
      ...RELATIONSHIPS_WITH_COPY, "parent", "sibling", "spouse", "other", "nonsense", null,
    ];
    for (const relationship of relationships) {
      expect(inviteLead({ relationship, gender: "male", days: 7 })).not.toMatch(
        /\b(their|them|they|her|she)\b/,
      );
      expect(inviteLead({ relationship, gender: "female", days: 7 })).not.toMatch(
        /\b(their|them|they|his|him|he)\b/,
      );
      for (const gender of [null, "other", "prefer_not_to_say"] as const) {
        expect(inviteLead({ relationship, gender, days: 7 })).not.toMatch(/\b(his|him|he|her|she)\b/);
      }
    }
  });

  it("a relationship it cannot name is someone close to him, never 'their other'", () => {
    expect(relationshipPhrase("other", "male")).toBe("someone close to him");
    expect(relationshipPhrase(null, "female")).toBe("someone close to her");
    expect(relationshipPhrase("nonsense", null)).toBe("someone close to them");
    expect(relationshipPhrase("sibling", "female")).toBe("her brother or sister");
  });

  it("leaves the duration out when there is none", () => {
    expect(inviteLead({ relationship: "friend", gender: "male", days: null })).toBe(
      "asked you, as his friend, to be his witness.",
    );
  });
});

/**
 * Every kind a witness can be told about has a voice, so nothing falls back
 * to a sentence that does not know who it is talking about. The plain copy
 * this replaced said "{name} kept their word" to a mother about her son.
 */
describe("every witness kind speaks in the relationship's voice", () => {
  const KINDS: WitnessKind[] = [
    "pact_started", "pact_completed", "pact_broken", "protection_lost",
    "uninstalled", "pact_moved", "protection_off", "time_earned",
  ];

  it("maps each kind to an event the table has", () => {
    for (const kind of KINDS) {
      const event = eventForKind(kind, null);
      expect(EVENTS).toContain(event);
    }
  });

  it("tells a broken pact apart by how it broke", () => {
    expect(eventForKind("pact_broken", "limit_exceeded")).toBe("limit_broken");
    expect(eventForKind("pact_broken", null)).toBe("limit_broken");
    expect(eventForKind("pact_broken", "user_gave_up")).toBe("challenge_given_up");
    expect(eventForKind("pact_broken", "app_removed")).toBe("challenge_abandoned");
    expect(eventForKind("pact_broken", "protection_disabled")).toBe("challenge_abandoned");
    expect(eventForKind("uninstalled", null)).toBe("challenge_abandoned");
  });

  it("says he about a man and she about a woman, in every voice, for every kind", () => {
    for (const relationship of RELATIONSHIPS_WITH_COPY) {
      for (const event of EVENTS) {
        // "them" is left out of the forbidden list on purpose: "is using
        // them now" is about the minutes, not the person.
        const his = relationshipCopy(event, relationship, { ...vars, gender: "male" });
        expect(`${his.title} ${his.body}`).not.toMatch(/\b(she|her|hers|herself|they|their|themselves)\b/);
        const hers = relationshipCopy(event, relationship, { ...vars, gender: "female" });
        expect(`${hers.title} ${hers.body}`).not.toMatch(/\b(he|him|his|himself|they|their|themselves)\b/);
      }
    }
  });

  it("a kept pact is told as good news to everyone, a silent phone as a question", () => {
    for (const relationship of RELATIONSHIPS_WITH_COPY) {
      const kept = relationshipCopy("pact_completed", relationship, vars);
      expect(`${kept.title} ${kept.body}`).toMatch(/kept|finished|completed|did it|pulled it off|saw it through/i);
      const quiet = relationshipCopy("protection_lost", relationship, vars);
      expect(quiet.body).toMatch(/a day|whole day/i);
      expect(quiet.body).toContain("Ariyan");
    }
  });
});

/**
 * The two things the person being witnessed is told about a witness. Both
 * name the witness the way that person is spoken of, and both use the
 * witness's own pronoun -- the message in the founder's screenshot said
 * "They'll know" about his brother.
 */
describe("what the owner is told about a witness", () => {
  it("names the relationship and uses the witness's pronoun", () => {
    expect(witnessAcceptedCopy("Ariyan Khan", "brother", "male")).toEqual({
      title: "Ariyan Khan is your witness",
      body: "Your brother accepted. He’ll know if your pact breaks.",
    });
    expect(witnessAcceptedCopy("Amina", "mother", "female").body).toBe(
      "Mom accepted. She’ll know if your pact breaks.",
    );
    expect(witnessAcceptedCopy("Sam", "friend", null).body).toBe(
      "Your friend accepted. They’ll know if your pact breaks.",
    );
    expect(witnessAcceptedCopy("Sam", null, "prefer_not_to_say").body).toBe(
      "Your witness accepted. They’ll know if your pact breaks.",
    );
  });

  it("an account that leaves is spoken of in its own pronoun", () => {
    expect(leftAsrCopy("Ariyan", "male").body).toBe("Ariyan deleted his account, so you are no longer connected.");
    expect(leftAsrCopy("Amina", "female").body).toBe("Amina deleted her account, so you are no longer connected.");
    expect(leftAsrCopy("Sam", null).body).toBe("Sam deleted their account, so you are no longer connected.");
  });
});
