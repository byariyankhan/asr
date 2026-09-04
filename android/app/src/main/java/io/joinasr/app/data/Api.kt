package io.joinasr.app.data

import android.content.Context
import io.joinasr.app.BuildConfig
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * One OkHttpClient for the process. It owns the connection pool and the
 * thread pool; creating one per call is the standard way to make an app
 * slow and leak sockets.
 *
 * The base URL comes from BuildConfig, never a literal here, so pointing a
 * debug build at a laptop is a build-file change and not a code change.
 */
object Api {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // Short enough that a dead network fails while the person is
            // still looking at the screen, long enough for a slow one.
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val auth: AuthApi by lazy { AuthApi(client, BuildConfig.API_BASE_URL) }
    val me: MeApi by lazy { MeApi(client, BuildConfig.API_BASE_URL) }
    val witnesses: WitnessApi by lazy { WitnessApi(client, BuildConfig.API_BASE_URL) }
    val account: AccountApi by lazy { AccountApi(client, BuildConfig.API_BASE_URL) }
    val devices: DeviceApi by lazy { DeviceApi(client, BuildConfig.API_BASE_URL) }
    val pacts: PactApi by lazy { PactApi(client, BuildConfig.API_BASE_URL) }

    fun tokens(context: Context) = TokenStore(context.applicationContext)
}
