import { publicPageMetadata } from "../site-metadata";
import { privacy } from "@/lib/legal";
import { LegalPage } from "../legal-page";

export const metadata = publicPageMetadata(
  "Privacy Policy — Asr",
  "What Asr collects, what stays on your phone, and what your witnesses are told.",
  "/privacy",
);

/** https://joinasr.io/privacy — the address the Play listing points at. */
export default function PrivacyPage() {
  return <LegalPage document={privacy} other={{ href: "/terms", label: "Terms" }} />;
}
