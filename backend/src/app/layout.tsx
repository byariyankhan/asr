import type { ReactNode } from "react";

export const metadata = { title: "Asr" };

// Mostly an API, plus the pages the links in the product open: /w/<code> is
// the witness invitation, shared through whatever the sender already uses to
// talk to their mother, and it has to render for somebody who has never
// heard of this app.
//
// The margin reset is here rather than in a stylesheet because these pages
// carry their own inline styles and a file of global CSS for one rule is a
// file to keep in step with nothing.
export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body style={{ margin: 0, background: "#0A0A0A" }}>{children}</body>
    </html>
  );
}
