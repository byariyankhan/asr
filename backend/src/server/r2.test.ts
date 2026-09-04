import { describe, expect, it } from "vitest";
import { __testing, configProblem, r2Config } from "./r2";

const { encodeKey, sign, signingKey } = __testing;

const config = {
  accountId: "acc123",
  accessKeyId: "AKIDEXAMPLE",
  secretAccessKey: "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY",
  bucket: "asr-media",
};

describe("signingKey", () => {
  it("matches the worked example in AWS's own documentation", () => {
    // The one published expected answer for this chain. If the HMAC order,
    // the AWS4 prefix or the aws4_request terminator were wrong, everything
    // would still look plausible and R2 would simply answer 403 forever.
    const key = signingKey(
      "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY",
      "20120215",
      "us-east-1",
      "iam",
    );
    expect(key.toString("hex")).toBe(
      "f4780e2d9f65fa895f9c67b32ce1baf0b0d8a43505a000a1a9e090d414db404d",
    );
  });

  it("changes when any input changes", () => {
    const base = signingKey("secret", "20260903", "auto", "s3").toString("hex");
    expect(signingKey("secret2", "20260903", "auto", "s3").toString("hex")).not.toBe(base);
    expect(signingKey("secret", "20260904", "auto", "s3").toString("hex")).not.toBe(base);
    expect(signingKey("secret", "20260903", "us-east-1", "s3").toString("hex")).not.toBe(base);
    expect(signingKey("secret", "20260903", "auto", "iam").toString("hex")).not.toBe(base);
  });
});

describe("encodeKey", () => {
  it("leaves slashes alone, because they are the path", () => {
    expect(encodeKey("avatars/abc123.jpg")).toBe("avatars/abc123.jpg");
  });

  it("encodes what S3's canonical request requires", () => {
    expect(encodeKey("avatars/a b.jpg")).toBe("avatars/a%20b.jpg");
    expect(encodeKey("avatars/it's(1).jpg")).toBe("avatars/it%27s%281%29.jpg");
    expect(encodeKey("avatars/নাম.jpg")).toBe(
      "avatars/%E0%A6%A8%E0%A6%BE%E0%A6%AE.jpg",
    );
  });
});

describe("sign", () => {
  it("addresses the bucket and key on the account's R2 host", () => {
    const { url } = sign(config, "GET", "avatars/x.jpg", "UNSIGNED-PAYLOAD");
    expect(url).toBe("https://acc123.r2.cloudflarestorage.com/asr-media/avatars/x.jpg");
  });

  it("signs exactly the three headers it sends", () => {
    const { headers } = sign(config, "PUT", "avatars/x.jpg", "abc");
    expect(headers.authorization).toContain("SignedHeaders=host;x-amz-content-sha256;x-amz-date");
    expect(headers["x-amz-content-sha256"]).toBe("abc");
    expect(headers["x-amz-date"]).toMatch(/^\d{8}T\d{6}Z$/);
    expect(headers.host).toBe("acc123.r2.cloudflarestorage.com");
  });

  it("scopes the credential to the date, auto region and s3", () => {
    const { headers } = sign(config, "GET", "avatars/x.jpg", "UNSIGNED-PAYLOAD");
    expect(headers.authorization).toMatch(
      /Credential=AKIDEXAMPLE\/\d{8}\/auto\/s3\/aws4_request/,
    );
  });

  it("produces a different signature for a different method, key or payload", () => {
    const sig = (r: { headers: Record<string, string> }) =>
      /Signature=([0-9a-f]+)/.exec(r.headers.authorization ?? "")?.[1];
    const a = sig(sign(config, "GET", "avatars/x.jpg", "hash1"));
    expect(sig(sign(config, "DELETE", "avatars/x.jpg", "hash1"))).not.toBe(a);
    expect(sig(sign(config, "GET", "avatars/y.jpg", "hash1"))).not.toBe(a);
    expect(sig(sign(config, "GET", "avatars/x.jpg", "hash2"))).not.toBe(a);
  });

  it("passes content-type through without signing it", () => {
    // Signed headers are the three above and no more: a proxy that rewrites
    // content-type would otherwise invalidate every upload.
    const { headers } = sign(config, "PUT", "k", "h", { "content-type": "image/jpeg" });
    expect(headers["content-type"]).toBe("image/jpeg");
    expect(headers.authorization).not.toContain("content-type");
  });
});

describe("r2Config", () => {
  it("is null unless every value is present", () => {
    const saved = { ...process.env };
    try {
      for (const k of ["R2_ACCOUNT_ID", "R2_ACCESS_KEY_ID", "R2_SECRET_ACCESS_KEY", "R2_BUCKET"]) {
        delete process.env[k];
      }
      expect(r2Config()).toBeNull();

      process.env.R2_ACCOUNT_ID = "a";
      process.env.R2_ACCESS_KEY_ID = "b";
      process.env.R2_SECRET_ACCESS_KEY = "c";
      // Still missing the bucket: a partial configuration is not a
      // configuration, and half-signing an upload fails at R2 with a 403
      // that says nothing useful.
      expect(r2Config()).toBeNull();

      process.env.R2_BUCKET = "asr-media";
      expect(r2Config()).toEqual({
        accountId: "a",
        accessKeyId: "b",
        secretAccessKey: "c",
        bucket: "asr-media",
      });
    } finally {
      process.env = saved;
    }
  });
});

describe("configuration", () => {
  it("trims what a .env file leaves on the ends", () => {
    const saved = process.env;
    try {
      process.env = { ...saved };
      // A trailing CR is what an .env edited on Windows leaves behind, and
      // it is invisible in every tool that shows the file. It reaches the
      // `host` header, which the runtime then refuses to build — the upload
      // dies as a bare TypeError from inside fetch.
      process.env.R2_ACCOUNT_ID = "acc123\r";
      process.env.R2_ACCESS_KEY_ID = " AKIDEXAMPLE ";
      process.env.R2_SECRET_ACCESS_KEY = "secret\n";
      process.env.R2_BUCKET = "asr-media ";
      expect(r2Config()).toEqual({
        accountId: "acc123",
        accessKeyId: "AKIDEXAMPLE",
        secretAccessKey: "secret",
        bucket: "asr-media",
      });
    } finally {
      process.env = saved;
    }
  });

  it("names the field when trimming cannot help", () => {
    expect(configProblem(config)).toBeNull();
    expect(configProblem({ ...config, accountId: "acc 123" })).toBe("account_id_has_whitespace");
    expect(configProblem({ ...config, bucket: "asr media" })).toBe("bucket_has_whitespace");
    expect(configProblem({ ...config, accessKeyId: "AK\tID" })).toBe(
      "access_key_id_has_whitespace",
    );
    expect(configProblem({ ...config, secretAccessKey: "a\u00a0b" })).toBe(
      "secret_access_key_has_whitespace",
    );
    // An account id is a hex string. Anything with punctuation in it is a
    // whole URL or an endpoint pasted where the id belongs, which produces a
    // hostname nobody would recognise as wrong at a glance.
    expect(configProblem({ ...config, accountId: "acc.123" })).toBe("account_id_is_not_a_hex_id");
  });
});
