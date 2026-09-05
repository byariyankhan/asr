import Link from "next/link";
import type { ReactNode } from "react";
import "./site.css";

/**
 * The header and footer every public page sits inside.
 *
 * The site lived on an unmerged branch as three static HTML files while
 * joinasr.io answered 404 -- the address on every invitation the app sends,
 * and the one Play needs for a privacy policy. It is served by the API
 * application now, behind the same nginx site, so shipping it is a deploy
 * and not a change to the server. Markup and styles are that branch's,
 * carried over; the colours are the app's.
 */
export type NavLink = { href: string; label: string };

export function SiteFrame({
  nav,
  footer,
  children,
}: {
  nav: NavLink[];
  footer: NavLink[];
  children: ReactNode;
}) {
  return (
    <div className="site">
      <header className="site-header">
        <div className="shell nav">
          <Link className="brand" href="/" aria-label="Asr home">
            Asr<span className="brand-mark">.</span>
          </Link>
          <nav className="nav-links" aria-label="Main navigation">
            {nav.map((link) => (
              <Link key={link.href} href={link.href}>
                {link.label}
              </Link>
            ))}
          </nav>
        </div>
      </header>
      {children}
      <footer className="site-footer">
        <div className="shell footer-inner">
          <p className="footer-copy">© 2026 Asr · Ariyan Khan, Dhaka. Make your time count.</p>
          <nav className="footer-links" aria-label="Legal navigation">
            {footer.map((link) =>
              link.href.startsWith("mailto:") ? (
                <a key={link.href} href={link.href}>
                  {link.label}
                </a>
              ) : (
                <Link key={link.href} href={link.href}>
                  {link.label}
                </Link>
              ),
            )}
          </nav>
        </div>
      </footer>
    </div>
  );
}
