import { timingSafeEqual } from "node:crypto";
import { NextResponse } from "next/server";
import { readJson, route } from "@/lib/http";
import { refreshPurchase } from "@/server/subscriptions";

// Google Play Real-time Developer Notifications, delivered by a Pub/Sub
// push subscription. Pub/Sub authenticates itself with the shared secret in
// the push endpoint's query string (`?token=...`), which is why this route
// must only ever be registered over HTTPS.
//
// Pub/Sub retries any non-2xx, so the rules are: reject an unauthenticated
// call outright, answer 200 for anything we cannot act on (an unknown
// token, a test notification) so it is not redelivered forever, and answer
// 500 only when a retry could actually help.
type PubSubEnvelope = {
  message?: { data?: string; messageId?: string };
};

type Rtdn = {
  packageName?: string;
  subscriptionNotification?: { purchaseToken?: string; notificationType?: number };
  voidedPurchaseNotification?: { purchaseToken?: string };
  testNotification?: { version?: string };
};

function authorized(request: Request): boolean {
  const expected = process.env.PLAY_PUBSUB_SECRET;
  if (!expected) return false;
  const given = new URL(request.url).searchParams.get("token") ?? "";
  const a = Buffer.from(expected);
  const b = Buffer.from(given);
  return a.length === b.length && timingSafeEqual(a, b);
}

export const POST = route(async (request) => {
  if (!authorized(request)) {
    return NextResponse.json({ error: "not_found", message: "Not found." }, { status: 404 });
  }

  const envelope = (await readJson(request)) as PubSubEnvelope;
  const encoded = envelope.message?.data;
  if (!encoded) return NextResponse.json({ ok: true, ignored: "no data" });

  let notification: Rtdn;
  try {
    notification = JSON.parse(Buffer.from(encoded, "base64").toString("utf8")) as Rtdn;
  } catch {
    console.error("[play webhook] message data was not JSON");
    return NextResponse.json({ ok: true, ignored: "unparseable" });
  }

  if (notification.testNotification) return NextResponse.json({ ok: true, test: true });

  const purchaseToken =
    notification.subscriptionNotification?.purchaseToken ?? notification.voidedPurchaseNotification?.purchaseToken;
  if (!purchaseToken) return NextResponse.json({ ok: true, ignored: "no purchase token" });

  // Failures here are worth a retry: the state we hold is now stale.
  const known = await refreshPurchase(purchaseToken);
  return NextResponse.json({ ok: true, known });
});
