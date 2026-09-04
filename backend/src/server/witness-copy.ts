import type { Relationship } from "./db/schema";

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
 * - `challenge_abandoned` is the pact being ended on purpose — Asr removed,
 *   protection switched off, or given up. This is the one that is allowed
 *   to bite.
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
export type WitnessEvent = "time_earned" | "challenge_abandoned";

export type CopyVars = {
  /** The person being witnessed. */
  userName: string;
  /** The app the limit was reached on, by its label: "TikTok", not a
   *  package name. Only `time_earned` uses it. */
  appName: string;
  /** Minutes the activity awarded. Only `time_earned` uses it. */
  extraMinutes: number;
};

export type Copy = { title: string; body: string };

type Template = { title: string; body: string };

/**
 * `{userName}`, `{appName}` and `{extraMinutes}`, and nothing else.
 *
 * Unknown placeholders are left alone rather than replaced with "undefined":
 * a typo in a template should look like a typo, not like a bug in whoever's
 * account it landed in.
 */
function fill(template: string, vars: CopyVars): string {
  return template.replace(/\{(userName|appName|extraMinutes)\}/g, (_, key: string) => {
    if (key === "userName") return vars.userName;
    if (key === "appName") return vars.appName;
    return String(vars.extraMinutes);
  });
}

const TABLE: Record<string, Record<WitnessEvent, Template>> = {
  mother: {
    time_earned: {
      title: "He earned a little more time 👀",
      body:
        "Hey Mom, {userName} reached the {appName} limit, earned {extraMinutes} extra minutes, " +
        "and is using them now. Don’t worry, he’s still playing by the rules.",
    },
    challenge_abandoned: {
      title: "The challenge ended early",
      body:
        "Hey Mom, {userName} removed Asr before finishing the challenge. The pact is now broken. " +
        "You may want to have a little talk with him. 😅",
    },
  },
  father: {
    time_earned: {
      title: "He earned his way back in.",
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
        "Hey bro, {userName} couldn’t change the rules, so he deleted Asr instead. The challenge " +
        "is officially over. You know what to do. 💀",
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
      title: "Well… he actually deleted it. 💀",
      body:
        "Hey sis, {userName} removed Asr before finishing the challenge. The pact is broken. This " +
        "information is now yours to use responsibly. Or not.",
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
        "broken. Your encouragement may help them reset and try again.",
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
