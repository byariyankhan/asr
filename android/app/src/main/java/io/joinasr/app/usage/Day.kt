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

    /**
     * The next midnight after [nowMillis]: when today's limits come back.
     *
     * The calendar's next day, not today's midnight plus twenty-four hours.
     * Those are the same instant on every day but the two a year a zone
     * changes offset, and on those the block screen said the apps were
     * back at one in the morning, or at eleven the night before.
     */
    fun nextMidnight(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMillis)
            .atZone(zone)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
}
