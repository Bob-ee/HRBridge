package com.example.runh10.shared.run

enum class Motion { IDLE, WALKING, RUNNING }

class MotionClassifier(
    private val runCadenceSpm: Double = 140.0,
    private val runSpeedMps: Double = 1.8,
    private val idleSpeedMps: Double = 0.5,
    private val runSustainMs: Long = 12_000,
    private val idleSustainMs: Long = 4_000,
) {
    private var state = Motion.WALKING
    private var runningSince: Long? = null
    private var idleSince: Long? = null

    fun feed(cadenceSpm: Double?, speedMps: Double?, nowMs: Long): Motion {
        val cad = cadenceSpm ?: 0.0
        val spd = speedMps ?: 0.0
        val looksRunning = cad >= runCadenceSpm && spd >= runSpeedMps
        val looksIdle = spd < idleSpeedMps && cad < 1.0

        runningSince = if (looksRunning) (runningSince ?: nowMs) else null
        idleSince = if (looksIdle) (idleSince ?: nowMs) else null

        when (state) {
            Motion.WALKING, Motion.IDLE ->
                if (runningSince != null && nowMs - runningSince!! >= runSustainMs) state = Motion.RUNNING
                else if (!looksIdle) state = Motion.WALKING
                else if (idleSince != null && nowMs - idleSince!! >= idleSustainMs) state = Motion.IDLE
            Motion.RUNNING ->
                if (idleSince != null && nowMs - idleSince!! >= idleSustainMs) state = Motion.IDLE
        }
        return state
    }
}
