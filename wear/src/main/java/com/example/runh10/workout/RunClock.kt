package com.example.runh10.workout

class RunClock(private val now: () -> Long = System::currentTimeMillis) {
    private var startMs = 0L
    private var pausedAccumMs = 0L
    private var pauseStartMs = 0L
    private var paused = false

    fun start() { startMs = now(); pausedAccumMs = 0; pauseStartMs = 0; paused = false }
    fun pause() { if (!paused && startMs != 0L) { paused = true; pauseStartMs = now() } }
    fun resume() { if (paused) { pausedAccumMs += now() - pauseStartMs; paused = false } }

    fun elapsedMs(): Long = if (startMs == 0L) 0 else now() - startMs
    fun movingMs(): Long {
        if (startMs == 0L) return 0
        val openPause = if (paused) now() - pauseStartMs else 0L
        return (elapsedMs() - pausedAccumMs - openPause).coerceAtLeast(0)
    }
    val isPaused: Boolean get() = paused
}
