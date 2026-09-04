package io.joinasr.app

import android.content.Context
import androidx.core.content.edit
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The invitation somebody is part-way through answering.
 *
 * A witness is by definition somebody who does not have this app: the whole
 * point of inviting them is that they are not a user yet. So the link is
 * opened on a phone with no Asr on it, and two gaps open up between the tap
 * and the answer. This closes both, with the same stored value.
 *
 * **The install.** They tap the link, land on the web page, and install from
 * Play. Nothing about the app knows why it was installed. The invitation
 * page links to the listing with `referrer=w%3D<code>`, and Play hands that
 * string back on first launch through the Install Referrer API -- so the
 * first thing the app shows is the invitation they were reading, rather
 * than a welcome screen and no memory of what they were doing.
 *
 * **The sign-up.** Accepting needs an account, and creating one involves
 * leaving for an email app. Android is free to kill this process while they
 * are gone. Holding the code in a composable would lose it exactly there --
 * at the point where they have done the work and are one tap from finishing
 * -- so it goes to disk and stays until the invitation is answered.
 *
 * The referrer is asked for once, ever. It does not expire and Play keeps
 * answering with it, so without the flag every launch after an accepted
 * invitation would reopen the same one.
 */
object PendingInvite {
    private const val PREFS = "asr.invite"
    private const val KEY_CODE = "pending"
    private const val KEY_REFERRER_READ = "referrer_read"

    /** Held from the moment a link is opened until the answer is sent. */
    fun remember(context: Context, code: String) {
        prefs(context).edit { putString(KEY_CODE, code) }
    }

    fun clear(context: Context) {
        prefs(context).edit { remove(KEY_CODE) }
    }

    /**
     * The invitation to open, if there is one: the code stored by an earlier
     * launch, or -- on the first launch after an install -- the one Play was
     * given. Null on every ordinary launch, which is most of them.
     */
    suspend fun load(context: Context): String? {
        prefs(context).getString(KEY_CODE, null)?.let { return it }
        val referred = fromReferrer(context) ?: return null
        remember(context, referred)
        return referred
    }

    private suspend fun fromReferrer(context: Context): String? {
        val prefs = prefs(context)
        if (prefs.getBoolean(KEY_REFERRER_READ, false)) return null
        prefs.edit { putBoolean(KEY_REFERRER_READ, true) }
        val referrer = runCatching { read(context) }.getOrNull() ?: return null
        return codeIn(referrer)
    }

    /**
     * `w=<code>` out of the referrer string.
     *
     * Split by hand rather than through Uri, because this is a bare query
     * and not a URL: handing it to a URL parser is how a value containing an
     * `&` starts being read as two parameters. Anything that is not an
     * invite code is ignored -- Play appends its own parameters, and an
     * organic install carries `utm_source=google-play` and nothing else.
     */
    internal fun codeIn(referrer: String): String? = referrer
        .split('&')
        .firstNotNullOfOrNull { part ->
            val key = part.substringBefore('=', "")
            val value = part.substringAfter('=', "")
            if (key != "w") null else value.takeIf(::looksLikeCode)
        }

    /** Ten characters of the server's alphabet. Not validation -- the server
     *  decides -- just enough that a stray parameter cannot open a screen. */
    private fun looksLikeCode(value: String): Boolean =
        value.length in 6..24 && value.all { it.isLetterOrDigit() }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private suspend fun read(context: Context): String? = suspendCancellableCoroutine { cont ->
        val client = InstallReferrerClient.newBuilder(context.applicationContext).build()
        // The callback is documented as at-most-once, but this is a binding
        // to another process and a second resume would crash.
        var settled = false
        fun finish(value: String?) {
            if (settled) return
            settled = true
            runCatching { client.endConnection() }
            cont.resume(value)
        }
        cont.invokeOnCancellation { runCatching { client.endConnection() } }
        runCatching {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    if (responseCode != InstallReferrerClient.InstallReferrerResponse.OK) {
                        finish(null)
                        return
                    }
                    finish(runCatching { client.installReferrer.installReferrer }.getOrNull())
                }

                // Play Services restarting mid-handshake. Not worth waiting
                // for: the code is also in the link they can tap again.
                override fun onInstallReferrerServiceDisconnected() = finish(null)
            })
        }.onFailure { finish(null) }
    }
}
