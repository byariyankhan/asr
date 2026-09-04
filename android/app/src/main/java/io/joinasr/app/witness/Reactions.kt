package io.joinasr.app.witness

/** One thing a witness can send. [value] is what the server takes. */
data class Reaction(val value: String, val emoji: String, val label: String)

/**
 * What a witness can react with.
 *
 * Exactly the five the API accepts, in its spelling. The Figma frames
 * disagree with each other and with the server here: 25 draws Funny, Haha,
 * Shoe and Tomato (four of these five), while 17 draws Respect ♛, Strong 🔥,
 * Push + and Roast 😂 — none of which exist as values. Sending one of those
 * would be a 400 on every tap, so the server's set is what is offered and
 * the mismatch is the designer's to settle.
 *
 * Which set is offered depends on what happened, which is 25's own rule:
 * "Completed challenges show positive reactions like Clap." Handing somebody
 * a tomato to throw at a friend who just finished a fortnight clean would be
 * the app misreading the moment.
 */
object Reactions {

    val laugh = Reaction("laugh", "🤣", "Funny")
    val haha = Reaction("haha", "😂", "Haha")
    val shoe = Reaction("shoe", "👞", "Shoe")
    val tomato = Reaction("tomato", "🍅", "Tomato")
    val clap = Reaction("clap", "👏", "Clap")

    val all = listOf(laugh, haha, shoe, tomato, clap)

    private val toBreach = listOf(laugh, haha, shoe, tomato)
    private val toGoodNews = listOf(clap, haha, laugh)

    /** The reactions worth offering for an event of [type]. */
    fun forEvent(type: String): List<Reaction> =
        if (type == "broken") toBreach else toGoodNews

    fun of(value: String?): Reaction? = all.firstOrNull { it.value == value }

    /** What an event says, in the words a witness reads. */
    fun describe(type: String, appLabel: String?, minutes: Int?): String = when (type) {
        "broken" -> "Broke the pact" + (appLabel?.let { " on $it" } ?: "")
        "completed" -> "Completed the challenge"
        "started" -> "Started a challenge"
        "limit_hit" -> "Reached a limit" + (appLabel?.let { " on $it" } ?: "")
        "activity_completed" -> "Earned +${minutes ?: 0} min"
        "restored" -> "Protection restored"
        else -> "Something happened"
    }
}
