import type { Metadata } from "next";

// Public identity stays on the marketing domain, even when the same pages
// are requested through the API hostname.
export const siteUrl = new URL(process.env.PUBLIC_SITE_URL ?? "https://joinasr.io").origin;
export const siteDescription =
  "Set daily app limits on Android and keep your screen-time commitments with people you trust. Asr combines app blocking with accountability.";

export function publicPageMetadata(title: string, description: string, path: string): Metadata {
  const url = new URL(path, siteUrl).toString();
  const image = {
    url: new URL("/og", siteUrl).toString(),
    width: 1200,
    height: 630,
    alt: "Asr — Android app limits, with people who hold you to them.",
  };
  return {
    title,
    description,
    alternates: { canonical: url },
    openGraph: { title, description, url, siteName: "Asr", type: "website", images: [image] },
    twitter: { card: "summary_large_image", title, description, images: [image.url] },
  };
}
