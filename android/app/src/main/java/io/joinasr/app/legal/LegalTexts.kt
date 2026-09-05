package io.joinasr.app.legal

/** One numbered part of a legal document. */
data class LegalSection(val heading: String, val body: String)

data class LegalDocument(
    val eyebrow: String,
    val title: String,
    val effective: String,
    val sections: List<LegalSection>,
)

/**
 * The privacy policy and terms, verbatim from Figma 36 and 37 except where
 * the frames describe something this app does not do.
 *
 * The same words are served at joinasr.io/privacy and /terms from
 * backend/src/lib/legal.ts, and a test there reads this file and fails when
 * the two differ. Change them together.
 *
 * Two corrections, both in the same place and both deliberate. The frames
 * say blocking works through Android Accessibility. It does not: it works
 * through Usage Access plus a screen this app puts in front of the blocked
 * app, and the app does not declare an accessibility service at all — the
 * reasoning is in docs/ANDROID.md. A privacy policy is the last document in
 * a product that may describe a permission the app does not hold: it is the
 * one a person reads to find out exactly that, and a store reviewer reads it
 * against the manifest.
 *
 * The blanks the frames themselves flagged — the operating legal entity, a
 * public contact address, governing law — are filled in from the founder's
 * own answer: the operator is Ariyan Khan, at the Mirpur address, reachable
 * at hi@ariyankhan.com (the same address Help & Support writes to), and the
 * terms are governed by the law of Bangladesh.
 */
object LegalTexts {

    const val EFFECTIVE = "Effective September 5, 2026"

    val privacy = LegalDocument(
        eyebrow = "PRIVACY",
        title = "Privacy Policy",
        effective = EFFECTIVE,
        sections = listOf(
            LegalSection(
                "1. What we collect",
                "Account information: email, full name, profile photo, date of birth, " +
                    "country and gender.\n\n" +
                    "Challenge information: selected apps, daily limits, challenge " +
                    "duration, challenge status, breach events and earned-time activity " +
                    "records.\n\n" +
                    "Device and protection information: Usage Access status, display-" +
                    "over-other-apps status, notification permission status, " +
                    "selected-app usage duration and foreground usage events needed to " +
                    "enforce limits.",
            ),
            LegalSection(
                "2. App usage and blocking",
                "Usage Access is used to measure how long each app you selected has " +
                    "been in the foreground, to enforce your limits and to show your " +
                    "progress. The \"display over other apps\" permission is used to put " +
                    "the block screen in front of a selected app once its limit is " +
                    "reached.\n\n" +
                    "This app reads the package name of the app in the foreground and " +
                    "how long it was there. It does not read messages, passwords, typed " +
                    "text, photos or anything shown inside another app, and it does not " +
                    "use Android's accessibility service.\n\n" +
                    "What the app measures stays on your phone. The server receives the " +
                    "apps you chose to limit, their limits, the daily minutes for each of " +
                    "them, and the moments a limit was reached. It never receives the " +
                    "list of other apps on your phone, or when you opened what.",
            ),
            LegalSection(
                "3. Activity rewards",
                "If you choose a walking reward, we may process step-count or motion " +
                    "data during the activity to verify completion. Step-based walking " +
                    "rewards do not require GPS or location access.",
            ),
            LegalSection(
                "4. Accountability features",
                "If you add witnesses, we store the relationship you selected, invite " +
                    "status and witness reactions. If your pact is breached, selected " +
                    "challenge information such as the breached app, breach status and " +
                    "time may be shared with your active witnesses. We do not share your " +
                    "messages, passwords or unrelated app content.",
            ),
            LegalSection(
                "5. How we use data",
                "We use data to create and secure your account, enforce app limits, " +
                    "operate challenges, calculate progress, grant earned time, deliver " +
                    "protection alerts, power witness notifications and improve " +
                    "reliability.",
            ),
            LegalSection(
                "6. Sharing",
                "We use service providers to run the service: hosting, sign-in, push " +
                    "notifications, crash reports and product analytics (Google " +
                    "Firebase), email (Resend) and profile-photo storage (Cloudflare R2). " +
                    "A crash report carries the app version, the phone model and Android " +
                    "version, and where in the app the failure happened; never your " +
                    "usage, your witnesses or anything you typed. Analytics receives a " +
                    "small number of product events, such as an account being created, a " +
                    "challenge starting, an invitation going out or a challenge ending, " +
                    "with the app version, the phone model, your country and language and " +
                    "a random identifier for the installation; never the apps you limit, " +
                    "your minutes, your name, your email address or your witnesses. The " +
                    "advertising identifier is switched off. There is no advertising " +
                    "provider in the app. Witnesses receive only the accountability " +
                    "information described above. We do not sell personal data to " +
                    "advertisers.",
            ),
            LegalSection(
                "7. Retention and deletion",
                "We retain information while your account is active and as needed for " +
                    "security, legal obligations and challenge history. You can request " +
                    "deletion from Personal details, then Delete account & data. " +
                    "Deletion removes the account and data that we are not required to " +
                    "keep.",
            ),
            LegalSection(
                "8. Security and changes",
                "We use reasonable technical and organizational safeguards to protect " +
                    "data. No system is perfectly secure. We may update this policy as " +
                    "the service changes and will publish a new effective date.",
            ),
            LegalSection(
                "9. Contact",
                "Asr is operated by Ariyan Khan, House 16, Road S11, Block L, Eastern " +
                    "Housing (Pallabi Phase 2), Rupnagar, Mirpur, Dhaka, Bangladesh. For " +
                    "privacy questions or requests, email hi@ariyankhan.com or use Help & " +
                    "Support inside the app.",
            ),
        ),
    )

    val terms = LegalDocument(
        eyebrow = "LEGAL",
        title = "Terms of Service",
        effective = EFFECTIVE,
        sections = listOf(
            LegalSection(
                "1. Your account",
                "You must provide accurate account information and keep your password " +
                    "secure. You are responsible for activity performed through your " +
                    "account unless you report unauthorized access.",
            ),
            LegalSection(
                "2. Challenges and locked rules",
                "When you start a challenge, the selected duration, controlled apps, " +
                    "daily limits and active witnesses may be locked until the challenge " +
                    "ends. A breach may be recorded when a controlled app exceeds its " +
                    "rule or when required protection is lost under the challenge rules " +
                    "shown before you start.",
            ),
            LegalSection(
                "3. Device permissions and protection",
                "App blocking depends on operating-system permissions: Usage Access, " +
                    "and permission to display over other apps. If either is disabled, " +
                    "blocking becomes unavailable and the interruption may be recorded. " +
                    "The service cannot prevent every operating-system action, including " +
                    "uninstalling or disabling the app.",
            ),
            LegalSection(
                "4. Earned time",
                "Earned time is an in-app allowance for a specific controlled app and " +
                    "day. It has no cash value, cannot be transferred, and may require " +
                    "completion of a supported activity such as walking or a focus " +
                    "session.",
            ),
            LegalSection(
                "5. Witnesses and reactions",
                "You may invite people you know as witnesses. You are responsible for " +
                    "choosing recipients and relationship labels. Witnesses may receive " +
                    "challenge events and send reactions. Do not use the service to " +
                    "harass, threaten or humiliate others.",
            ),
            LegalSection(
                "6. Acceptable use",
                "Do not misuse the service, interfere with security, attempt " +
                    "unauthorized access, automate abuse, impersonate others or use the " +
                    "app in violation of applicable law.",
            ),
            LegalSection(
                "7. Service availability",
                "Features can depend on device model, Android version, background " +
                    "restrictions and third-party infrastructure. We may modify, suspend " +
                    "or discontinue features when necessary for security, compliance or " +
                    "product changes.",
            ),
            LegalSection(
                "8. Wellness disclaimer",
                "The app is a self-management and digital-wellbeing tool. It is not " +
                    "medical treatment, diagnosis or professional healthcare advice.",
            ),
            LegalSection(
                "9. Account deletion and termination",
                "You can request account deletion through Personal details. We may " +
                    "restrict or terminate accounts that materially violate these Terms, " +
                    "abuse the service or create security risk.",
            ),
            LegalSection(
                "10. Changes and legal details",
                "We may update these Terms and publish a new effective date. Asr is " +
                    "operated by Ariyan Khan, House 16, Road S11, Block L, Eastern Housing " +
                    "(Pallabi Phase 2), Rupnagar, Mirpur, Dhaka, Bangladesh; contact " +
                    "hi@ariyankhan.com. These Terms are governed by the laws of " +
                    "Bangladesh, without taking away any consumer rights you have under " +
                    "the laws of the country where you live.",
            ),
        ),
    )
}
