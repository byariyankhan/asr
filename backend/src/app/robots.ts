import type { MetadataRoute } from "next";
import { siteUrl } from "./site-metadata";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      disallow: ["/api/", "/v1/"],
    },
    // Invite/reset/verification pages keep their own noindex metadata.
    // Crawlers must be able to fetch them to see that directive.
    sitemap: new URL("/sitemap.xml", siteUrl).toString(),
  };
}
