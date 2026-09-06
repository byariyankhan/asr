package io.joinasr.app

import android.app.Application
import io.joinasr.app.push.AsrMessagingService

class AsrApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // The channel witness updates land on, made before the first one can
        // arrive. A push that comes while the app is in the background is
        // posted by Firebase itself, on the channel the manifest names -- and
        // if that channel does not exist yet, Firebase invents a default one
        // with default importance: no heads-up, no sound, easy to miss, and
        // it stays that way. Until now the channel was only created the
        // first time the app itself posted a notification in the foreground.
        AsrMessagingService.createChannel(this)
    }
}
