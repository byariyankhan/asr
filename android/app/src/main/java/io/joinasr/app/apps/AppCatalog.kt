package io.joinasr.app.apps

/**
 * One app the person could put under a limit, before its icon is loaded.
 *
 * Deliberately free of Android types so the ordering, exclusion and search
 * rules below are ordinary functions with ordinary tests. Everything that
 * needs a PackageManager lives in [InstalledApps]; everything that decides
 * what a person sees lives here.
 */
data class AppEntry(val packageName: String, val label: String)

/**
 * The rules for turning "every launchable app on this phone" into the list on
 * screen 06.
 *
 * Two of them are worth stating plainly, because they are the difference
 * between a screen-time app and a trap:
 *
 *  - Some apps must never be blockable at all. If Settings can be blocked,
 *    the person cannot revoke the permissions this app runs on. If the phone
 *    app can be blocked, the person cannot call for help. A commitment app
 *    is supposed to be hard to wriggle out of, not dangerous.
 *  - The apps that actually cost people their evenings should be at the top.
 *    Alphabetical order puts Amazon and Android Auto above Instagram, and a
 *    person then scrolls a list of ninety apps to find the four they meant.
 */
object AppCatalog {

    /**
     * Never offered, whatever the phone reports.
     *
     * [InstalledApps] adds more at runtime — the current launcher, the
     * default dialer and SMS app, the settings app as this manufacturer
     * names it — because those are answers only the device can give. This
     * list is the part that does not depend on the device: the OS plumbing
     * that has a launcher entry on some phones and would be nonsense to
     * limit, and this app itself.
     */
    val NeverOffered: Set<String> = setOf(
        "io.joinasr.app",
        "com.android.settings",
        "com.android.emergency",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.contacts",
        "com.google.android.contacts",
        "com.android.mms",
        "com.google.android.apps.messaging",
        "com.android.systemui",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        // Play, because an app that cannot be updated cannot be fixed, and
        // because uninstalling Asr goes through it.
        "com.android.vending",
    )

    /**
     * The apps this product exists for, in the order the design shows them.
     * Being on this list only lifts an app to the top of the picker; it is
     * never selected for anybody, and an app not on it is not treated as
     * harmless.
     *
     * Several of these ship under more than one package name across regions
     * and forks, which is why the match is by exact package and the fallback
     * is simply "sorted with everything else".
     */
    val Suggested: List<String> = listOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.zhiliaoapp.musically", // TikTok
        "com.ss.android.ugc.trill", // TikTok, some regions
        "com.reddit.frontpage",
        "com.twitter.android",
        "com.x.android",
        "com.snapchat.android",
        "com.whatsapp",
        "com.facebook.orca", // Messenger
        "com.netflix.mediaclient",
        "com.pinterest",
        "com.linkedin.android",
        "com.spotify.music",
        "tv.twitch.android.app",
        "com.discord",
        "com.telegram.messenger",
        "org.telegram.messenger",
    )

    private val suggestedRank: Map<String, Int> =
        Suggested.withIndex().associate { (index, name) -> name to index }

    /**
     * The picker's order: the well-known attention apps first in the order
     * above, then everything else by label.
     *
     * Sorted case-insensitively, and by package name where two apps share a
     * label — two apps really can, and a comparator that calls them equal
     * gives an order that changes between runs for no reason a person can
     * see.
     */
    fun ordered(entries: List<AppEntry>): List<AppEntry> = entries.sortedWith(
        compareBy<AppEntry> { suggestedRank[it.packageName] ?: Int.MAX_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
            .thenBy { it.packageName },
    )

    /** Everything the person is allowed to choose from, in picker order. */
    fun offerable(entries: List<AppEntry>, alsoExcluded: Set<String>): List<AppEntry> =
        ordered(
            entries.filter {
                it.packageName !in NeverOffered && it.packageName !in alsoExcluded
            },
        )

    /**
     * Search over labels, with apps whose name *starts* with the query above
     * apps that merely contain it: typing "in" should offer Instagram before
     * LinkedIn. Order inside each group is the picker order it was given, so
     * the suggested apps stay first there too.
     *
     * A blank query is not a search; it returns the list unchanged rather
     * than an empty one.
     */
    fun search(entries: List<AppEntry>, query: String): List<AppEntry> {
        val needle = query.trim()
        if (needle.isEmpty()) return entries
        val (prefix, rest) = entries
            .filter { it.label.contains(needle, ignoreCase = true) }
            .partition { it.label.startsWith(needle, ignoreCase = true) }
        return prefix + rest
    }

    /**
     * The two-line footer under the list. Kept here rather than in the
     * screen because "how many is enough" is a rule of the product, and the
     * screen should not be the place it is written down.
     */
    const val MINIMUM_SELECTED = 1

    fun selectionSummary(count: Int): String = when (count) {
        0 -> "No apps selected"
        1 -> "1 app selected"
        else -> "$count apps selected"
    }
}
