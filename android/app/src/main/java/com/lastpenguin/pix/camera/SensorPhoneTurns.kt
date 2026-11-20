// AI-generated with Claude Code, 2026-11-17, reviewed by Joonhyung Han
package com.lastpenguin.pix.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [PhoneTurns] from the gravity sensor, or the accelerometer on a phone without one, while a camera screen watches.
 * Callbacks run on the main thread. A phone with neither keeps 0: its photos are saved upright, as before.
 */
class SensorPhoneTurns(context: Context) : PhoneTurns, SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val tracker = PhoneTurnTracker()
    private val current = MutableStateFlow(0)
    private var listening: Sensor? = null
    private val watchers = Watchers(start = ::start, stop = ::stop)

    override val turn: StateFlow<Int> = current.asStateFlow()

    override fun watch(owner: LifecycleOwner) = watchers.watch(owner)

    private fun start() {
        val manager = sensors ?: return
        val handler = Handler(Looper.getMainLooper())
        listening = listOfNotNull(
            manager.getDefaultSensor(Sensor.TYPE_GRAVITY),
            manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
        ).firstOrNull { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI, handler) }
    }

    private fun stop() {
        sensors?.unregisterListener(this)
        listening = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor != listening || event.values.size < AXES) return
        current.value = tracker.update(event.values[0], event.values[1], event.values[2])
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val AXES = 3
    }
}
