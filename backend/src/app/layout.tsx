import type { ReactNode } from "react";

export const metadata = { title: "Asr" };

// API-only app. The root layout exists because the App Router requires one;
// the landing page and /w/<code> fallback will render inside it later.
export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
