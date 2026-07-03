package com.example.runh10.record

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.ArrayDeque

/**
 * Steps-per-minute from the cumulative step counter over a rolling window.
 * (The phone has no Health Services cadence stream — this is its stand-in.)
 */
class CadenceTracker(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private data class P(val tsMs: Long, val steps: Double)
    private val window = ArrayDeque<P>()
    private val windowMs = 15_000L

    @Volatile var cadenceSpm: Double? = null
        private set

    fun start() {
        stepSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        window.clear()
        cadenceSpm = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val now = System.currentTimeMillis()
        window.addLast(P(now, event.values[0].toDouble()))
        while (window.size > 1 && now - window.peekFirst().tsMs > windowMs) window.pollFirst()
        val first = window.peekFirst()
        val last = window.peekLast()
        val dtMin = (last.tsMs - first.tsMs) / 60_000.0
        cadenceSpm = if (dtMin > 0.05) ((last.steps - first.steps) / dtMin).coerceAtLeast(0.0) else null
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
