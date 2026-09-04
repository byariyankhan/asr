import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { peekInvite } from "@/server/witnesses";
import { pronounsFor } from "@/server/witness-copy";

/**
 * The page a witness invitation actually opens.
 *
 * It did not exist. `joinasr.io/w/<code>` was the link in every invitation
 * the app has ever sent, the target of an autoVerify App Link in the
 * manifest, and nothing was serving the apex at all — so the link produced
 * no preview in WhatsApp, no page for anybody without the app, and no
 * assetlinks.json for Android to verify against. Three symptoms of one
 * missing thing.
 *
 * Server-rendered on purpose. WhatsApp, iMessage and Signal fetch the URL
 * with a crawler that runs no JavaScript and gives up quickly: whatever the
 * preview is going to say has to be in the first response.
 */

const SITE = () => (process.env.PUBLIC_SITE_URL ?? "https://joinasr.io").replace(/\/$/, "");

/**
 * The Play listing, with the invitation attached.
 *
 * `referrer` survives the install: Play hands the string back to the app on
 * first launch through the Install Referrer API, and PendingInvite reads
 * `w=<code>` out of it. That is what closes the gap between tapping a link
 * on a phone with no Asr on it and answering the invitation — without it
 * the app opens on a welcome screen and the person has to go back and find
 * the message again.
 *
 * Until the app is published this link answers "item not found". That is a
 * known state and not a bug: the page is otherwise finished, and the day
 * the listing exists nothing here changes.
 */
function playUrl(code: string): string {
  const pkg = process.env.PLAY_PACKAGE_NAME || "io.joinasr.app";
  const referrer = encodeURIComponent(`w=${code}`);
  return `https://play.google.com/store/apps/details?id=${pkg}&referrer=${referrer}`;
}

const RELATIONSHIP_LABEL: Record<string, string> = {
  mother: "their mother",
  father: "their father",
  brother: "their brother",
  sister: "their sister",
  husband: "their husband",
  wife: "their wife",
  friend: "their friend",
  mentor: "their mentor",
  colleague: "their colleague",
  parent: "their parent",
  sibling: "their sibling",
  spouse: "their spouse",
  partner: "their partner",
  other: "someone they trust",
};

type Invite = Awaited<ReturnType<typeof peekInvite>>;

async function load(code: string): Promise<Invite | null> {
  try {
    return await peekInvite(code);
  } catch {
    // peekInvite throws for a code that is malformed, unknown, already
    // answered, or belongs to a deleted account. All four are the same
    // thing to whoever opened the link.
    return null;
  }
}

function sentence(invite: Invite): string {
  const days = invite.days ? `${invite.days}-day ` : "";
  const p = pronounsFor(invite.gender);
  return `${invite.inviter_name} is starting a ${days}challenge to cut down ${p.their} screen time, and wants you to hold ${p.them} to it.`;
}

export async function generateMetadata(
  { params }: { params: Promise<{ code: string }> },
): Promise<Metadata> {
  const { code } = await params;
  const invite = await load(code);
  if (!invite) {
    return { title: "Asr", description: "Screen time you actually commit to." };
  }

  // "his witness", not "their witness". The profile holds the gender and
  // this is the first thing anybody sees of this product -- a preview card
  // in a chat, about somebody the reader knows personally.
  const title = `${invite.inviter_name} asked you to be ${pronounsFor(invite.gender).their} witness`;
  const description = sentence(invite);
  // Same origin as this page, which is why it can be built by joining the
  // path the API stored: the apex and the API are the same application
  // behind two names, so the photo needs no second hostname and no second
  // certificate to be fetchable by a crawler.
  const image = invite.inviter_image ? `${SITE()}${invite.inviter_image}` : undefined;

  return {
    title,
    description,
    openGraph: {
      title,
      description,
      url: `${SITE()}/w/${code}`,
      siteName: "Asr",
      type: "website",
      ...(image ? { images: [{ url: image, width: 512, height: 512, alt: invite.inviter_name }] } : {}),
    },
    twitter: {
      card: image ? "summary" : "summary",
      title,
      description,
      ...(image ? { images: [image] } : {}),
    },
    // An invitation is addressed to one person and the code is the only
    // thing guarding it.
    robots: { index: false, follow: false },
  };
}

export default async function InvitePage({ params }: { params: Promise<{ code: string }> }) {
  const { code } = await params;
  const invite = await load(code);
  if (!invite) notFound();

  const them = pronounsFor(invite.gender);
  const relationship = invite.relationship
    ? RELATIONSHIP_LABEL[invite.relationship] ?? "someone they trust"
    : "someone they trust";
  const initial = invite.inviter_name.trim().slice(0, 1).toUpperCase() || "?";
  const play = playUrl(code);

  return (
    <main style={S.page}>
      <div style={S.card}>
        <p style={S.eyebrow}>ASR · WITNESS INVITATION</p>

        {invite.inviter_image ? (
          /* eslint-disable-next-line @next/next/no-img-element --
             next/image optimises on request through a loader; this photo is
             already a square JPEG under 1024px because the phone sized it
             before uploading, and one <img> needs no second pipeline. */
          <img src={invite.inviter_image} alt={invite.inviter_name} style={S.photo} />
        ) : (
          <div style={{ ...S.photo, ...S.initial }}>{initial}</div>
        )}

        <h1 style={S.name}>{invite.inviter_name}</h1>
        <p style={S.lead}>
          asked you, as {relationship}, to be {them.their} witness
          {invite.days ? ` for a ${invite.days}-day challenge` : ""}.
        </p>

        <div style={S.what}>
          <p style={S.whatTitle}>What that means</p>
          <p style={S.whatBody}>
            You are told when the challenge starts, when it finishes, and if {them.they}{" "}
            {them.has} broken the limits {them.they} set. You are not shown what {them.they}{" "}
            {them.does} on {them.their} phone — only whether the promise was kept.
          </p>
        </div>

        <div style={S.accept}>
          {/* You are reading this because the app is not on this phone: with
              Asr installed the link opens it and never reaches here. So the
              install is the only thing to offer, and the code rides along
              with it. */}
          <a href={play} style={S.cta}>
            Install Asr to accept
          </a>
          <p style={S.acceptBody}>
            Asr opens on this invitation once it is installed. Nothing is shared until you accept,
            and declining tells them nothing beyond that you said no.
          </p>
        </div>
      </div>
    </main>
  );
}

// Inline, because this is the only page on this hostname and a stylesheet
// for it would be one more file to keep in step with the app's palette.
const S: Record<string, React.CSSProperties> = {
  page: {
    minHeight: "100vh",
    margin: 0,
    background: "#0A0A0A",
    color: "#F5F5F2",
    fontFamily:
      "system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    padding: "24px",
    boxSizing: "border-box",
  },
  card: {
    width: "100%",
    maxWidth: 420,
    background: "#0E1110",
    border: "1px solid #212926",
    borderRadius: 24,
    padding: "32px 26px",
    textAlign: "center",
  },
  eyebrow: {
    margin: "0 0 22px",
    color: "#12B886",
    fontSize: 11,
    letterSpacing: "0.14em",
    fontWeight: 600,
  },
  photo: {
    width: 92,
    height: 92,
    borderRadius: "50%",
    objectFit: "cover",
    border: "1px solid #212926",
    background: "#0E0E0E",
    display: "block",
    margin: "0 auto",
  },
  initial: {
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    color: "#12B886",
    fontSize: 34,
    fontWeight: 700,
  },
  name: { margin: "20px 0 8px", fontSize: 28, fontWeight: 700, lineHeight: 1.15 },
  lead: { margin: "0 0 26px", color: "#9A9F9C", fontSize: 15, lineHeight: 1.5 },
  what: {
    background: "#0B0D0C",
    border: "1px solid #212926",
    borderRadius: 16,
    padding: 17,
    textAlign: "left",
  },
  whatTitle: { margin: "0 0 8px", fontSize: 14, fontWeight: 600 },
  whatBody: { margin: 0, color: "#9A9F9C", fontSize: 13, lineHeight: 1.6 },
  accept: { marginTop: 22, textAlign: "left" },
  cta: {
    display: "block",
    background: "#12B886",
    color: "#04120C",
    textDecoration: "none",
    textAlign: "center",
    borderRadius: 27,
    padding: "16px 20px",
    fontSize: 16,
    fontWeight: 700,
    marginBottom: 14,
  },
  acceptBody: { margin: 0, color: "#9A9F9C", fontSize: 13, lineHeight: 1.6, textAlign: "center" },

};
