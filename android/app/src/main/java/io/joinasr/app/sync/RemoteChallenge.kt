package io.joinasr.app.sync

import io.joinasr.app.enforcement.Pact

/**
 * The challenge this account is running, as the server holds it.
 *
 * One account runs on one phone, so there is never a question of whose this
 * is: by the time it can be read here, this phone is the one running it.
 * What it carries is what a fresh install cannot know -- the apps, the
 * limits, the day it started -- and the id to report against.
 */
data class RemoteChallenge(
    val pact: Pact,
    val remoteId: String,
)
