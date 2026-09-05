import type { Metadata } from "next";
import { privacy } from "@/lib/legal";
import { LegalPage } from "../legal-page";

export const metadata: Metadata = {
  title: "Privacy Policy — Asr",
  description: "What Asr collects, what stays on your phone, and what your witnesses are told.",
};

/** https://joinasr.io/privacy — the address the Play listing points at. */
export default function PrivacyPage() {
  return <LegalPage document={privacy} other={{ href: "/terms", label: "Terms" }} />;
}
