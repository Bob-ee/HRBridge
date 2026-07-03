package com.example.runh10.session

import com.example.runh10.shared.model.EvtRow
import com.example.runh10.shared.model.HrRow
import com.example.runh10.shared.model.LocRow
import com.example.runh10.shared.model.RrRow
import com.example.runh10.shared.model.SampleRow
import com.example.runh10.shared.model.SessionMeta
import com.example.runh10.shared.serial.NdjsonSerializer
import com.example.runh10.workout.ExerciseMetrics
import com.example.runh10.workout.HrSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.time.ZoneId

class SessionRecorder(
    private val scope: CoroutineScope,
    private val store: SessionStore,
) {
    private var writer: BufferedWriter? = null
    private var meta: SessionMeta? = null
    private val jobs = mutableListOf<Job>()
    val activeSessionId: String? get() = meta?.sessionId

    suspend fun start(
        hr: StateFlow<HrSample?>,
        metrics: StateFlow<ExerciseMetrics>,
    ): SessionMeta {
        val m = store.createSession(ZoneId.systemDefault().id)
        meta = m
        writer = store.fileFor(m.sessionId).bufferedWriter()
        jobs += scope.launch {
            hr.filterNotNull().collect { s ->
                writeLine(HrRow(ts = s.timestamp, bpm = s.bpm))
                s.rrMs.forEach { rr -> writeLine(RrRow(ts = s.timestamp, rr = rr)) }
            }
        }
        jobs += scope.launch {
            // Diagnostic breadcrumbs: remember the last-seen GPS availability and
            // exercise state so we can record a transition the moment either changes.
            // This is what tells us, post-hoc, whether GPS went UNAVAILABLE (and never
            // recovered) or the exercise ended — logcat rotates away, the session file
            // does not. Init to null so the first emission writes a baseline.
            var lastGps: String? = null
            var lastState: String? = null
            metrics.collect { mx ->
                val ts = System.currentTimeMillis()
                if (mx.gps != lastGps || mx.exerciseState != lastState) {
                    writeLine(
                        EvtRow(
                            ts = ts,
                            gps = mx.gps.takeIf { it != lastGps },
                            state = mx.exerciseState.takeIf { it != lastState },
                        )
                    )
                    lastGps = mx.gps
                    lastState = mx.exerciseState
                }
                if (mx.lat != null && mx.lon != null) {
                    writeLine(LocRow(ts = ts, lat = mx.lat, lon = mx.lon, alt = mx.altitude, spd = mx.speedMps, dist = mx.distanceMeters))
                }
            }
        }
        return m
    }

    @Synchronized private fun writeLine(row: SampleRow) {
        val w = writer ?: return
        w.write(NdjsonSerializer.encode(row)); w.newLine(); w.flush()
    }

    suspend fun stop() {
        jobs.forEach { it.cancel() }; jobs.clear()
        writer?.flush(); writer?.close(); writer = null
        meta?.let { store.finalize(it.sessionId, System.currentTimeMillis()) }
        meta = null
    }
}
