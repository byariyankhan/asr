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
 * arrives when the other person accepts, which is a server the app does not
 * have yet — so until then the relationship *is* the name, which is what the
 * design shows anyway ("Mom", "Relationship · Mom").
 */
@Serializable
data class Witness(
    val id: String,
    val relationship: String,
    val invitedAtMillis: Long,
    /**
     * True once the other person has accepted. Nothing sets it yet: accepting
     * needs an invite the server issues and a link that opens this app, and
     * neither exists. It is here rather than added later so that a witness
     * invited today is not one the app has to guess about tomorrow.
     */
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

    val all: List<Relationship> = listOf(
        Relationship("mother", "Mother"),
        Relationship("father", "Father"),
        Relationship("brother", "Brother"),
        Relationship("sister", "Sister"),
        Relationship("partner", "Partner"),
        Relationship("friend", "Friend"),
        Relationship("colleague", "Colleague"),
        Relationship("mentor", "Mentor"),
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
     * No link in it. An invite link needs a page that accepts it, and this
     * app has no such page: a URL that 404s in somebody's mother's messages
     * is worse than an invitation that simply says what it is and asks them
     * to expect one. The link goes in the moment the server can answer it.
     */
    fun inviteText(fromName: String, relationship: String, days: Int): String {
        val name = fromName.trim().ifBlank { "Someone" }
        val role = labelFor(relationship).lowercase()
        return "$name is starting a $days-day challenge to cut down their screen time, " +
            "and has asked you — as their $role — to be a witness.\n\n" +
            "Being a witness means you are told if they break it. That is the whole " +
            "point: it is harder to quit when somebody knows.\n\n" +
            "Asr will send you the link to accept once it is out."
    }
}
