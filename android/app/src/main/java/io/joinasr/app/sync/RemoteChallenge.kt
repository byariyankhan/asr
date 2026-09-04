package io.joinasr.app.sync

import io.joinasr.app.enforcement.Pact

/**
 * A challenge this account is running that this phone is not.
 *
 * One challenge runs on one handset, and that is not a limitation to be
 * apologised for -- it is the only honest arrangement. Each phone can measure
 * only its own screen: two phones enforcing the same thirty minutes would
 * give somebody sixty, and the witnesses a number that flips between the two
 * every half hour depending on which reported last.
 *
 * So a second phone -- or the same phone after a reinstall -- is shown this
 * rather than a challenge it can act on, and moving it here is something the
 * person does on purpose.
 */
data class RemoteChallenge(
    /** Rebuilt from the server's snapshot: the apps, the limits, the dates. */
    val pact: Pact,
    /** The server's id for it, needed to claim it and to report against it. */
    val remoteId: String,
    /** True when the server already says this handset is the one running it. */
    val onThisPhone: Boolean,
    /** What to call the handset that is running it, when the server knows. */
    val phone: String?,
)
