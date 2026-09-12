import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { EFFECTIVE, privacy, terms, type LegalSection } from "./legal";

/**
 * The web pages and the app show the same privacy policy and terms.
 *
 * Neither side can import the other -- one is Kotlin, one is TypeScript --
 * so this reads the Kotlin source, resolves its string concatenations, and
 * compares section by section. The alternative is two documents that were
 * the same on the day they were written, which is how a privacy policy ends
 * up naming a provider that was never installed -- or, the other way round,
 * promising there is no analytics on the day analytics is added.
 */
const KOTLIN = path.resolve(
  __dirname,
  "../../../android/app/src/main/java/com/joinasr/app/legal/LegalTexts.kt",
);

const MANIFEST = path.resolve(__dirname, "../../../android/app/src/main/AndroidManifest.xml");

type Parsed = { eyebrow: string; title: string; sections: LegalSection[] };

function unescape(literal: string): string {
  return literal.replace(/\\n/g, "\n").replace(/\\"/g, '"').replace(/\\'/g, "'").replace(/\\\\/g, "\\");
}

function literals(source: string): string[] {
  return [...source.matchAll(/"((?:[^"\\]|\\.)*)"/g)].map((m) => unescape(m[1]!));
}

function parseDocument(source: string, name: string): Parsed {
  const start = source.indexOf(`val ${name} = LegalDocument(`);
  if (start < 0) throw new Error(`no ${name} in LegalTexts.kt`);
  const nextDoc = source.indexOf("= LegalDocument(", start + 20);
  const block = source.slice(start, nextDoc < 0 ? undefined : nextDoc);

  const eyebrow = /eyebrow = "([^"]*)"/.exec(block)?.[1];
  const title = /title = "([^"]*)"/.exec(block)?.[1];
  if (!eyebrow || !title) throw new Error(`${name}: eyebrow or title missing`);

  const chunks = block.split("LegalSection(").slice(1);
  const sections = chunks.map((chunk) => {
    // Each section's arguments end at the `),` that closes the call. The
    // literals before it are the heading and then the body's pieces, which
    // Kotlin joins with `+` and we join by concatenation.
    const inner = chunk.slice(0, chunk.indexOf("\n            ),"));
    const [heading, ...body] = literals(inner);
    return { heading: heading!, body: body.join("") };
  });
  return { eyebrow, title, sections };
}

describe("the legal texts the app and the site show", () => {
  const source = readFileSync(KOTLIN, "utf8");

  it("carry the same effective date", () => {
    expect(source).toContain(`const val EFFECTIVE = "${EFFECTIVE}"`);
  });

  it("are the same privacy policy", () => {
    const kotlin = parseDocument(source, "privacy");
    expect(kotlin.eyebrow).toBe(privacy.eyebrow);
    expect(kotlin.title).toBe(privacy.title);
    expect(kotlin.sections).toEqual(privacy.sections);
  });

  it("are the same terms", () => {
    const kotlin = parseDocument(source, "terms");
    expect(kotlin.eyebrow).toBe(terms.eyebrow);
    expect(kotlin.title).toBe(terms.title);
    expect(kotlin.sections).toEqual(terms.sections);
  });

  it("name the analytics the app has, and what it never receives", () => {
    const sharing = privacy.sections.find((s) => s.heading === "6. Sharing")?.body ?? "";
    expect(sharing).toContain("product analytics (Google Firebase)");
    expect(sharing).toContain("never the apps you limit, your minutes, your name, your email address or your witnesses");
    expect(sharing).toContain("The advertising identifier is switched off.");
    expect(sharing).not.toContain("no analytics");
  });

  /**
   * The policy has to name every sensitive permission the app asks for.
   *
   * It did not. Earning time grew from walking and a focus session to nine
   * activities, three of which open the camera and one of which reads GPS,
   * and the policy still described only step counts -- while saying, of the
   * one activity it did describe, that it needed no location. A reviewer
   * comparing the manifest with the policy would have read that as a denial.
   *
   * So the manifest is the input: add a permission from this list and the
   * words that have to appear alongside it are not optional any more.
   */
  const SENSITIVE: Array<[permission: string, mustSay: RegExp]> = [
    ["android.permission.CAMERA", /camera/i],
    ["android.permission.ACCESS_FINE_LOCATION", /precise location|GPS/i],
    ["android.permission.ACTIVITY_RECOGNITION", /step counter|physical activity/i],
    ["android.permission.PACKAGE_USAGE_STATS", /Usage Access/i],
    ["android.permission.SYSTEM_ALERT_WINDOW", /display over other apps/i],
  ];

  it("names every sensitive permission the manifest asks for", () => {
    const manifest = readFileSync(MANIFEST, "utf8");
    const policy = privacy.sections.map((s) => s.body).join("\n");
    // Without this the test passes by matching nothing at all -- a renamed
    // manifest path or a typo in a permission string would read as a clean
    // bill of health.
    const asked = SENSITIVE.filter(([permission]) => manifest.includes(permission));
    expect(asked.length).toBe(SENSITIVE.length);
    for (const [permission, mustSay] of asked) {
      expect(mustSay.test(policy), `${permission} is asked for but the policy never says so`).toBe(
        true,
      );
    }
  });

  it("says what the camera and the location are not used for", () => {
    const rewards = privacy.sections.find((s) => s.heading === "3. Activity rewards")?.body ?? "";
    // The three promises that make the permissions acceptable to grant. Each
    // is a fact about the code (PoseCamera.kt, RideService.kt); if one ever
    // stops being true, the sentence has to go before the code ships.
    expect(rewards).toContain("no photo or video is recorded, saved or uploaded");
    expect(rewards).toContain("no route is recorded and no location is uploaded");
    expect(rewards).toContain("never in the background");
    // And no leftover of the sentence that read as a blanket denial.
    expect(rewards).not.toContain("do not require GPS or location access");
  });
});
