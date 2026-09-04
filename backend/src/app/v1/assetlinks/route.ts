import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

/**
 * Digital Asset Links, served at /.well-known/assetlinks.json by a rewrite
 * in next.config.ts.
 *
 * This is what makes `android:autoVerify="true"` mean anything. Android
 * fetches it when the app is installed, and only if this file names the
 * app's signing certificate does a tap on joinasr.io/w/<code> open Asr
 * directly. Without it the link opens a browser, or asks which app to use —
 * which is not what somebody who has just installed the app to answer an
 * invitation should be asked.
 *
 * The fingerprints come from the environment because they are not one
 * value: a debug build signed on a laptop, the CI's debug key, and the key
 * Play signs releases with are three different certificates, and a phone
 * only verifies against the one that signed the app it has. All of them can
 * be listed; none of them is a secret — a fingerprint is a public hash of a
 * public certificate, which is why this file is world-readable by design.
 *
 * 404 when unset, rather than an empty list: an empty list is a valid file
 * that says "no app may claim these links", and Android caches it.
 */
const FINGERPRINT = /^[0-9A-F]{2}(:[0-9A-F]{2}){31}$/;

export async function GET() {
  const pkg = process.env.PLAY_PACKAGE_NAME?.trim() || "io.joinasr.app";
  const fingerprints = (process.env.ANDROID_CERT_SHA256 ?? "")
    .split(/[,\s]+/)
    .map((value) => value.trim().toUpperCase())
    .filter((value) => FINGERPRINT.test(value));

  if (fingerprints.length === 0) {
    // Named, because the symptom otherwise is "the link opens a chooser"
    // and nothing anywhere connects that to a missing variable.
    console.warn("[assetlinks] ANDROID_CERT_SHA256 is unset or malformed; App Links will not verify");
    return new NextResponse("Not found", { status: 404 });
  }

  return NextResponse.json(
    [
      {
        relation: ["delegate_permission/common.handle_all_urls"],
        target: {
          namespace: "android_app",
          package_name: pkg,
          sha256_cert_fingerprints: fingerprints,
        },
      },
    ],
    {
      headers: {
        "content-type": "application/json",
        // Android re-checks on install and on app updates. An hour is short
        // enough that adding the release fingerprint takes effect the same
        // day, long enough that it is not fetched on every install.
        "cache-control": "public, max-age=3600",
      },
    },
  );
}
