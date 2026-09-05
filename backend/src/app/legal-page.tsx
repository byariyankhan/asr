import type { LegalDocument } from "@/lib/legal";
import { SiteFrame } from "./site-frame";

/** The privacy policy and the terms, in the same frame with the same shape. */
export function LegalPage({ document, other }: { document: LegalDocument; other: { href: string; label: string } }) {
  return (
    <SiteFrame
      nav={[{ href: "/#how-it-works", label: "How it works" }, other]}
      footer={[{ href: "/", label: "Home" }, other]}
    >
      <main className="legal-main">
        <div className="shell">
          <header>
            <p className="eyebrow">{document.eyebrow}</p>
            <h1>{document.title}</h1>
            <p className="lede">{document.lede}</p>
            <p className="updated">{document.effective}</p>
          </header>
          <div className="legal-copy">
            {document.sections.map((section) => (
              <section key={section.heading}>
                <h2>{section.heading}</h2>
                <p>{section.body}</p>
              </section>
            ))}
          </div>
        </div>
      </main>
    </SiteFrame>
  );
}
