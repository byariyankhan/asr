package io.joinasr.app.earn

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The phone's own step counter.
 *
 * TYPE_STEP_COUNTER, not TYPE_STEP_DETECTOR, and the difference is the whole
 * design of this feature. The counter is a running total kept by the sensor
 * hub since the last reboot: it keeps counting while the app is closed, the
 * screen is off and the process is dead, and reading it later still gives
 * the right number. That is what lets Figma 23 promise "you can lock your
 * phone or leave this screen" and mean it.
 *
 * A detector would have needed a foreground service listening all the way
 * through the walk, which is battery spent to learn something the hardware
 * already knows.
 *
 * The counter is shared by every app on the phone and cannot be reset, so
 * progress is always the difference from a baseline taken when the activity
 * started.
 */
class StepCounter(context: Context) {

    private val app = context.applicationContext
    private val manager = app.getSystemService<SensorManager>()
    private val sensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    /** False on a phone with no step hardware, where walking cannot be offered. */
    val available: Boolean get() = sensor != null

    /**
     * Readings of the running total.
     *
     * SENSOR_DELAY_UI rather than anything faster: this drives a number on a
     * screen, and a step is not a thing that happens sixty times a second.
     */
    fun readings(): Flow<Int> = callbackFlow {
        val hardware = sensor
        if (manager == null || hardware == null) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val total = event.values.firstOrNull()?.toInt() ?: return
                trySend(total)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, hardware, SensorManager.SENSOR_DELAY_UI)
        awaitClose { manager.unregisterListener(listener) }
    }
}
