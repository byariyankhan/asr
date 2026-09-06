import { publicPageMetadata } from "../site-metadata";
import type { LegalDocument } from "@/lib/legal";
import { LegalPage } from "../legal-page";

export const metadata = publicPageMetadata(
  "Delete your account — Asr",
  "How to delete your Asr account and everything in it, from the app or by email.",
  "/delete-account",
);

/**
 * https://joinasr.io/delete-account — the address Google Play's Data safety
 * form asks for: where somebody can have their account deleted, including
 * somebody who no longer has the app. The same deletion the app offers
 * (server/account.ts), described once, with the way in for people without a
 * phone to do it from.
 */
const deletion: LegalDocument = {
  eyebrow: "Your account",
  title: "Delete your account",
  lede: "Everything in it goes with it: your profile and photo, your challenges and their history, your witnesses, your notifications.",
  effective: "Updated September 6, 2026",
  sections: [
    {
      heading: "From the app",
      body:
        "Open Asr, go to Profile, then Personal details, then Delete account & data, and type your password. Your account is scheduled for deletion, every phone is signed out, and your witnesses are told that you left. Seven days later the account and everything in it are deleted for good. If you sign in again within those seven days, the deletion is cancelled and nothing is lost.",
    },
    {
      heading: "Without the app",
      body:
        "If you no longer have the app or a phone to run it on, email hi@ariyankhan.com from the address on your account with the subject \"Delete my account\". We reply to that address to confirm it is you, then schedule the same deletion, with the same seven days to change your mind. Requests are handled within seven days of the confirmation.",
    },
    {
      heading: "What is deleted",
      body:
        "Your account, name, email address, date of birth, country and gender; your profile photo; every challenge, its limits and its ledger of events; your witness invitations and connections, on both sides; your notifications and reactions; your devices' registrations. Your witnesses keep the notifications they were already sent, the same way a message you sent stays in the inbox it was sent to.",
    },
    {
      heading: "What is not kept",
      body:
        "Nothing that identifies you. Product analytics and crash reports carry no name, email or account id, only a random installation identifier that is not linked to your account. Server request logs, which hold an IP address and a URL, are rotated away within a few weeks. Backups of the database expire on their own schedule, within weeks, and are never restored to bring back a deleted account.",
    },
    {
      heading: "Deleting some things without deleting the account",
      body:
        "You can remove your profile photo and change your details in Personal details, remove a witness from Witnesses, and end a challenge from the dashboard. Your app usage never leaves your phone, so there is nothing of it on our servers to delete. For anything else, email hi@ariyankhan.com.",
    },
  ],
};

export default function DeleteAccountPage() {
  return <LegalPage document={deletion} other={{ href: "/privacy", label: "Privacy" }} />;
}
