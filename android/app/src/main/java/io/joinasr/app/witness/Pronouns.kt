package io.joinasr.app.witness

/**
 * He, she or they, from the profile's own answer.
 *
 * The server composes every notification with the same rule (witness-copy.ts),
 * and the screens that talk about a specific person -- the invitation, a
 * person's page -- follow it here rather than saying "their" about
 * somebody's brother. Null, "other" and "prefer_not_to_say" are they/them,
 * which is the right answer to a question somebody declined to answer.
 */
data class Pronouns(
    val they: String,
    val them: String,
    val their: String,
    val theirs: String,
    /** The verb "to be" that agrees with [they]: "he is", "they are". */
    val are: String,
) {
    companion object {
        val HE = Pronouns(they = "he", them = "him", their = "his", theirs = "his", are = "is")
        val SHE = Pronouns(they = "she", them = "her", their = "her", theirs = "hers", are = "is")
        val THEY = Pronouns(they = "they", them = "them", their = "their", theirs = "theirs", are = "are")

        fun of(gender: String?): Pronouns = when (gender) {
            "male" -> HE
            "female" -> SHE
            else -> THEY
        }
    }
}
