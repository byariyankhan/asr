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
  "../../../android/app/src/main/java/io/joinasr/app/legal/LegalTexts.kt",
);

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
});
