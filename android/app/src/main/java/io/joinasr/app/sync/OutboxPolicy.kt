package io.joinasr.app.sync

/**
 * Whether an event the server refused with an HTTP status is worth sending
 * again.
 *
 * The outbox has two failure modes and only one of them is a bug. An event
 * it keeps forever because the server will never accept it is a stuck queue:
 * nothing behind it is sent either, and a challenge that ended offline is
 * never reported. An event it drops because the server was having a bad
 * minute is a witness who is never told -- and the first version of this
 * dropped on anything but 401 and 429, which included the 502 every deploy
 * serves for a minute while the container is recreated. A person who gave up
 * during that minute saw "3 witnesses notified · SENT", and nobody was.
 *
 * So the line is drawn at whose fault it is. A 4xx is the server saying no
 * to *this event*, and it will say no again: a 409 on a closed pact, a 400
 * on a body this build sends wrongly. Those are dropped. Anything that says
 * "not now" is kept: no session yet, too many requests, the server itself
 * failing. The one it does not see is being offline, which the caller
 * handles before this is asked.
 */
object OutboxPolicy {

    fun keepAfter(code: Int): Boolean = when {
        // The session may be back, or this phone is about to be signed out
        // and the outbox goes with it. Either way, not the event's fault.
        code == 401 -> true
        // Asked to slow down, or the request timed out on the way in.
        code == 408 || code == 425 || code == 429 -> true
        // The server's problem, whatever the number.
        code >= 500 -> true
        else -> false
    }
}
