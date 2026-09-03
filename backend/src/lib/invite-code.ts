import { randomInt } from "node:crypto";

// 10 characters from an alphabet without 0/O/1/I, so a code read aloud or
// typed from a screenshot survives. 32^10 ≈ 1e15 possibilities; the column
// is unique and creation is rate limited, so guessing is not a concern.
const ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
export const INVITE_CODE_LENGTH = 10;
export const INVITE_CODE_RE = /^[A-HJ-NP-Z2-9]{10}$/;

export function generateInviteCode(): string {
  let out = "";
  for (let i = 0; i < INVITE_CODE_LENGTH; i++) out += ALPHABET[randomInt(ALPHABET.length)];
  return out;
}

export function isInviteCode(value: unknown): value is string {
  return typeof value === "string" && INVITE_CODE_RE.test(value);
}
