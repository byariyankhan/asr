/**
 * The privacy policy and the terms, as the web pages show them.
 *
 * The same words live in the app, in
 * android/app/src/main/java/io/joinasr/app/legal/LegalTexts.kt, because a
 * person reads them in one place and a store reviewer in the other, and the
 * two must not disagree. `legal.test.ts` parses the Kotlin file and fails
 * when they do: change the text here and there together, or not at all.
 *
 * The blanks the documents flag -- the operating legal entity, a public
 * contact address, governing law -- are the founder's to fill in and are
 * left as gaps rather than invented.
 */
export type LegalSection = { heading: string; body: string };

export type LegalDocument = {
  eyebrow: string;
  title: string;
  /** One sentence over the document; web only. */
  lede: string;
  effective: string;
  sections: LegalSection[];
};

export const EFFECTIVE = "Effective September 3, 2026";

export const privacy: LegalDocument = {
  eyebrow: "PRIVACY",
  title: "Privacy Policy",
  lede:
    "Asr is built around one boundary: what the app measures stays on your phone. The server keeps only what it takes to run your account, your challenge, and the people you asked to watch it.",
  effective: EFFECTIVE,
  sections: [
    {
      heading: "1. What we collect",
      body:
        "Account information: email, full name, profile photo, date of birth, country and gender.\n\n" +
        "Challenge information: selected apps, daily limits, challenge duration, challenge status, breach events and earned-time activity records.\n\n" +
        "Device and protection information: Usage Access status, display-over-other-apps status, notification permission status, selected-app usage duration and foreground usage events needed to enforce limits.",
    },
    {
      heading: "2. App usage and blocking",
      body:
        "Usage Access is used to measure how long each app you selected has been in the foreground, to enforce your limits and to show your progress. The \"display over other apps\" permission is used to put the block screen in front of a selected app once its limit is reached.\n\n" +
        "This app reads the package name of the app in the foreground and how long it was there. It does not read messages, passwords, typed text, photos or anything shown inside another app, and it does not use Android's accessibility service.\n\n" +
        "What the app measures stays on your phone. The server receives the apps you chose to limit, their limits, the daily minutes for each of them, and the moments a limit was reached. It never receives the list of other apps on your phone, or when you opened what.",
    },
    {
      heading: "3. Activity rewards",
      body:
        "If you choose a walking reward, we may process step-count or motion data during the activity to verify completion. Step-based walking rewards do not require GPS or location access.",
    },
    {
      heading: "4. Accountability features",
      body:
        "If you add witnesses, we store the relationship you selected, invite status and witness reactions. If your pact is breached, selected challenge information such as the breached app, breach status and time may be shared with your active witnesses. We do not share your messages, passwords or unrelated app content.",
    },
    {
      heading: "5. How we use data",
      body:
        "We use data to create and secure your account, enforce app limits, operate challenges, calculate progress, grant earned time, deliver protection alerts, power witness notifications and improve reliability.",
    },
    {
      heading: "6. Sharing",
      body:
        "We use service providers to run the service: hosting, sign-in, push notifications (Google Firebase), email (Resend) and profile-photo storage (Cloudflare R2). There is no analytics or advertising provider in the app. Witnesses receive only the accountability information described above. We do not sell personal data to advertisers.",
    },
    {
      heading: "7. Retention and deletion",
      body:
        "We retain information while your account is active and as needed for security, legal obligations and challenge history. You can request deletion from Personal details, then Delete account & data. Deletion removes the account and data that we are not required to keep.",
    },
    {
      heading: "8. Security and changes",
      body:
        "We use reasonable technical and organizational safeguards to protect data. No system is perfectly secure. We may update this policy as the service changes and will publish a new effective date.",
    },
    {
      heading: "9. Contact",
      body:
        "For privacy questions, use Help & Support inside the app. The published release should also include the operator's legal name and a public contact method.",
    },
  ],
};

export const terms: LegalDocument = {
  eyebrow: "LEGAL",
  title: "Terms of Service",
  lede: "The plain rules for using Asr: your account, your challenge, your witnesses, and what the service can and cannot promise.",
  effective: EFFECTIVE,
  sections: [
    {
      heading: "1. Your account",
      body:
        "You must provide accurate account information and keep your password secure. You are responsible for activity performed through your account unless you report unauthorized access.",
    },
    {
      heading: "2. Challenges and locked rules",
      body:
        "When you start a challenge, the selected duration, controlled apps, daily limits and active witnesses may be locked until the challenge ends. A breach may be recorded when a controlled app exceeds its rule or when required protection is lost under the challenge rules shown before you start.",
    },
    {
      heading: "3. Device permissions and protection",
      body:
        "App blocking depends on operating-system permissions: Usage Access, and permission to display over other apps. If either is disabled, blocking becomes unavailable and the interruption may be recorded. The service cannot prevent every operating-system action, including uninstalling or disabling the app.",
    },
    {
      heading: "4. Earned time",
      body:
        "Earned time is an in-app allowance for a specific controlled app and day. It has no cash value, cannot be transferred, and may require completion of a supported activity such as walking or a focus session.",
    },
    {
      heading: "5. Witnesses and reactions",
      body:
        "You may invite people you know as witnesses. You are responsible for choosing recipients and relationship labels. Witnesses may receive challenge events and send reactions. Do not use the service to harass, threaten or humiliate others.",
    },
    {
      heading: "6. Acceptable use",
      body:
        "Do not misuse the service, interfere with security, attempt unauthorized access, automate abuse, impersonate others or use the app in violation of applicable law.",
    },
    {
      heading: "7. Service availability",
      body:
        "Features can depend on device model, Android version, background restrictions and third-party infrastructure. We may modify, suspend or discontinue features when necessary for security, compliance or product changes.",
    },
    {
      heading: "8. Wellness disclaimer",
      body:
        "The app is a self-management and digital-wellbeing tool. It is not medical treatment, diagnosis or professional healthcare advice.",
    },
    {
      heading: "9. Account deletion and termination",
      body:
        "You can request account deletion through Personal details. We may restrict or terminate accounts that materially violate these Terms, abuse the service or create security risk.",
    },
    {
      heading: "10. Changes and legal details",
      body:
        "We may update these Terms and publish a new effective date. Before public release, the published Terms should identify the operating legal entity, contact details, governing law and any region-specific consumer rights that apply.",
    },
  ],
};
