package io.joinasr.app.witness

import kotlinx.serialization.Serializable

/** How somebody knows their witness. The only thing Figma 08 asks for. */
@Serializable
data class Relationship(val value: String, val label: String)

/**
 * One person invited to watch a challenge.
 *
 * There is no name here, because screen 08 never asks for one: it asks for a
 * relationship and then hands the invite to Android's share sheet. The name
 * arrives with the other person when they accept, so until then the
 * relationship *is* the name — which is what the design shows anyway ("Mom",
 * "Relationship · Mom").
 */
@Serializable
data class Witness(
    val id: String,
    val relationship: String,
    val invitedAtMillis: Long,
    /** The link the server issued for this invite, so it can be shared again. */
    val inviteUrl: String? = null,
    /** True once the other person has opened the link and accepted. */
    val accepted: Boolean = false,
    /** Their name, which only exists once they have accepted. */
    val name: String? = null,
) {
    /**
     * What to call them. Their name once there is one, and the relationship
     * until then — which is not a placeholder but the truth: an invite that
     * has not been accepted has no person behind it yet.
     */
    val label: String get() = name?.takeIf { it.isNotBlank() }
        ?: Relationships.labelFor(relationship)
}

/**
 * The relationships offered.
 *
 * Deliberately short and concrete. The list is what the invite and every
 * later notification are written in terms of — "your mother will be told" —
 * and a free-text box would make that sentence impossible to write.
 */
object Relationships {

    /**
     * Exactly the set the server accepts, in the same spelling.
     *
     * The API validates `relationship` against an enum, so a value this list
     * invents is a 400 on the first invite anybody sends.
     *
     * Each one names a single person, because a witness is a single person
     * and whoever is sending the invitation knows which one. The first
     * version of this list lumped them — "Parent", "Husband or wife" — which
     * reads as the app hedging about something it never had to guess at, and
     * makes every sentence built from it hedge too: "your husband or wife
     * will be told" is not a sentence anybody would write.
     */
    val all: List<Relationship> = listOf(
        Relationship("mother", "Mother"),
        Relationship("father", "Father"),
        Relationship("brother", "Brother"),
        Relationship("sister", "Sister"),
        Relationship("husband", "Husband"),
        Relationship("wife", "Wife"),
        Relationship("friend", "Friend"),
        Relationship("mentor", "Mentor"),
        Relationship("colleague", "Colleague"),
    )

    /**
     * Values the app has written but no longer offers.
     *
     * Not offered, still displayed: an invite sent last week is still an
     * invite, and a witness row reading "Witness" because the app stopped
     * recognising its own value would be a list change eating old data.
     */
    private val legacy = mapOf(
        "parent" to "Parent",
        "sibling" to "Brother or sister",
        "spouse" to "Husband or wife",
        "partner" to "Partner",
        "other" to "Someone else",
    )

    fun labelFor(value: String): String =
        all.firstOrNull { it.value == value }?.label ?: legacy[value] ?: "Witness"

    /** One required, three the design lays out room for. */
    const val REQUIRED = 1
    const val SLOTS = 3

    /**
     * What the share sheet sends.
     *
     * One message per relationship, because the person receiving it is a
     * specific person. "Hey Mom" is a message somebody's mother reads as a
     * message from her child; a paragraph written about "the user" in the
     * third person is one she reads as an advertisement, and deletes.
     *
     * Nothing here is composed from a name. The app never asks who the
     * witness is — only what they are to the sender — so the greeting is the
     * relationship's own word and the message is first person throughout.
     *
     * The link is the server's. It allocates the code, stores it against
     * this account and hands back the URL that opens it; one composed here
     * that merely looked right would be a link nothing answers.
     */
    fun inviteText(relationship: String, days: Int, url: String): String {
        val duration = "$days-day"
        val body = when (relationship) {
            "mother" -> "Hey Mom,\n\n" +
                "I\u2019m starting a $duration challenge to cut down my screen time, and " +
                "I want you to be my witness."

            "father" -> "Hey Dad,\n\n" +
                "I\u2019m starting a $duration challenge to cut down my screen time, and " +
                "I want you to be my witness."

            "brother" -> "Hey bro,\n\n" +
                "I\u2019m doing a $duration screen-time challenge, and I want you to keep " +
                "me accountable."

            "sister" -> "Hey sis,\n\n" +
                "I\u2019m doing a $duration screen-time challenge, and I want you to keep " +
                "me accountable."

            "husband" -> "Hey love,\n\n" +
                "I\u2019m starting a $duration challenge to cut down my screen time, and " +
                "I want you to keep me accountable."

            "wife" -> "Hey love,\n\n" +
                "I\u2019m starting a $duration challenge to cut down my screen time, and " +
                "I want you to be my witness."

            "friend" -> "Hey,\n\n" +
                "I\u2019m doing a $duration screen-time challenge. Be my witness?"

            "mentor" -> "Hi,\n\n" +
                "I\u2019m starting a $duration challenge to reduce my screen time, and " +
                "I\u2019d value having you as my witness."

            "colleague" -> "Hi,\n\n" +
                "I\u2019m starting a $duration screen-time challenge, and I\u2019d like " +
                "you to keep me accountable."

            // Only reachable from a value this build no longer offers, which
            // means an invite re-shared from an older row. Neutral rather
            // than absent: a share sheet opening with nothing in it is worse
            // than one opening with a plain sentence.
            else -> "Hi,\n\n" +
                "I\u2019m starting a $duration screen-time challenge, and I\u2019d like " +
                "you to be my witness."
        }
        return body + "\n\n" +
            "I\u2019ve made a commitment to myself, and Asr will keep you updated on how " +
            "it goes.\n\n" + url
    }
}
