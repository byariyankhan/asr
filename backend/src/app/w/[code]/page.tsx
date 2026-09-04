import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { peekInvite } from "@/server/witnesses";

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
 * The Play listing, once there is one.
 *
 * Behind a flag because a button linking to a listing that does not exist
 * is worse than no button: it would be the one action the page offers, and
 * it would land on a Play page saying the app was not found. Set
 * PLAY_LISTING_LIVE=true in .env the day the app is published.
 */
function playUrl(): string | null {
  if (process.env.PLAY_LISTING_LIVE !== "true") return null;
  const pkg = process.env.PLAY_PACKAGE_NAME || "io.joinasr.app";
  return `https://play.google.com/store/apps/details?id=${pkg}`;
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
  return `${invite.inviter_name} is starting a ${days}challenge to cut down their screen time, and wants you to hold them to it.`;
}

export async function generateMetadata(
  { params }: { params: Promise<{ code: string }> },
): Promise<Metadata> {
  const { code } = await params;
  const invite = await load(code);
  if (!invite) {
    return { title: "Asr", description: "Screen time you actually commit to." };
  }

  const title = `${invite.inviter_name} asked you to be their witness`;
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

  const relationship = invite.relationship
    ? RELATIONSHIP_LABEL[invite.relationship] ?? "someone they trust"
    : "someone they trust";
  const initial = invite.inviter_name.trim().slice(0, 1).toUpperCase() || "?";
  const play = playUrl();

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
          asked you, as {relationship}, to be their witness
          {invite.days ? ` for a ${invite.days}-day challenge` : ""}.
        </p>

        <div style={S.what}>
          <p style={S.whatTitle}>What that means</p>
          <p style={S.whatBody}>
            You are told when they start, when they finish, and if they break the limits they set
            for themselves. You are not shown what they do on their phone — only whether they kept
            the promise they made.
          </p>
        </div>

        <div style={S.accept}>
          <p style={S.acceptTitle}>To accept</p>
          <a href={`${SITE()}/w/${code}`} style={S.cta}>
            Open in Asr
          </a>
          <p style={S.acceptBody}>
            {play
              ? "That opens the app if you have it. If you do not, install Asr and tap the link in the message again — it takes you straight here."
              : "That opens the app if you have it. If you do not, install Asr and tap the link in the message again — it takes you straight here. Nothing is shared until you accept."}
          </p>
          {play ? (
            <a href={play} style={S.secondary}>
              Get Asr on Google Play
            </a>
          ) : null}
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
  acceptTitle: { margin: "0 0 12px", fontSize: 14, fontWeight: 600 },
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
  acceptBody: { margin: "0 0 12px", color: "#9A9F9C", fontSize: 13, lineHeight: 1.6 },
  secondary: {
    display: "block",
    color: "#12B886",
    textDecoration: "none",
    textAlign: "center",
    border: "1px solid #173B2D",
    background: "#071A13",
    borderRadius: 27,
    padding: "14px 20px",
    fontSize: 15,
    fontWeight: 600,
  },
};
