import type { Metadata } from "next";
import { SiteFrame } from "./site-frame";

const SITE = () => (process.env.PUBLIC_SITE_URL ?? "https://joinasr.io").replace(/\/$/, "");

export const metadata: Metadata = {
  title: "Asr - Protect Your Time & Focus",
  description:
    "Asr helps you put selected apps behind a time-limited pact and keep your witnesses informed.",
  openGraph: {
    title: "Asr - Protect Your Time & Focus",
    description:
      "Choose the apps that take too much of your day, set a daily limit, and name the people who should know whether you kept your word.",
    url: SITE(),
    siteName: "Asr",
    type: "website",
  },
};

/** joinasr.io. What somebody finds when they follow the brand rather than a link. */
export default function LandingPage() {
  return (
    <SiteFrame
      nav={[
        { href: "#how-it-works", label: "How it works" },
        { href: "/privacy", label: "Privacy" },
      ]}
      footer={[
        { href: "/privacy", label: "Privacy" },
        { href: "/terms", label: "Terms" },
        { href: "mailto:hi@ariyankhan.com", label: "Contact" },
      ]}
    >
      <main>
        <section className="hero">
          <div className="shell hero-grid">
            <div>
              <p className="eyebrow">Screen-time commitment</p>
              <h1>Make a pact with your time.</h1>
              <p className="lede">
                Choose the apps that take too much of your day, set a daily limit, and name the
                people who should know whether you kept your word.
              </p>
              {/* A placeholder and not a link until the listing exists: a
                  Play URL that answers "item not found" is worse than a
                  badge that says when. */}
              <div className="play-placeholder" aria-label="Coming soon to Google Play">
                <svg width="20" height="22" viewBox="0 0 20 22" aria-hidden="true">
                  <path
                    fill="#04110D"
                    d="M1.7.6 12.5 11 1.7 21.4A2 2 0 0 1 1 19.9V2.1A2 2 0 0 1 1.7.6Zm12 11.6 2.5 2.4-11.5 6.5 9-8.9Zm3.9-3a2 2 0 0 1 0 3.6l-2.2 1.3-2.7-3.1 2.7-3.1 2.2 1.3ZM4.7.9l11.5 6.5-2.5 2.4-9-8.9Z"
                  />
                </svg>
                Coming to Google Play
              </div>
            </div>

            <aside className="pact-card" aria-label="Example pact">
              <p className="eyebrow">Your pact</p>
              <p className="card-label">Commitment length</p>
              <div className="pact-days" aria-label="Pact length options">
                <span className="day active">7 days</span>
                <span className="day">14</span>
                <span className="day">21</span>
                <span className="day">30</span>
              </div>
              <div className="status-row">
                <div>
                  <p className="status-title">Today’s limit</p>
                  <p className="status-copy">Your witnesses will know</p>
                </div>
                <span className="status-dot" aria-hidden="true" />
              </div>
            </aside>
          </div>
        </section>

        <section className="section" id="how-it-works">
          <div className="shell">
            <div className="section-heading">
              <p className="eyebrow">How it works</p>
              <h2>A limit means more when someone else knows.</h2>
              <p>
                Asr turns a screen-time goal into a clear commitment with a beginning, an end, and
                people who can hold you to it.
              </p>
            </div>
            <div className="steps">
              <article className="step">
                <h3>Choose your limits</h3>
                <p>Pick the apps you want to limit and decide how many minutes each gets every day.</p>
              </article>
              <article className="step">
                <h3>Make the pact</h3>
                <p>
                  Commit for 7, 14, 21, or 30 days, or choose a custom length, and name your
                  witnesses.
                </p>
              </article>
              <article className="step">
                <h3>Keep your word</h3>
                <p>
                  Your witnesses are told when you keep the pact. If you give it up, switch
                  protection off, or uninstall Asr, they are told that too.
                </p>
              </article>
            </div>
          </div>
        </section>

        <section className="section">
          <div className="shell">
            <div className="promise">
              <p className="eyebrow">The honest promise</p>
              <p>
                Asr does not promise to give you perfect discipline. It makes the commitment
                visible, makes breaking it accountable, and lets the people you trust see how you
                did.
              </p>
            </div>
          </div>
        </section>
      </main>
    </SiteFrame>
  );
}
