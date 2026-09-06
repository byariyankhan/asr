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
 * The other six -- a pact started, kept, broken past a limit, moved to
 * another phone, left unenforced for two hours, silent for a day -- were
 * written later, in the same nine voices, when the plain copy they had been
 * getting ("{name} kept their word", to a mother about her son) was the
 * one sentence in the product that did not know who it was talking about.
 * Every witness message now comes from this table; nothing is composed
 * anywhere else.
 */
export type WitnessEvent =
  | "pact_started"
  | "pact_completed"
  | "limit_broken"
  | "challenge_abandoned"
  | "challenge_given_up"
  | "pact_moved"
  | "protection_off"
  | "protection_lost"
  | "time_earned";

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
const capital = (word: string) => word.charAt(0).toUpperCase() + word.slice(1);

function fill(template: string, vars: CopyVars): string {
  const p = pronounsFor(vars.gender);
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
    pact_started: {
      title: "{userName} made a promise 🤍",
      body:
        "Hey Mom, {userName} just started a screen-time challenge and asked you to witness it. You’ll hear how it goes, and {they} {is} counting on you to notice.",
    },
    pact_completed: {
      title: "{userName} kept {their} word 🤍",
      body:
        "Hey Mom, {userName} finished the whole challenge without breaking it once. This is a good moment to tell {them} you noticed.",
    },
    limit_broken: {
      title: "The pact didn’t hold.",
      body:
        "Hey Mom, {userName} went past a limit {they} had set, so the challenge is now broken. Not the end of the world. A kind word may help more than a hard one.",
    },
    pact_moved: {
      title: "{userName} changed phones",
      body:
        "Hey Mom, {userName} moved the challenge to a different phone. Same days, same limits, nothing lost. Just so you know where it lives now.",
    },
    protection_off: {
      title: "Nothing is stopping the apps right now",
      body:
        "Hey Mom, blocking has been switched off on {userName}’s phone for two hours. The challenge is still running, but nothing is holding the limits. A gentle nudge might help.",
    },
    protection_lost: {
      title: "We haven’t heard from {userName}’s phone",
      body:
        "Hey Mom, {userName}’s phone has been silent for a whole day. The challenge is still running, but nothing has confirmed it is being kept. Might be worth checking in.",
    },
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
    pact_started: {
      title: "{userName} started a challenge.",
      body:
        "Hey Dad, {userName} just started a screen-time challenge and named you as a witness. You’ll be told how it ends, and whether {they} kept to it along the way.",
    },
    pact_completed: {
      title: "{userName} saw it through.",
      body:
        "Hey Dad, {userName} completed the challenge, every day of it, within the limits {they} set. Worth saying something.",
    },
    limit_broken: {
      title: "{userName} broke the pact.",
      body:
        "Hey Dad, {userName} went over one of the limits {they} committed to, so the challenge is over. Something to talk through before the next one.",
    },
    pact_moved: {
      title: "{userName} switched phones.",
      body:
        "Hey Dad, {userName} is keeping the challenge on a different phone from now on. The days and the limits carried over unchanged.",
    },
    protection_off: {
      title: "{userName}’s limits aren’t being enforced.",
      body:
        "Hey Dad, {userName}’s phone hasn’t been blocking anything for two hours. The challenge is still on paper; in practice, nothing is stopping the apps right now.",
    },
    protection_lost: {
      title: "{userName}’s phone went quiet.",
      body:
        "Hey Dad, nothing has come from {userName}’s phone in a day. The challenge hasn’t ended, but it isn’t being confirmed either. Worth a call.",
    },
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
    pact_started: {
      title: "{userName} is doing a challenge. Allegedly. 👀",
      body:
        "Hey bro, {userName} just started a screen-time challenge and picked you to watch it. You’ll hear about every slip. Use this power wisely. Or don’t.",
    },
    pact_completed: {
      title: "{userName} actually did it. 😳",
      body:
        "Hey bro, {userName} finished the whole challenge without breaking it once. No loopholes, no deleted apps. You may now be slightly impressed.",
    },
    limit_broken: {
      title: "{userName} broke it. 💀",
      body:
        "Hey bro, {userName} blew past a limit {they} set for {themself}, and the challenge is officially broken. You have the full moral high ground. Temporarily.",
    },
    pact_moved: {
      title: "{userName} moved the challenge to another phone 👀",
      body:
        "Hey bro, {userName} moved the challenge to a different phone. Same limits, same days. You’re being told because a phone left in a drawer is the oldest trick there is.",
    },
    protection_off: {
      title: "{userName} switched the blocking off 👀",
      body:
        "Hey bro, {userName}’s phone has been enforcing nothing for two hours while the challenge is still ‘running’. Draw your own conclusions. Then say so out loud.",
    },
    protection_lost: {
      title: "{userName} went dark 👀",
      body:
        "Hey bro, {userName}’s phone hasn’t reported anything for a day. Dead battery, or a very quiet exit. Find out which.",
    },
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
    pact_started: {
      title: "{userName} made a pact 👀",
      body:
        "Hey sis, {userName} just started a screen-time challenge with you as the witness. If {they} {does} anything dramatic, you’ll be the first to know.",
    },
    pact_completed: {
      title: "{userName} pulled it off ✨",
      body:
        "Hey sis, {userName} finished the challenge and kept every limit the whole way through. Yes, really. Some acknowledgement is due.",
    },
    limit_broken: {
      title: "The pact is broken 💀",
      body:
        "Hey sis, {userName} went past a limit and the challenge is over. {They} knew you’d hear about it. And now you have.",
    },
    pact_moved: {
      title: "New phone, same pact 👀",
      body:
        "Hey sis, {userName} moved the challenge onto another phone. The limits followed. You’re being told so nothing moves quietly.",
    },
    protection_off: {
      title: "Blocking’s been off for two hours 👀",
      body:
        "Hey sis, {userName}’s phone stopped blocking anything two hours ago. The challenge is technically still on. Technically.",
    },
    protection_lost: {
      title: "{userName}’s phone has gone quiet 👀",
      body:
        "Hey sis, nothing from {userName}’s phone in a whole day. The challenge is still on, in theory. Ask what happened.",
    },
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
    pact_started: {
      title: "A promise, with you watching ❤️",
      body:
        "Hey love, {userName} just started a screen-time challenge and asked you to be the witness. You’ll hear how it goes, and {they} will know you’re watching.",
    },
    pact_completed: {
      title: "{userName} kept the promise ❤️",
      body:
        "Hey love, {userName} finished the challenge and stayed inside the limits the entire time, with you watching. Say something nice tonight.",
    },
    limit_broken: {
      title: "The pact didn’t make it this time.",
      body:
        "Hey love, {userName} went over a limit {they} had set, and the challenge is broken. Be gentle. Then maybe a little less gentle.",
    },
    pact_moved: {
      title: "The challenge moved phones",
      body:
        "Hey love, {userName} is running the challenge from a different phone now. Same days, same limits, carried over.",
    },
    protection_off: {
      title: "The limits aren’t being held right now",
      body:
        "Hey love, blocking has been off on {userName}’s phone for two hours. The challenge is still running, but nothing is enforcing it. Maybe ask.",
    },
    protection_lost: {
      title: "{userName}’s phone has been silent for a day",
      body:
        "Hey love, nothing has come from {userName}’s phone in a day. Probably nothing sinister, but the challenge can’t be confirmed until it reports again.",
    },
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
    pact_started: {
      title: "A promise you get to witness ❤️",
      body:
        "Hey love, {userName} just started a screen-time challenge and wants you as the witness. You’ll be told how it goes, every step.",
    },
    pact_completed: {
      title: "Promise kept ❤️",
      body:
        "Hey love, {userName} completed the whole challenge without breaking the pact once. That took something. Tonight might deserve a small celebration.",
    },
    limit_broken: {
      title: "The challenge is broken.",
      body:
        "Hey love, {userName} went past one of {their} own limits and the pact ended there. Not a disaster. The next attempt is the one that counts.",
    },
    pact_moved: {
      title: "Same pact, different phone",
      body:
        "Hey love, {userName} moved the challenge to another phone. Nothing about the days or the limits changed.",
    },
    protection_off: {
      title: "Nothing is enforcing the pact right now",
      body:
        "Hey love, {userName}’s phone hasn’t blocked anything for two hours while the challenge is still running. It may be an accident. Worth a question.",
    },
    protection_lost: {
      title: "No word from {userName}’s phone",
      body:
        "Hey love, {userName}’s phone hasn’t checked in for a day. The challenge is still running; it just can’t be confirmed right now.",
    },
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
    pact_started: {
      title: "{userName} is doing a challenge. You’re the witness. 👀",
      body:
        "{userName} just started a screen-time challenge and picked you to keep {them} honest. You’ll hear the moment a limit gives way.",
    },
    pact_completed: {
      title: "{userName} finished the challenge. Undefeated. 🏆",
      body:
        "{userName} completed the whole challenge without breaking it once. Accountability worked. Tell {them} you saw it.",
    },
    limit_broken: {
      title: "{userName} broke the pact. 💀",
      body:
        "{userName} went over a limit {they} set, and the challenge is officially broken. The witness has been notified. That’s you.",
    },
    pact_moved: {
      title: "{userName} changed phones 👀",
      body:
        "{userName} moved the challenge to a different phone. Same limits, same days. Noted, in case the old one is conveniently in a drawer.",
    },
    protection_off: {
      title: "{userName}’s blocking has been off for two hours 👀",
      body:
        "{userName}’s phone stopped enforcing the limits two hours ago, and the challenge is still running. Could be a permission. Could be a loophole. Ask.",
    },
    protection_lost: {
      title: "{userName} went quiet 👀",
      body:
        "{userName}’s phone hasn’t reported in for a day. The challenge is still technically running. Check that {they} {is} alive, and then check the phone.",
    },
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
    pact_started: {
      title: "{userName} has started a challenge.",
      body:
        "{userName} has started a screen-time challenge and asked you to witness it. You’ll be told how it goes. A word from you along the way may matter more than you’d expect.",
    },
    pact_completed: {
      title: "{userName} completed the challenge.",
      body:
        "{userName} completed the challenge and stayed within the limits throughout. Consistency like that is worth naming. A word from you would land well.",
    },
    limit_broken: {
      title: "The pact was broken.",
      body:
        "{userName} went over one of the limits {they} committed to, so the challenge has ended as broken. Encouragement now may decide whether there is a next attempt.",
    },
    pact_moved: {
      title: "{userName} moved to another phone.",
      body:
        "{userName} is continuing the challenge on a different phone. The duration and the limits are unchanged; this is only so the record is complete.",
    },
    protection_off: {
      title: "{userName}’s challenge is not being enforced.",
      body:
        "{userName}’s phone has not been blocking anything for two hours while the challenge continues. This is usually a permission that was switched off; a reminder to check may be all that is needed.",
    },
    protection_lost: {
      title: "{userName}’s phone has been silent for a day.",
      body:
        "{userName}’s phone has not reported for a day. The challenge continues, but enforcement cannot be confirmed. A check-in from you may help.",
    },
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
    pact_started: {
      title: "Challenge started.",
      body:
        "{userName} started a screen-time challenge and named you as a witness. You’ll be notified when it ends.",
    },
    pact_completed: {
      title: "Challenge completed.",
      body:
        "{userName} completed the challenge within the limits {they} set. It has been recorded as kept.",
    },
    limit_broken: {
      title: "Pact broken.",
      body:
        "{userName} exceeded a limit set for the challenge. It has been recorded as broken.",
    },
    pact_moved: {
      title: "Challenge moved phones.",
      body:
        "{userName} moved the challenge to another phone. The days and limits are unchanged.",
    },
    protection_off: {
      title: "Challenge not being enforced.",
      body:
        "{userName}’s phone has not enforced the limits for two hours. The challenge is still running.",
    },
    protection_lost: {
      title: "No report from {userName}’s phone.",
      body:
        "{userName}’s phone has not reported in a day. The challenge is still running.",
    },
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

/**
 * What to call a witness when talking to the person they watch.
 *
 * "Mom saw what happened", not "Jonny Harris saw what happened" -- the name
 * is already the title of the notification, and repeating it says nothing
 * the line above did not. What the second line is for is the thing the title
 * cannot carry: *who this person is to you*.
 *
 * Second person throughout, because that is who is reading: a parent is
 * "Mom" and "Dad" because nobody says "your mother saw that", and everybody
 * else takes "Your", because "Brother saw that" is not English.
 */
const WITNESS_LABEL: Record<string, string> = {
  mother: "Mom",
  father: "Dad",
  brother: "Your brother",
  sister: "Your sister",
  husband: "Your husband",
  wife: "Your wife",
  partner: "Your partner",
  friend: "Your friend",
  mentor: "Your mentor",
  colleague: "Your colleague",
  // Written by builds before the specific list, and still on rows.
  parent: "Your parent",
  sibling: "Your brother or sister",
  spouse: "Your husband or wife",
  other: "Your witness",
};

export function witnessLabel(relationship?: string | null): string {
  return (relationship && WITNESS_LABEL[relationship]) || "Your witness";
}

/**
 * To the person being witnessed: who just said yes, named the way that
 * person is spoken of and with that person's own pronoun. "Ariyan Khan
 * accepted. They'll know if your pact breaks" went to a man about his own
 * brother, when the profile knew better on both counts.
 */
export function witnessAcceptedCopy(
  witnessName: string,
  relationship: string | null | undefined,
  gender?: Gender | null,
): Copy {
  const p = pronounsFor(gender);
  return {
    title: `${witnessName} is your witness`,
    body: `${witnessLabel(relationship)} accepted. ${capital(p.they)}’ll know if your pact breaks.`,
  };
}

/** To everybody connected to an account that is being deleted. */
export function leftAsrCopy(name: string, gender?: Gender | null): Copy {
  const p = pronounsFor(gender);
  return {
    title: `${name} left Asr`,
    body: `${name} deleted ${p.their} account, so you are no longer connected.`,
  };
}

/**
 * Who the reader is to the person asking, said from that person's side:
 * "his brother", "her mentor", "their friend".
 *
 * For the invitation page, where the same sentence goes on to say "to be
 * his witness". The two halves have to agree, and they did not: the first
 * came from a map with "their" written into every entry and the second
 * from the pronoun table, which put "asked you, as their brother, to be
 * his witness" on a real invitation. Both halves come from the table now.
 *
 * "other", the lumped values older rows still carry, and null all read as
 * "someone close to him". What the relationship list could not say is not
 * a reason to say something odd.
 */
const RELATIONSHIP_NOUN: Record<string, string> = {
  mother: "mother",
  father: "father",
  brother: "brother",
  sister: "sister",
  husband: "husband",
  wife: "wife",
  partner: "partner",
  friend: "friend",
  mentor: "mentor",
  colleague: "colleague",
  // Written by builds before the specific list, and still on rows.
  parent: "parent",
  sibling: "brother or sister",
  spouse: "husband or wife",
};

export function relationshipPhrase(
  relationship: string | null | undefined,
  gender?: Gender | null,
): string {
  const p = pronounsFor(gender);
  const noun = relationship ? RELATIONSHIP_NOUN[relationship] : undefined;
  return noun ? `${p.their} ${noun}` : `someone close to ${p.them}`;
}

/**
 * The line under the name on the invitation page: "asked you, as his
 * brother, to be his witness for a 14-day challenge." One function, so the
 * test can read the whole sentence and check that every pronoun in it is
 * the same person's.
 */
export function inviteLead(invite: {
  relationship?: string | null;
  gender?: Gender | null;
  days?: number | null;
}): string {
  const p = pronounsFor(invite.gender);
  const days = invite.days ? ` for a ${invite.days}-day challenge` : "";
  return `asked you, as ${relationshipPhrase(invite.relationship, invite.gender)}, to be ${p.their} witness${days}.`;
}
