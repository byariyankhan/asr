import { createHash, createHmac } from "node:crypto";

/**
 * Cloudflare R2 through its S3-compatible API, signed by hand.
 *
 * `@aws-sdk/client-s3` would do this, and it is several megabytes of
 * dependency plus its own credential-resolution machinery for three
 * operations: put one object, get one object, delete one object. The Play
 * Billing integration signs its own RS256 JWT for the same reason, and this
 * project has twice lost a deploy to a dependency's files not surviving a
 * Next standalone build. node:crypto is already there.
 *
 * The bucket has no public access and nothing here grants any: objects are
 * streamed by /v1/media instead. The photos themselves are public -- that is
 * what a profile picture is, and the invite preview needs one before the
 * viewer has an account -- so this is not a privacy measure. It is so that
 * no URL is ever stored, only a key, and a CDN or a media domain can go in
 * front later without touching a single stored value.
 */

export type R2Config = {
  accountId: string;
  accessKeyId: string;
  secretAccessKey: string;
  bucket: string;
};

/**
 * Null when the credentials are absent, which is a working state: the
 * upload route answers 503 and everything else is unaffected.
 *
 * Trimmed, because these arrive through a .env file on a server and a value
 * pasted with a trailing space or a CR is not a typo anybody can see. The
 * account id becomes a hostname and goes into the signed `host` header, so
 * one invisible character there is not a wrong address — it is a header
 * value the runtime refuses to build, and the upload dies as a TypeError
 * from inside fetch with nothing naming the cause.
 */
export function r2Config(): R2Config | null {
  const accountId = accountIdFrom(process.env.R2_ACCOUNT_ID ?? "");
  const accessKeyId = process.env.R2_ACCESS_KEY_ID?.trim();
  const secretAccessKey = process.env.R2_SECRET_ACCESS_KEY?.trim();
  const bucket = process.env.R2_BUCKET?.trim();
  if (!accountId || !accessKeyId || !secretAccessKey || !bucket) return null;
  return { accountId, accessKeyId, secretAccessKey, bucket };
}

/**
 * The account id out of whatever was pasted.
 *
 * Cloudflare's dashboard does not show the account id on its own next to the
 * R2 keys. It shows the S3 API endpoint —
 * `https://<id>.r2.cloudflarestorage.com/<bucket>` — and copying that whole
 * line into R2_ACCOUNT_ID is the obvious thing to do with it. It is also
 * what happened here: the probe reported `account_id_is_not_a_hex_id` from
 * production, after the same value had spent days failing every upload as an
 * unexplained TypeError from inside fetch.
 *
 * Refusing it would be correct and useless. The id is right there in the
 * string, in the documented position, and taking it is parsing rather than
 * guessing: scheme, credentials, port, path, and the
 * `.cloudflarestorage.com` suffix all come off, including the jurisdiction
 * forms like `<id>.eu.r2.cloudflarestorage.com`.
 *
 * A value that is already the id passes through untouched.
 */
export function accountIdFrom(raw: string): string {
  let value = raw.trim().replace(/^[a-z][a-z0-9+.-]*:\/\//i, "");
  value = value.split(/[/?#]/)[0] ?? "";
  const at = value.lastIndexOf("@");
  if (at >= 0) value = value.slice(at + 1);
  value = value.split(":")[0] ?? "";
  if (/\.cloudflarestorage\.com$/i.test(value)) value = value.slice(0, value.indexOf("."));
  return value;
}

/**
 * What is odd about a value, without saying what the value is.
 *
 * `account_id_is_not_a_hex_id` came back twice: once from the endpoint URL,
 * and once more after that was parsed out — so the value is neither an id
 * nor the documented endpoint, and no further guess from here is worth the
 * round trip it costs. What settles it is which characters are in there and
 * how many, and neither of those is the value.
 *
 * Only the characters outside [a-zA-Z0-9] are listed, deduplicated, with
 * anything unprintable rendered as its code point — a stray CR, a smart
 * quote or a non-breaking space is exactly the kind of thing that is
 * invisible in the file it was pasted into and obvious the moment it is
 * named. The alphanumerics themselves never leave the server.
 */
export function shapeOf(value: string): string[] {
  const odd = [...new Set(Array.from(value).filter((c) => !/[a-zA-Z0-9]/.test(c)))].map((c) => {
    const code = c.codePointAt(0) ?? 0;
    return code >= 0x21 && code <= 0x7e ? c : `U+${code.toString(16).toUpperCase().padStart(4, "0")}`;
  });
  return [`length=${Array.from(value).length}`, `unexpected=[${odd.join(" ")}]`];
}

/**
 * What is wrong with the configuration itself, before a byte is sent.
 *
 * Trimming fixes the ends; it cannot fix a space in the middle, and every
 * one of these values goes somewhere that forbids whitespace — a hostname, a
 * path segment, a header value. Naming the field is the whole diagnosis, and
 * the value is never returned: this is reported on a route that takes no
 * session.
 */
export function configProblem(config: R2Config): string | null {
  const bad = (value: string) => /[^\x21-\x7e]/.test(value);
  if (bad(config.accountId)) return "account_id_has_whitespace";
  if (bad(config.bucket)) return "bucket_has_whitespace";
  if (bad(config.accessKeyId)) return "access_key_id_has_whitespace";
  if (bad(config.secretAccessKey)) return "secret_access_key_has_whitespace";
  if (!/^[a-zA-Z0-9]+$/.test(config.accountId)) return "account_id_is_not_a_hex_id";
  return null;
}

const REGION = "auto"; // R2 has one region and expects this literal.
const SERVICE = "s3";
const UNSIGNED = "UNSIGNED-PAYLOAD";

const sha256Hex = (data: string | Buffer) => createHash("sha256").update(data).digest("hex");
const hmac = (key: Buffer, data: string) => createHmac("sha256", key).update(data).digest();

/**
 * The object key, percent-encoded the way S3 expects for a canonical request:
 * every byte outside the unreserved set, with `/` left alone because it
 * separates path segments.
 */
function encodeKey(key: string): string {
  return key
    .split("/")
    .map((segment) =>
      encodeURIComponent(segment).replace(
        /[!'()*]/g,
        (c) => `%${c.charCodeAt(0).toString(16).toUpperCase()}`,
      ),
    )
    .join("/");
}

type SignedRequest = { url: string; headers: Record<string, string> };

/**
 * SigV4 over a single-header canonical request. Only `host`,
 * `x-amz-content-sha256` and `x-amz-date` are signed; adding more signed
 * headers means every proxy that touches them breaks the signature, and
 * nothing here needs them.
 */
function sign(
  config: R2Config,
  method: "PUT" | "GET" | "DELETE",
  key: string,
  payloadHash: string,
  extraHeaders: Record<string, string> = {},
): SignedRequest {
  const host = `${config.accountId}.r2.cloudflarestorage.com`;
  const canonicalUri = `/${config.bucket}/${encodeKey(key)}`;
  const now = new Date();
  const amzDate = now.toISOString().replace(/[:-]|\.\d{3}/g, "");
  const dateStamp = amzDate.slice(0, 8);

  const signedHeaders = "host;x-amz-content-sha256;x-amz-date";
  const canonicalHeaders =
    `host:${host}\n` + `x-amz-content-sha256:${payloadHash}\n` + `x-amz-date:${amzDate}\n`;
  const canonicalRequest = [
    method,
    canonicalUri,
    "", // no query string
    canonicalHeaders,
    signedHeaders,
    payloadHash,
  ].join("\n");

  const scope = `${dateStamp}/${REGION}/${SERVICE}/aws4_request`;
  const stringToSign = [
    "AWS4-HMAC-SHA256",
    amzDate,
    scope,
    sha256Hex(canonicalRequest),
  ].join("\n");

  const kSigning = signingKey(config.secretAccessKey, dateStamp, REGION, SERVICE);
  const signature = createHmac("sha256", kSigning).update(stringToSign).digest("hex");

  return {
    url: `https://${host}${canonicalUri}`,
    headers: {
      ...extraHeaders,
      host,
      "x-amz-content-sha256": payloadHash,
      "x-amz-date": amzDate,
      authorization:
        `AWS4-HMAC-SHA256 Credential=${config.accessKeyId}/${scope}, ` +
        `SignedHeaders=${signedHeaders}, Signature=${signature}`,
    },
  };
}

export class R2Error extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

export async function putObject(
  config: R2Config,
  key: string,
  body: Buffer,
  contentType: string,
): Promise<void> {
  // The payload hash is required and is what makes the body tamper-evident
  // in transit; it is cheap here because an avatar is measured in kilobytes.
  const signed = sign(config, "PUT", key, sha256Hex(body), {
    "content-type": contentType,
    "content-length": String(body.length),
  });
  const response = await fetch(signed.url, {
    method: "PUT",
    headers: signed.headers,
    body: new Uint8Array(body),
  });
  if (!response.ok) {
    throw new R2Error(response.status, `R2 refused the upload: ${await response.text()}`);
  }
}

export type FetchedObject = { body: ArrayBuffer; contentType: string; etag: string | null };

export async function getObject(config: R2Config, key: string): Promise<FetchedObject | null> {
  const signed = sign(config, "GET", key, UNSIGNED);
  const response = await fetch(signed.url, { method: "GET", headers: signed.headers });
  if (response.status === 404) return null;
  if (!response.ok) {
    throw new R2Error(response.status, `R2 refused the read: ${await response.text()}`);
  }
  return {
    body: await response.arrayBuffer(),
    contentType: response.headers.get("content-type") ?? "application/octet-stream",
    etag: response.headers.get("etag"),
  };
}

/** Deleting an object that is not there is a success, as it is in S3. */
export async function deleteObject(config: R2Config, key: string): Promise<void> {
  const signed = sign(config, "DELETE", key, UNSIGNED);
  const response = await fetch(signed.url, { method: "DELETE", headers: signed.headers });
  if (!response.ok && response.status !== 404) {
    throw new R2Error(response.status, `R2 refused the delete: ${await response.text()}`);
  }
}

/**
 * The four-step HMAC chain SigV4 derives its signing key with. Split out so
 * a test can check it against the worked example in AWS's own documentation
 * -- the chain is the part of this file most likely to be subtly wrong, and
 * the only part with a published expected answer.
 */
function signingKey(secret: string, dateStamp: string, region: string, service: string): Buffer {
  const kDate = hmac(Buffer.from(`AWS4${secret}`, "utf8"), dateStamp);
  const kRegion = hmac(kDate, region);
  const kService = hmac(kRegion, service);
  return hmac(kService, "aws4_request");
}

/** Exported for the signing tests, which are the only way to check any of
 *  this without real credentials and a network. */
export const __testing = { encodeKey, sign, sha256Hex, signingKey };
