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
) {
    val label: String get() = Relationships.labelFor(relationship)
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
     * The API validates `relationship` against an enum, so a client list
     * that read "mother" and "brother" would have been refused with a 400 on
     * the first invite anybody sent. The labels are this app's; the values
     * are the server's, and they must stay that way.
     */
    val all: List<Relationship> = listOf(
        Relationship("parent", "Parent"),
        Relationship("sibling", "Brother or sister"),
        Relationship("spouse", "Husband or wife"),
        Relationship("partner", "Partner"),
        Relationship("friend", "Friend"),
        Relationship("mentor", "Mentor"),
        Relationship("colleague", "Colleague"),
        Relationship("other", "Someone else"),
    )

    fun labelFor(value: String): String =
        all.firstOrNull { it.value == value }?.label ?: "Witness"

    /** One required, three the design lays out room for. */
    const val REQUIRED = 1
    const val SLOTS = 3

    /**
     * What the share sheet sends.
     *
     * The link is the server's: it allocates the code, stores it against
     * this account and hands back the URL that opens it. Composing one here
     * that merely looked right would be a link nothing answers.
     */
    fun inviteText(fromName: String, relationship: String, days: Int, url: String): String {
        val name = fromName.trim().ifBlank { "Someone" }
        val role = labelFor(relationship).lowercase()
        return "$name is starting a $days-day challenge to cut down their screen time, " +
            "and has asked you — as their $role — to be a witness.\n\n" +
            "Being a witness means you are told if they break it. That is the whole " +
            "point: it is harder to quit when somebody knows.\n\n" +
            url
    }
}
