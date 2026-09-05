import { publicPageMetadata } from "../site-metadata";
import { terms } from "@/lib/legal";
import { LegalPage } from "../legal-page";

export const metadata = publicPageMetadata(
  "Terms of Service — Asr",
  "Plain-language terms for using Asr, the Android app for screen-time limits and accountability.",
  "/terms",
);

export default function TermsPage() {
  return <LegalPage document={terms} other={{ href: "/privacy", label: "Privacy" }} />;
}
