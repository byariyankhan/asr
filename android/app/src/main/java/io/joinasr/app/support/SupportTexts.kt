package io.joinasr.app.support

/** One question and its answer. */
data class SupportAnswer(val question: String, val answer: String)

/**
 * What people ask, and where to write when the answer is not here.
 *
 * Kept out of the screen for the same reason the legal texts are: this is
 * copy, it will be rewritten more often than the layout, and a paragraph
 * buried in a Composable is a paragraph nobody edits.
 *
 * The questions are the ones this app actually provokes. Three of them are
 * about things Asr does that look like faults until they are explained --
 * the permanent notification, the two permissions, and the fact that limits
 * cannot be edited mid-challenge -- and a support page that does not answer
 * those is a support page that receives them all by email.
 */
object SupportTexts {

    const val EMAIL = "hi@ariyankhan.com"

    val questions = listOf(
        SupportAnswer(
            "Why is there always an Asr notification?",
            "Android will not let an app watch which app is in front of you unless it is " +
                "running in the foreground, and it will not let anything run in the foreground " +
                "without a notification. So the notification is the price of the limits working " +
                "while your phone is asleep. It is set to the quietest level Android allows: no " +
                "sound, no status bar icon, and no place in your notification list except the " +
                "silent section at the bottom. You can hide it altogether from Profile → App " +
                "permissions; your limits, the block screen and your witnesses are unaffected.",
        ),
        SupportAnswer(
            "Why does the Asr notification come back after I swipe it away?",
            "Because your phone stopped Asr and Asr started again. Swiping it away does not " +
                "stop anything — the notification and the protection are the same thing to " +
                "Android — so it stays gone until something restarts the service, which is " +
                "usually your phone killing it to save battery. Seeing it reappear often means " +
                "your limits are being switched off often. The fix is Background activity, under " +
                "Profile → App permissions. To be rid of the notification itself, hide it there " +
                "instead of swiping.",
        ),
        SupportAnswer(
            "Why does Asr need Usage access and Display over other apps?",
            "Usage access is how it knows which app is open and for how long today. Display " +
                "over other apps is how the block screen appears over the app you have run out " +
                "of time on. Nothing else uses either, and neither can read what you type, what " +
                "you look at, or anything inside another app.",
        ),
        SupportAnswer(
            "Can I change my limits after a challenge starts?",
            "No, and that is the point. A limit you can raise at the moment it stops you is not " +
                "a limit. If you need to stop, end the challenge — your witnesses are told, and " +
                "you can start a new one with different limits straight away.",
        ),
        SupportAnswer(
            "What do my witnesses actually see?",
            "The apps you limited and for how long each day, how far into the challenge you " +
                "are, and whether a limit held. Nothing else: not your messages, not your " +
                "browsing, not what you do inside any app. They see the challenge you invited " +
                "them to and nothing before or after it.",
        ),
        SupportAnswer(
            "How do I get more time?",
            "Earn it. A walk or a focus session adds minutes to today's allowance for the app " +
                "that stopped you. Tomorrow's limit is the one you committed to, unchanged — " +
                "earning time is not editing the promise.",
        ),
        SupportAnswer(
            "What happens if I uninstall Asr mid-challenge?",
            "Your witnesses are told the app was removed and the challenge is recorded as " +
                "broken. If you want to stop, ending the challenge from inside the app is the " +
                "same outcome with an honest message attached.",
        ),
        SupportAnswer(
            "Can I remove a witness?",
            "A witness stays for the challenge they accepted — that is what makes naming them " +
                "mean something. They are not carried into your next challenge, so every " +
                "challenge starts with whoever you invite to it.",
        ),
        SupportAnswer(
            "How do I delete my account?",
            "Profile → Personal details → Delete account. It removes your challenges, your " +
                "witnesses and your photo. Anyone you were a witness for stops seeing you.",
        ),
    )
}
