package io.joinasr.app.usage

import java.time.Instant
import java.time.ZoneId

/**
 * Where one day ends and the next begins, for a person rather than for a
 * server.
 *
 * Always in the phone's own zone, and always recomputed rather than
 * remembered: somebody crossing a timezone gets a shorter or longer day, and
 * that is the right answer — their limits reset at the midnight they are
 * living in. It is also why nothing here caches a ZoneId.
 */
object Day {

    fun startOfDay(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMillis)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    fun isSameDay(a: Long, b: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        startOfDay(a, zone) == startOfDay(b, zone)
}
