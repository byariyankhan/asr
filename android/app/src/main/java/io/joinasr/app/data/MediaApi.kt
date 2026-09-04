package io.joinasr.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Reads an image the server holds, by the path the server gave.
 *
 * `Me.image` is a path and never a URL -- the server stores a key and the
 * client owns the base -- so this is the one place that joins the two.
 *
 * No token. A profile photo is public on purpose: the witness invite has to
 * show the inviter's face to somebody who has no account yet, and an
 * authenticated image is an image that cannot appear on that screen.
 *
 * Null on anything that is not a 200 with a body. A missing photo is an
 * ordinary state -- the account may not have one, or the key may have been
 * replaced between the profile arriving and this call -- and the caller
 * draws an initial for all of them.
 */
class MediaApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
) {
    suspend fun bytes(path: String): ByteArray? = withContext(Dispatchers.IO) {
        val url = if (path.startsWith("http")) path else "$baseUrl$path"
        try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.bytes()
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            // A malformed path from an older build. Not worth a crash.
            null
        }
    }
}
