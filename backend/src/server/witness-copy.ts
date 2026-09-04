import type { Gender, Relationship } from "./db/schema";

/**
 * What a witness is told, in the words of the relationship they hold.
 *
 * The same event reads differently depending on who is reading it. A mother
 * hearing that her son bought himself ten more minutes wants reassurance
 * that he is still inside the rules; a brother wants to be handed
 * ammunition. Sending both of them the same sentence makes the message
 * ignorable, and a witness who ignores the message is not a witness.
 *
 * One table, one function, and nothing about relationships anywhere else.
 * Every string is here so that changing the voice of this product is a
 * change to one file — and, because these are composed on the server and
 * pushed as finished title/body, a change that reaches phones without an
 * app update.
 *
 * Two events, deliberately:
 *
 * - `time_earned` is not a failure. The minutes were earned by doing the
 *   thing the pact asked for, so the tone stays playful or warm. A witness
 *   who is alarmed by a legitimate reward will stop reading the alarming
 *   ones.
 * - `challenge_abandoned` is somebody getting out from under the pact: Asr
 *   removed, or protection switched off. This is the one that is allowed to
 *   bite, and every line of it says so — "removed Asr", "deleted the
 *   referee".
 * - `challenge_given_up` is the opposite act with the same result. They
 *   opened the app and pressed Give up. Reporting that as an uninstall
 *   would be false about the one person who was honest, so it has its own
 *   nine: the same voices, but roasting the surrender rather than the
 *   escape. Every one of them says, somewhere, that nothing was deleted --
 *   because that is the whole difference and the reason the door exists.
 *
 * Every message here is addressed to the witness, about the person being
 * witnessed — because `relationship` is what the *witness* is: "Invited you
 * as · Brother" means the reader is the brother. Four of these were written
 * the other way round ("Your brother deleted Asr", sent to the brother), so
 * they open the way that relationship is actually spoken to: Hey Mom, Hey
 * Dad, Hey bro, Hey sis, Hey love.
 *
 * Everything else a witness hears keeps the older, plainer copy in
 * notifications.ts: those events were not part of this spec, and inventing
 * nine voices for them would be guessing at a tone nobody asked for.
 */
export type WitnessEvent = "time_earned" | "challenge_abandoned" | "challenge_given_up";

export type CopyVars = {
  /** The person being witnessed. */
  userName: string;
  /**
   * Theirs, from the profile. Sign-up asks for it and the profile is not
   * complete without it, so this is a thing the product knows rather than a
   * thing it guesses -- and half these messages are about a son, a husband
   * or a sister, where getting it wrong is the whole message ruined.
   *
   * Null, "other" and "prefer_not_to_say" all read as they/them, which is
   * the right answer to a question somebody declined to answer.
   */
  gender?: Gender | null;
  /** The app the limit was reached on, by its label: "TikTok", not a
   *  package name. Only `time_earned` uses it. */
  appName: string;
  /** Minutes the activity awarded. Only `time_earned` uses it. */
  extraMinutes: number;
};

export type Copy = { title: string; body: string };

type Template = { title: string; body: string };

/**
 * The words that change with who is being talked about.
 *
 * Verbs as well as pronouns, because "they is still playing by the rules" is
 * not English. `{is}`, `{was}`, `{has}` and `{does}` exist for that, and
 * belong only where the *pronoun* is the subject. Where `{userName}` is the
 * subject the verb is singular whatever the pronoun -- "Ariyan is using them
 * now", never "are" -- so those stay written out.
 *
 * `Their` and `They` are the capitalised forms, for the start of a sentence.
 */
type Pronouns = {
  they: string;
  them: string;
  their: string;
  themself: string;
  is: string;
  was: string;
  has: string;
  does: string;
};

const HE: Pronouns = {
  they: "he", them: "him", their: "his", themself: "himself",
  is: "is", was: "was", has: "has", does: "does",
};
const SHE: Pronouns = {
  they: "she", them: "her", their: "her", themself: "herself",
  is: "is", was: "was", has: "has", does: "does",
};
const THEY: Pronouns = {
  they: "they", them: "them", their: "their", themself: "themselves",
  is: "are", was: "were", has: "have", does: "do",
};

/**
 * Singular "they" for anybody who did not say, which includes "other" and
 * "prefer_not_to_say". Somebody who declined to answer has not thereby
 * become a "he".
 */
export function pronounsFor(gender?: Gender | null): Pronouns {
  if (gender === "male") return HE;
  if (gender === "female") return SHE;
  return THEY;
}

const KEYS = [
  "userName", "appName", "extraMinutes",
  "they", "them", "their", "themself",
  "They", "Them", "Their",
  "is", "was", "has", "does",
] as const;

const PATTERN = new RegExp(`\\{(${KEYS.join("|")})\\}`, "g");

/**
 * The placeholders above, and nothing else.
 *
 * Unknown placeholders are left alone rather than replaced with "undefined":
 * a typo in a template should look like a typo, not like a bug in whoever's
 * account it landed in.
 */
function fill(template: string, vars: CopyVars): string {
  const p = pronounsFor(vars.gender);
  const capital = (word: string) => word.charAt(0).toUpperCase() + word.slice(1);
  return template.replace(PATTERN, (_, key: string) => {
    switch (key) {
      case "userName": return vars.userName;
      case "appName": return vars.appName;
      case "extraMinutes": return String(vars.extraMinutes);
      case "They": return capital(p.they);
      case "Them": return capital(p.them);
      case "Their": return capital(p.their);
      default: return p[key as keyof Pronouns];
    }
  });
}

const TABLE: Record<string, Record<WitnessEvent, Template>> = {
  mother: {
    time_earned: {
      title: "{They} earned a little more time 👀",
      body:
        "Hey Mom, {userName} reached the {appName} limit, earned {extraMinutes} extra minutes, " +
        "and is using them now. Don’t worry, {they} {is} still playing by the rules.",
    },
    challenge_abandoned: {
      title: "The challenge ended early",
      body:
        "Hey Mom, {userName} removed Asr before finishing the challenge. The pact is now broken. " +
        "You may want to have a little talk with {them}. 😅",
    },
    challenge_given_up: {
      title: "{userName} ended the challenge",
      body:
        "Hey Mom, {userName} stopped the challenge today — opened Asr and ended it rather " +
        "than quietly walking away from it. That is the honest way to stop. A word from " +
        "you might be what gets the next one finished.",
    },
  },
  father: {
    time_earned: {
      title: "{They} earned {their} way back in.",
      body:
        "Hey Dad, {userName} hit the {appName} limit, earned {extraMinutes} more minutes, and is " +
        "using them now. Still within the rules.",
    },
    challenge_abandoned: {
      title: "The pact didn’t make it.",
      body:
        "Hey Dad, {userName} removed Asr before completing the challenge. The pact has officially " +
        "been broken. Looks like this one needs a conversation.",
    },
    challenge_given_up: {
      title: "{userName} called it off.",
      body:
        "Hey Dad, {userName} ended the challenge today rather than letting it quietly fall " +
        "apart. Owning it counts for something. Worth a conversation before the next " +
        "one starts.",
    },
  },
  brother: {
    time_earned: {
      title: "{userName} worked for the scroll. 💀",
      body:
        "Hey bro, {userName} ran out of {appName} time, earned {extraMinutes} more minutes, and " +
        "went straight back in. Technically legal. Unfortunately.",
    },
    challenge_abandoned: {
      title: "{userName} found the emergency exit. 🏳️",
      body:
        "Hey bro, {userName} couldn’t change the rules, so {they} deleted Asr instead. The challenge " +
        "is officially over. You know what to do. 💀",
    },
    challenge_given_up: {
      title: "Tapped out. Voluntarily. 🏳️",
      body:
        "Hey bro, {userName} opened the app and pressed Give up with {their} own thumb. " +
        "Nothing broke and nothing went missing — that was a choice, made on purpose, " +
        "in writing.",
    },
  },
  sister: {
    time_earned: {
      title: "{userName} is back at it 👀",
      body:
        "Hey sis, {userName} hit the {appName} limit, earned {extraMinutes} extra minutes, and " +
        "immediately went back in. At least the scrolling had to be earned.",
    },
    challenge_abandoned: {
      title: "Well… {they} actually deleted it. 💀",
      body:
        "Hey sis, {userName} removed Asr before finishing the challenge. The pact is broken. This " +
        "information is now yours to use responsibly. Or not.",
    },
    challenge_given_up: {
      title: "{userName} surrendered on purpose 🏳️",
      body:
        "Hey sis, {userName} didn’t get caught and didn’t sneak off. {They} walked in and " +
        "quit, on purpose. Somehow that is worse. Over to you.",
    },
  },
  husband: {
    time_earned: {
      title: "Extra time, fairly earned ❤️",
      body:
        "Hey love, {userName} reached the {appName} limit, earned {extraMinutes} more minutes, " +
        "and is using them now. Still keeping the commitment.",
    },
    challenge_abandoned: {
      title: "{userName} gave up on this one.",
      body:
        "Hey love, {userName} removed Asr before the challenge ended. The pact is broken. A " +
        "gentle reminder from you may be more effective than anything Asr can send. 😶",
    },
    challenge_given_up: {
      title: "White flag 🏳️",
      body:
        "Hey love, {userName} ended the challenge today. Not sneaked out of — ended, on " +
        "purpose, with a button. That counts for something. Maybe not much, but " +
        "something.",
    },
  },
  wife: {
    time_earned: {
      title: "A little extra time, earned ❤️",
      body:
        "Hey love, {userName} hit the {appName} limit, earned {extraMinutes} more minutes, and is " +
        "using them now. The commitment still stands.",
    },
    challenge_abandoned: {
      title: "{userName} ended the challenge early.",
      body:
        "Hey love, {userName} removed Asr before completing the challenge. The pact is broken. " +
        "Asr will leave the rest of this conversation to you. 😶",
    },
    challenge_given_up: {
      title: "The challenge is off 🏳️",
      body:
        "Hey love, {userName} ended the challenge today rather than quietly letting it " +
        "slip. Honest about it, at least. The rest of this one is yours.",
    },
  },
  friend: {
    time_earned: {
      title: "Back for another round. 👀",
      body:
        "{userName} hit the {appName} limit, earned {extraMinutes} extra minutes, and went " +
        "straight back in. Apparently the scroll was worth working for.",
    },
    challenge_abandoned: {
      title: "Accountability has left the chat. 💀",
      body:
        "{userName} deleted Asr and abandoned the challenge. Can’t lose the challenge if you " +
        "delete the referee, apparently.",
    },
    challenge_given_up: {
      title: "Withdrawn from the competition. 🏳️",
      body:
        "{userName} ended the challenge {themself}. Didn’t break it, didn’t sneak off — " +
        "just looked accountability in the eye and pressed Give up.",
    },
  },
  mentor: {
    time_earned: {
      title: "Still on track.",
      body:
        "{userName} reached the {appName} limit and earned {extraMinutes} additional minutes " +
        "through the challenge rules. The commitment is still intact.",
    },
    challenge_abandoned: {
      title: "The commitment was ended early.",
      body:
        "{userName} removed Asr before completing the challenge, so the pact has been marked as " +
        "broken. Your encouragement may help {them} reset and try again.",
    },
    challenge_given_up: {
      title: "The challenge was ended.",
      body:
        "{userName} ended the challenge before completing it, and did so openly rather " +
        "than letting it lapse. That is worth acknowledging. Your encouragement may " +
        "help with the next one.",
    },
  },
  colleague: {
    time_earned: {
      title: "Extra time earned.",
      body:
        "{userName} reached the {appName} limit and earned {extraMinutes} additional minutes. " +
        "The challenge is still on track.",
    },
    challenge_abandoned: {
      title: "Challenge ended early.",
      body:
        "{userName} removed Asr before completing the challenge. The pact has been marked as " +
        "broken.",
    },
    challenge_given_up: {
      title: "Challenge ended.",
      body:
        "{userName} ended the challenge before it finished. It has been recorded as not " +
        "completed.",
    },
  },
};

/**
 * The restrained voice, for a relationship this table has no entry for.
 *
 * Rows written before the relationship list was split still say "parent",
 * "sibling", "spouse", "partner" or "other", and a witness invited without
 * one at all has null. None of those is a reason to send nothing, and none
 * of them tells us enough to pick a tone — so they get the version that is
 * safe to send to anybody, which is the colleague's.
 */
const FALLBACK = TABLE.colleague as Record<WitnessEvent, Template>;

export function relationshipCopy(
  event: WitnessEvent,
  relationship: Relationship | null,
  vars: CopyVars,
): Copy {
  const templates = (relationship && TABLE[relationship]) || FALLBACK;
  const template = templates[event];
  return { title: fill(template.title, vars), body: fill(template.body, vars) };
}

/** Exported for the test that walks every relationship. */
export const RELATIONSHIPS_WITH_COPY = Object.keys(TABLE) as Relationship[];
