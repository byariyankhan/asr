import { createSign } from "node:crypto";

// Google Play Developer API, subscriptionsv2. Signed with the service
// account directly (RS256 JWT -> access token) rather than pulling in
// googleapis: this is the one endpoint we call, and the whole exchange is
// forty lines against a heavy dependency tree.

const SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const TOKEN_URI = "https://oauth2.googleapis.com/token";
const API = "https://androidpublisher.googleapis.com/androidpublisher/v3";

export type PlaySubscriptionState =
  | "SUBSCRIPTION_STATE_PENDING"
  | "SUBSCRIPTION_STATE_ACTIVE"
  | "SUBSCRIPTION_STATE_PAUSED"
  | "SUBSCRIPTION_STATE_IN_GRACE_PERIOD"
  | "SUBSCRIPTION_STATE_ON_HOLD"
  | "SUBSCRIPTION_STATE_CANCELED"
  | "SUBSCRIPTION_STATE_EXPIRED";

export type PlayPurchase = {
  subscriptionState: PlaySubscriptionState;
  /** Latest expiry across line items; undefined only on a malformed reply. */
  expiresAt: Date | null;
  productId: string | null;
  /** Set when this purchase replaced another (upgrade/downgrade). */
  linkedPurchaseToken: string | null;
  raw: Record<string, unknown>;
};

/** Injectable so tests and the webhook share one seam. */
export type PurchaseVerifier = (purchaseToken: string) => Promise<PlayPurchase>;

type ServiceAccount = { client_email: string; private_key: string; token_uri?: string };

let account: ServiceAccount | null | undefined;

function serviceAccount(): ServiceAccount | null {
  if (account !== undefined) return account;
  const b64 = process.env.PLAY_SERVICE_ACCOUNT_JSON_B64;
  if (!b64) {
    account = null;
    return null;
  }
  try {
    const parsed = JSON.parse(Buffer.from(b64, "base64").toString("utf8")) as ServiceAccount;
    account = parsed.client_email && parsed.private_key ? parsed : null;
  } catch {
    console.error("[play] PLAY_SERVICE_ACCOUNT_JSON_B64 is not valid base64 JSON");
    account = null;
  }
  return account;
}

export function playConfigured(): boolean {
  return serviceAccount() !== null && Boolean(process.env.PLAY_PACKAGE_NAME);
}

const b64url = (input: string | Buffer) =>
  Buffer.from(input).toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

// One token per process, reused until a minute before it expires.
let cached: { token: string; expiresAt: number } | null = null;

async function accessToken(): Promise<string> {
  if (cached && cached.expiresAt > Date.now() + 60_000) return cached.token;
  const sa = serviceAccount();
  if (!sa) throw new Error("play_not_configured");

  const tokenUri = sa.token_uri ?? TOKEN_URI;
  const iat = Math.floor(Date.now() / 1000);
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64url(JSON.stringify({ iss: sa.client_email, scope: SCOPE, aud: tokenUri, iat, exp: iat + 3600 }));
  const signature = b64url(createSign("RSA-SHA256").update(`${header}.${claims}`).sign(sa.private_key.replace(/\\n/g, "\n")));

  const response = await fetch(tokenUri, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${header}.${claims}.${signature}`,
    }),
  });
  if (!response.ok) throw new Error(`play_token_failed: ${response.status} ${await response.text()}`);
  const body = (await response.json()) as { access_token: string; expires_in: number };
  cached = { token: body.access_token, expiresAt: Date.now() + body.expires_in * 1000 };
  return body.access_token;
}

type LineItem = { expiryTime?: string; productId?: string };

export const verifyPurchase: PurchaseVerifier = async (purchaseToken) => {
  const packageName = process.env.PLAY_PACKAGE_NAME;
  if (!packageName) throw new Error("play_not_configured");
  const token = await accessToken();
  const url = `${API}/applications/${encodeURIComponent(packageName)}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`;
  const response = await fetch(url, { headers: { authorization: `Bearer ${token}` } });
  if (response.status === 404 || response.status === 400) throw new Error("play_unknown_token");
  if (!response.ok) throw new Error(`play_lookup_failed: ${response.status} ${await response.text()}`);

  const raw = (await response.json()) as Record<string, unknown>;
  const lineItems = Array.isArray(raw.lineItems) ? (raw.lineItems as LineItem[]) : [];
  const expiries = lineItems
    .map((l) => (l.expiryTime ? new Date(l.expiryTime) : null))
    .filter((d): d is Date => d !== null && !Number.isNaN(d.getTime()));

  return {
    subscriptionState: (raw.subscriptionState as PlaySubscriptionState) ?? "SUBSCRIPTION_STATE_EXPIRED",
    expiresAt: expiries.length > 0 ? new Date(Math.max(...expiries.map((d) => d.getTime()))) : null,
    productId: lineItems.find((l) => l.productId)?.productId ?? null,
    linkedPurchaseToken: typeof raw.linkedPurchaseToken === "string" ? raw.linkedPurchaseToken : null,
    raw,
  };
};
