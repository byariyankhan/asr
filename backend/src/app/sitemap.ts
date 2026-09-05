import type { MetadataRoute } from "next";
import { siteUrl } from "./site-metadata";

export default function sitemap(): MetadataRoute.Sitemap {
  // Only public editorial pages; never invite codes, tokens or account data.
  return ["/", "/privacy", "/terms"].map((path) => ({
    url: new URL(path, siteUrl).toString(),
  }));
}
