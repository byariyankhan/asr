package io.joinasr.app.data

import android.content.Context
import io.joinasr.app.enforcement.CarriedUsage
import io.joinasr.app.enforcement.EnforcementService
import io.joinasr.app.enforcement.PactStore
import io.joinasr.app.enforcement.UsageFloor
import io.joinasr.app.sync.SyncStore
import io.joinasr.app.witness.WitnessStore

/**
 * Everything this install holds about the person, forgotten at once.
 *
 * One account runs on one phone, so signing in somewhere else ends the
 * session here -- and this is what "ended" has to mean on this side of it.
 * Clearing the token alone would leave a phone still blocking apps for a
 * challenge that moved out of it, with a permanent notification saying it
 * was protecting something.
 *
 * The pact goes first, because the flow the enforcement service reads it
 * from is what stops the service. Nothing here is an ending: no outcome is
 * written and nobody is told, because the challenge did not end. It is being
 * kept somewhere else now.
 */
object LocalSignOut {

    suspend fun run(context: Context) {
        val app = context.applicationContext
        PactStore(app).clear()
        CarriedUsage(app).clear()
        UsageFloor(app).clear()
        WitnessStore(app).clear()
        SyncStore(app).clearDevice()
        Api.tokens(app).clear()
        EnforcementService.stop(app)
    }
}
