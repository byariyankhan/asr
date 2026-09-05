import { ImageResponse } from "next/og";

export const dynamic = "force-static";

export function GET() {
  return new ImageResponse(
    (
      <div style={{
        display: "flex", flexDirection: "column", justifyContent: "space-between",
        width: "100%", height: "100%", padding: "64px 72px",
        background: "#0A0A0A", color: "#F5F5F2",
      }}>
        <div style={{ display: "flex", fontSize: 48, fontWeight: 700 }}>
          Asr<span style={{ color: "#12B886" }}>.</span>
        </div>
        <div style={{ display: "flex", flexDirection: "column", gap: 24 }}>
          <div style={{ fontSize: 72, fontWeight: 700, maxWidth: 1000 }}>
            Make a pact with your time.
          </div>
          <div style={{ fontSize: 30, color: "#9A9F9C" }}>
            Android app limits. Accountability from people you trust.
          </div>
        </div>
        <div style={{ display: "flex", color: "#12B886", fontSize: 24 }}>joinasr.io</div>
      </div>
    ),
    { width: 1200, height: 630 },
  );
}
