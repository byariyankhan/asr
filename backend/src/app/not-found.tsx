import type { Metadata } from "next";
import Link from "next/link";
import { SiteFrame } from "./site-frame";

export const metadata: Metadata = {
  title: "Page not found — Asr",
  robots: { index: false, follow: false },
};

/** Every path that is not a page, an endpoint or a link the product sends. */
export default function NotFound() {
  return (
    <SiteFrame
      nav={[
        { href: "/privacy", label: "Privacy" },
        { href: "/terms", label: "Terms" },
      ]}
      footer={[
        { href: "/", label: "Home" },
        { href: "/privacy", label: "Privacy" },
      ]}
    >
      <main className="not-found">
        <div className="shell">
          <p className="eyebrow">404 / Page not found</p>
          <h1>This page isn’t here.</h1>
          <p>
            The address may be wrong, or the page may have moved. Your next minute is better spent
            somewhere useful.
          </p>
          <Link className="text-link" href="/">
            Return to Asr
          </Link>
        </div>
      </main>
    </SiteFrame>
  );
}
