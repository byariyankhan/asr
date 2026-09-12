import { afterEach, describe, expect, it, vi } from "vitest";
import {
  emailFailureLine,
  inviteEmail,
  maskAddresses,
  resetCodeEmail,
  sendEmail,
} from "./email";

/**
 * The emailed invitation said "their phone" and "if they keep it" about
 * everybody, while the page the same link opens already said "his". Same
 * table, same rule as the page: the inviter's own pronoun, and a sentence
 * that names the promise rather than the person where a verb would have to
 * agree.
 */
describe("inviteEmail", () => {
  const url = "https://joinasr.com/w/G73N2QJWGM";

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

const CODE = "4830192";

/**
 * A refused send used to leave nothing at all. Resend rejecting the From
 * address looked exactly like Resend accepting it -- the API answered the
 * same 200 either way, the app said "check your email" either way, and no
 * record anywhere said which had happened. These describe the line a
 * failure leaves now, and the things that line must never carry.
 */
describe("the line a failed send leaves", () => {
  it("keeps the domain, which explains the bounce, and drops the person", () => {
    expect(maskAddresses("ariyan@gmail.com")).toBe("***@gmail.com");
    expect(maskAddresses("Asr <noreply@joinasr.com>")).toBe("Asr <***@joinasr.com>");
    expect(maskAddresses("a@x.com and b@y.com")).toBe("***@x.com and ***@y.com");
    expect(maskAddresses("nothing to hide in here")).toBe("nothing to hide in here");
  });

  it("names the kind, the domain, and what the provider said", () => {
    const line = JSON.parse(
      emailFailureLine("reset", "ariyan@gmail.com", "The joinasr.com domain is not verified."),
    );
    expect(line.event).toBe("email_failed");
    expect(line.kind).toBe("reset");
    expect(line.to).toBe("***@gmail.com");
    expect(line.error).toContain("not verified");
    expect(Number.isNaN(Date.parse(line.at))).toBe(false);
  });

  it("masks an address the provider quoted back at us", () => {
    const line = JSON.parse(
      emailFailureLine("invite", "someone@example.com", "Invalid `to` field: someone@example.com"),
    );
    expect(line.error).toBe("Invalid `to` field: ***@example.com");
  });

  it("never carries the reset code, the body, or the subject", () => {
    const mail = resetCodeEmail(CODE);
    expect(mail.text).toContain(CODE); // the code really is in the message
    const line = emailFailureLine(mail.kind, "ariyan@gmail.com", "connect ETIMEDOUT");
    expect(line).not.toContain(CODE);
    expect(line).not.toContain(mail.subject);
  });
});

describe("sending with no key configured", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
  });

  it("prints the whole message in development, which is how a reset link is opened locally", async () => {
    vi.stubEnv("RESEND_API_KEY", "");
    vi.stubEnv("NODE_ENV", "development");
    const info = vi.spyOn(console, "info").mockImplementation(() => {});
    const error = vi.spyOn(console, "error").mockImplementation(() => {});
    const result = await sendEmail("ariyan@gmail.com", resetCodeEmail(CODE));
    expect(result).toEqual({ ok: false, error: "email_not_configured" });
    expect(String(info.mock.calls[0]?.[0])).toContain(CODE);
    expect(error).not.toHaveBeenCalled();
  });

  it("in production writes the failure down, and never the body", async () => {
    vi.stubEnv("RESEND_API_KEY", "");
    vi.stubEnv("NODE_ENV", "production");
    const info = vi.spyOn(console, "info").mockImplementation(() => {});
    const error = vi.spyOn(console, "error").mockImplementation(() => {});
    const result = await sendEmail("ariyan@gmail.com", resetCodeEmail(CODE));
    expect(result).toEqual({ ok: false, error: "email_not_configured" });
    expect(info).not.toHaveBeenCalled();
    const written = String(error.mock.calls[0]?.[0]);
    expect(JSON.parse(written)).toMatchObject({
      event: "email_failed",
      kind: "reset",
      to: "***@gmail.com",
      error: "email_not_configured",
    });
    expect(written).not.toContain(CODE);
  });
});

/**
 * The code is what the reset is. It has to be findable by somebody reading
 * the message on a phone, and it must not be surrounded by other numbers
 * that could be mistaken for it.
 */
describe("the reset code email", () => {
  it("puts the code alone on its own line, with no link anywhere", () => {
    const mail = resetCodeEmail(CODE);
    const lines = mail.text.split("\n");
    expect(lines[0]).toBe(CODE);
    expect(lines.filter((line) => line.includes(CODE))).toHaveLength(1);
    expect(mail.text).not.toContain("http");
  });

  it("says how long it lasts and how many tries there are", () => {
    const mail = resetCodeEmail(CODE);
    expect(mail.text).toContain("ten minutes");
    expect(mail.text).toContain("three tries");
  });
});
