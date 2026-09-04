package io.joinasr.app.sync

import java.security.SecureRandom
import java.util.Locale

/**
 * Time-ordered UUIDs, generated on the phone.
 *
 * The server takes the event id from the device and uses it as the
 * idempotency key, so it must be unique across every phone that ever posts
 * one — which rules out a counter — and it should sort by time, so a batch
 * of events that arrives late still reads in the order it happened.
 * That is exactly UUIDv7: 48 bits of Unix milliseconds, then randomness.
 *
 * Written out here rather than pulled in as a dependency. It is twenty
 * lines, it has no configuration, and a library for it would be a supply
 * chain to audit for something the standard defines in a paragraph.
 */
object Uuid7 {

    private val random = SecureRandom()

    fun next(nowMillis: Long = System.currentTimeMillis()): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)

        // 48 bits of milliseconds, most significant byte first.
        bytes[0] = (nowMillis ushr 40).toByte()
        bytes[1] = (nowMillis ushr 32).toByte()
        bytes[2] = (nowMillis ushr 24).toByte()
        bytes[3] = (nowMillis ushr 16).toByte()
        bytes[4] = (nowMillis ushr 8).toByte()
        bytes[5] = nowMillis.toByte()

        // Version 7 in the high nibble of byte 6, variant 10 in byte 8.
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()

        val hex = buildString(32) {
            for (byte in bytes) append(String.format(Locale.US, "%02x", byte))
        }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
            "${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
