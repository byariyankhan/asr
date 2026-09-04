import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Required for the Docker runner stage, which copies .next/standalone.
  output: "standalone",
  poweredByHeader: false,

  // Android fetches this exact path and no other. It is a rewrite rather
  // than a route file at src/app/.well-known/, because a directory whose
  // name begins with a dot is not something to rely on the App Router
  // picking up.
  async rewrites() {
    return [{ source: "/.well-known/assetlinks.json", destination: "/v1/assetlinks" }];
  },
};

export default nextConfig;
