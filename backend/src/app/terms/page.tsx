import type { Metadata } from "next";
import { terms } from "@/lib/legal";
import { LegalPage } from "../legal-page";

export const metadata: Metadata = {
  title: "Terms of Service — Asr",
  description: "Plain-language terms for using Asr.",
};

export default function TermsPage() {
  return <LegalPage document={terms} other={{ href: "/privacy", label: "Privacy" }} />;
}
