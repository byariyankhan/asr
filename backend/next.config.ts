import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Required for the Docker runner stage, which copies .next/standalone.
  output: "standalone",
  poweredByHeader: false,
};

export default nextConfig;
