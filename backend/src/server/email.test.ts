import { describe, expect, it } from "vitest";
import { inviteEmail } from "./email";

/**
 * The emailed invitation said "their phone" and "if they keep it" about
 * everybody, while the page the same link opens already said "his". Same
 * table, same rule as the page: the inviter's own pronoun, and a sentence
 * that names the promise rather than the person where a verb would have to
 * agree.
 */
describe("inviteEmail", () => {
  const url = "https://joinasr.io/w/G73N2QJWGM";

  it("uses the inviter's own pronoun", () => {
    expect(inviteEmail("Ariyan", "brother", url, "male").text).toContain("use his phone less");
    expect(inviteEmail("Alice", "friend", url, "female").text).toContain("use her phone less");
    expect(inviteEmail("Sam", null, url, null).text).toContain("use their phone less");
    expect(inviteEmail("Sam", null, url, "prefer_not_to_say").text).toContain("use their phone less");
  });

  it("leaves no other person's pronoun anywhere in the message", () => {
    expect(inviteEmail("Ariyan", "brother", url, "male").text).not.toMatch(/\b(their|they|them|her|she)\b/);
    expect(inviteEmail("Alice", "friend", url, "female").text).not.toMatch(/\b(their|they|them|his|him|he)\b/);
    expect(inviteEmail("Sam", null, url, null).text).not.toMatch(/\b(his|him|he|her|she)\b/);
  });

  it("names who is asking, as what, and where to answer", () => {
    const mail = inviteEmail("Ariyan", "brother", url, "male");
    expect(mail.subject).toBe("Ariyan wants you as a witness");
    expect(mail.text).toContain("Ariyan (your brother) is making a pact");
    expect(mail.text).toContain(url);
  });
});
