package com.example.runh10.workout

import android.content.Context
import com.example.runh10.shared.ble.HeartRateBleClient
import com.example.runh10.data.DevicePrefs
import com.example.runh10.data.RunHistoryPrefs
import com.example.runh10.data.RunSettings
import com.example.runh10.data.SettingsStore
import com.example.runh10.data.defaultRunName
import com.example.runh10.data.effectiveMaxHr
import com.example.runh10.shared.run.RollingRmssd
import com.example.runh10.exercise.ExerciseClientManager
import com.example.runh10.session.SessionRecorder
import com.example.runh10.session.SessionStore
import com.example.runh10.shared.model.Split
import com.example.runh10.voice.MileAnnouncement
import com.example.runh10.voice.VoiceCoach
import com.example.runh10.zones.ZoneCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Process-scoped owner of the live run. Held as a singleton (not in the Activity)
 * so recording survives the UI being backgrounded or swept away — the foreground
 * service keeps the process alive; this object keeps the data and the merged state.
 */
object WorkoutController {

    /** Generic placeholder name used only when no real advertised/persisted name is known.
     * The true origin of this fallback is HeartRateBleClient.connectedAddressAndName()'s
     * targetName ?: DEFAULT_STRAP_NAME fallback — the persistence guard here must match it exactly. */
    private val GENERIC_STRAP_NAME = HeartRateBleClient.DEFAULT_STRAP_NAME

    private var initialized = false
    private lateinit var ble: HeartRateBleClient
    private lateinit var exercise: ExerciseClientManager
    private lateinit var devicePrefs: DevicePrefs
    private lateinit var store: SessionStore
    private lateinit var recorder: SessionRecorder
    private lateinit var settingsStore: SettingsStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val running = MutableStateFlow(false)
    private val startTime = MutableStateFlow(0L)

    private val _rememberedDevice = MutableStateFlow<ScanDevice?>(null)
    val rememberedDevice: StateFlow<ScanDevice?> get() = _rememberedDevice

    /**
     * Tracks the device currently being connected to (set immediately in connectStrap,
     * cleared in forgetDevice). Non-null whenever we are on the Prep screen with a
     * device selected — covers both the "just picked from scan" case (before DataStore
     * saves the address) and the "remembered on relaunch" case.
     */
    private val _pendingDevice = MutableStateFlow<ScanDevice?>(null)
    val pendingDevice: StateFlow<ScanDevice?> get() = _pendingDevice

    /** 1 Hz tick so elapsed time advances even when no sample arrives. */
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000)
        }
    }

    // Phase-2 logic units — re-instantiated on beginRun() for a fresh run.
    private var clock = RunClock()
    private var classifier = MotionClassifier()
    private var stateMachine = RunStateMachine()
    private var splitTracker = SplitTracker()
    private var rollingPace = RollingPace()
    private var rollingRmssd = RollingRmssd()

    /** Decimated GPS trail for the summary sketch (≥10 m between points, capped). */
    private val _route = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())

    /** Collectors that live only for the duration of one run. */
    private val runJobs = mutableListOf<kotlinx.coroutines.Job>()
    private lateinit var historyPrefs: RunHistoryPrefs

    // --- GPS re-engagement / fallback state (see GpsReengageDecider) ---
    /** Wall-clock of the last REENGAGE attempt this run (0 = none / cleared on recovery). */
    @Volatile private var lastReengageAtMs: Long = 0L
    /** True while the direct FusedLocationProvider fallback is streaming. */
    @Volatile private var fallbackActive: Boolean = false
    /** The live fallback stream, non-null only while [fallbackActive]. */
    private var fallback: com.example.runh10.exercise.FusedLocationFallback? = null

    @Volatile private var zoneCalc: ZoneCalculator? = null
    @Volatile private var currentSettings: RunSettings = RunSettings()
    /** Wall-clock time of the last HR sample, from any BLE connection (Ready or Live). */
    @Volatile private var lastHrAtMs: Long = 0L
    private lateinit var appContext: Context
    private lateinit var voice: VoiceCoach

    private val _splits = mutableListOf<Split>()
    private var warmupDistanceMeters: Double? = null

    lateinit var uiState: StateFlow<UiState>
        private set

    val devices: StateFlow<List<ScanDevice>> get() = ble.devices
    val bleState: StateFlow<HeartRateBleClient.State> get() = ble.state

    val sessions get() = store.observeAll()

    fun init(appContext: Context) {
        if (initialized) return
        initialized = true
        val ctx = appContext.applicationContext
        this.appContext = ctx
        ble = HeartRateBleClient(ctx)
        exercise = ExerciseClientManager(ctx)
        // Recovery fast-path: a genuine HS fix arriving mid-fallback tears the
        // fallback down immediately (same idempotent teardown as the tick path,
        // which stays as the backstop and clears the re-engage clock ≤1 s later).
        // The HS callback and this controller both run on the main looper.
        exercise.onHsLocationDuringFallback = { stopFallback() }
        devicePrefs = DevicePrefs(ctx)
        store = SessionStore(ctx)
        recorder = SessionRecorder(scope, store)
        settingsStore = SettingsStore(ctx)
        historyPrefs = RunHistoryPrefs(ctx)
        voice = VoiceCoach(ctx)
        scope.launch { store.recoverOrphans() }

        // Mirror persisted device into rememberedDevice StateFlow.
        scope.launch {
            devicePrefs.lastDevice.collect { saved ->
                _rememberedDevice.value = saved?.let { (mac, name) ->
                    ScanDevice(name = name, address = mac, rssi = 0)
                }
            }
        }

        // Persist the device address+name every time we reach CONNECTED.
        scope.launch {
            ble.state.collect { st ->
                if (st == HeartRateBleClient.State.CONNECTED) {
                    ble.connectedAddressAndName()?.let { (mac, name) ->
                        // V4: a pairing round-trip must never clobber a known-good,
                        // specific device name (e.g. "Polar H10 182CCF39") with the bare
                        // generic fallback — if we already have a more specific name on
                        // file for this exact MAC, keep it instead of overwriting.
                        val existing = _rememberedDevice.value
                        val wouldTruncate = name == GENERIC_STRAP_NAME &&
                            existing != null && existing.address == mac && existing.name != GENERIC_STRAP_NAME
                        if (!wouldTruncate) devicePrefs.saveLastDevice(mac, name)
                    }
                }
            }
        }

        // Keep ZoneCalculator and currentSettings in sync with user settings.
        scope.launch {
            settingsStore.settings.collect { s: RunSettings ->
                currentSettings = s
                zoneCalc = s.effectiveMaxHr()?.let { mx ->
                    s.restingHr?.let { r -> ZoneCalculator(mx, r) }
                }
            }
        }

        // Stamp freshness on every HR sample — runs for the process lifetime, so it
        // covers both the Prep (Ready) screen and a live run (F3: stale-HR display).
        scope.launch {
            ble.hr.filterNotNull().collect { lastHrAtMs = System.currentTimeMillis() }
        }

        val merged = combine(
            ble.hr, ble.state, exercise.metrics, running, startTime,
        ) { hr, bleState, metrics, isRunning, start ->
            Merged(hr, bleState, metrics, isRunning, start)
        }

        uiState = combine(merged, ticker) { m, now ->
            val elapsed = if (m.running && m.start > 0L) (now - m.start) / 1000 else 0L
            val distance = m.metrics.distanceMeters
            val bpm = m.hr?.bpm

            // Phase-2 logic — only driven when a run is active.
            // The scope is Main.immediate (single-threaded), so mutations below are safe.
            if (m.running) {
                val motion = classifier.feed(m.metrics.cadenceSpm, m.metrics.speedMps, now)
                // Auto-pause is a setting: run detection always works, but idle-driven
                // pause/resume transitions only fire when the toggle is on.
                val ev = if (stateMachine.state == RunState.WARMUP || currentSettings.autoPause)
                    stateMachine.onMotion(motion) else null
                when (ev) {
                    RunEvent.RUN_DETECTED -> {
                        warmupDistanceMeters = distance
                        if (currentSettings.announce) voice.say("Run detected")
                    }
                    RunEvent.AUTO_PAUSED, RunEvent.MANUAL_PAUSED -> {
                        clock.pause()
                        if (ev == RunEvent.AUTO_PAUSED && currentSettings.announce) voice.say("Paused")
                    }
                    RunEvent.AUTO_RESUMED, RunEvent.RESUMED -> {
                        clock.resume()
                        if (ev == RunEvent.AUTO_RESUMED && currentSettings.announce) voice.say("Resumed")
                    }
                    null -> Unit
                }

                if (stateMachine.state != RunState.WARMUP) {
                    rollingPace.add(now, distance ?: 0.0)
                    val runDist = (distance ?: 0.0) - (warmupDistanceMeters ?: 0.0)
                    splitTracker.onSample(runDist, clock.movingMs(), bpm, m.metrics.altitude)
                        ?.let { split ->
                            _splits.add(split)
                            if (currentSettings.announce) {
                                val hrZoneForAnnouncement = if (bpm != null) zoneCalc?.zoneFor(bpm) else null
                                voice.say(MileAnnouncement.build(split, hrZoneForAnnouncement, currentSettings))
                            }
                        }
                }
            }

            // Never-had-a-reading keeps the existing "--" behavior; only a reading that
            // has gone quiet 5s+ counts as stale (F3).
            val hrStale = lastHrAtMs > 0 && now - lastHrAtMs > 5_000

            val currentZoneCalc = zoneCalc
            val hrZone = if (bpm != null) currentZoneCalc?.zoneFor(bpm) else null
            val zoneEdges = currentZoneCalc?.edges ?: emptyList()
            // GPS status string confirmed/tuned on-device in Task 17
            val gpsLocked = m.metrics.gps.equals("ACQUIRED", ignoreCase = true) ||
                m.metrics.gps.contains("ACTIVE", ignoreCase = true)

            val movingMs = clock.movingMs()
            val avgPaceMps = distance?.let { d ->
                if (movingMs > 0) d / (movingMs / 1000.0) else null
            }

            // Live lap snapshot + delta vs the average of closed laps (sec/mi).
            val liveLap = if (m.running && stateMachine.state != RunState.WARMUP) {
                val runDist = (distance ?: 0.0) - (warmupDistanceMeters ?: 0.0)
                splitTracker.currentLap(runDist, movingMs)
            } else null
            val lapDelta = liveLap?.paceMps?.let { cur ->
                val closed = _splits.mapNotNull { s -> s.avgPaceMps.takeIf { it > 0.1 } }
                if (cur > 0.1 && closed.isNotEmpty()) {
                    val curSec = com.example.runh10.shared.Constants.MILE_METERS / cur
                    val avgSec = closed.map { com.example.runh10.shared.Constants.MILE_METERS / it }.average()
                    curSec - avgSec
                } else null
            }

            UiState(
                running = m.running,
                hrState = m.bleState.name,
                bpm = bpm,
                rrMs = m.hr?.rrMs ?: emptyList(),
                distanceMeters = distance,
                speedMps = m.metrics.speedMps,
                gps = m.metrics.gps,
                lat = m.metrics.lat,
                lon = m.metrics.lon,
                elapsedSec = elapsed,
                exerciseState = m.metrics.exerciseState,
                movingSec = movingMs / 1000,
                runState = stateMachine.state,
                warmupDistanceMeters = warmupDistanceMeters,
                rollingPaceMps = rollingPace.paceMps(),
                avgPaceMps = avgPaceMps,
                currentLapPaceMps = null, // lap pace populated when SplitTracker lap closes
                kcal = null,              // calorie computation reserved for a future task
                cadenceSpm = m.metrics.cadenceSpm,
                hrZone = hrZone,
                zoneEdges = zoneEdges,
                gpsLocked = gpsLocked,
                splits = _splits.toList(),
                currentLap = liveLap,
                lapDeltaSecPerMi = lapDelta,
                hrvMs = rollingRmssd.value(),
                routePoints = _route.value,
                hrStale = hrStale,
            )
        }.stateIn(scope, SharingStarted.Eagerly, UiState())
    }

    /**
     * V6: [onTick] fires once a second with the elapsed seconds (1..60) for the
     * duration of the measurement, independent of whether any HR samples actually
     * arrive — so the caller can drive a live "Measuring… Ns" label even in the
     * eventual no-strap-data case, where the sample collector below never emits at all.
     */
    suspend fun measureRestingHr(onTick: (elapsedSec: Int) -> Unit = {}): Int {
        val readings = mutableListOf<Int>()
        coroutineScope {
            val tickJob = launch {
                var elapsed = 0
                while (isActive) {
                    delay(1000)
                    elapsed++
                    onTick(elapsed)
                }
            }
            try {
                withTimeoutOrNull(60_000L) {
                    // Collect at most 40 samples, then let the flow COMPLETE normally.
                    // The prior `cancel()` early-exit cancelled this coroutineScope on
                    // the success path too, which propagated up and froze the caller's
                    // Settings countdown UI before `measuring=false` could run. take(40)
                    // bounds the collection without cancelling anything, so the success
                    // path returns cleanly and the caller's finally/post-code runs
                    // (final-review N4). 60s timeout + lowest-20% below unchanged.
                    ble.hr.filterNotNull().take(40).collect { sample ->
                        readings += sample.bpm
                    }
                }
            } finally {
                tickJob.cancel()
            }
        }
        if (readings.isEmpty()) return 0
        val sorted = readings.sorted()
        val count = maxOf(1, sorted.size / 5)
        return sorted.take(count).average().toInt()
    }

    fun startScan() = ble.startScan()

    fun stopScan() = ble.stopScan()

    /**
     * Connect-only: establishes the BLE link so live HR appears on the Prep screen,
     * but does NOT start the run (running stays false, exercise/FGS not started).
     * Call this when the user picks a device from the scan list, or on remembered-strap
     * auto-connect at launch.
     */
    fun connectStrap(deviceAddress: String, autoConnect: Boolean) {
        val deviceName = ble.devices.value.find { it.address == deviceAddress }?.name
            ?: _rememberedDevice.value?.takeIf { it.address == deviceAddress }?.name
        ble.setTargetName(deviceName)
        // Track this device immediately so the Prep screen renders before DataStore saves.
        _pendingDevice.value = ScanDevice(
            name = deviceName ?: GENERIC_STRAP_NAME,
            address = deviceAddress,
            rssi = ble.devices.value.find { it.address == deviceAddress }?.rssi ?: 0,
        )
        ble.connect(deviceAddress, autoConnect = autoConnect)
    }

    /**
     * Begin-run: flips [running] to true, records the start timestamp, and issues an
     * async [ExerciseClientManager.start] to begin the Health Services exercise session.
     * Re-instantiates all stateful logic units so a second run starts clean.
     * Must only be called AFTER connectStrap() has established (or is establishing) a link.
     */
    fun beginRun() {
        // Fresh logic units for this run — no state carries over from a prior run.
        clock = RunClock()
        classifier = MotionClassifier()
        stateMachine = RunStateMachine()
        splitTracker = SplitTracker()
        rollingPace = RollingPace()
        rollingRmssd = RollingRmssd()
        voice = VoiceCoach(appContext)
        _splits.clear()
        warmupDistanceMeters = null
        _route.value = emptyList()
        lastReengageAtMs = 0L
        stopFallback()

        clock.start()
        startTime.value = System.currentTimeMillis()
        running.value = true
        scope.launch { runCatching { exercise.start() } }
        scope.launch { recorder.start(ble.hr, exercise.metrics) }

        // Run-scoped collectors: RR intervals → streaming RMSSD; GPS → decimated trail.
        runJobs += scope.launch {
            ble.hr.filterNotNull().collect { s -> s.rrMs.forEach { rollingRmssd.feed(it) } }
        }
        runJobs += scope.launch {
            exercise.metrics.collect { mx ->
                val lat = mx.lat ?: return@collect
                val lon = mx.lon ?: return@collect
                val cur = _route.value
                val last = cur.lastOrNull()
                if (last == null || roughMeters(last.first, last.second, lat, lon) >= 10.0) {
                    // Cap the trail: halve resolution when it grows past 600 points.
                    _route.value =
                        if (cur.size >= 600) cur.filterIndexed { i, _ -> i % 2 == 0 } + (lat to lon)
                        else cur + (lat to lon)
                }
            }
        }

        // GPS-cutout defence: on a ~1 Hz tick, ask the pure decider whether Health
        // Services location has gone silent mid-run and act on its verdict. Every
        // transition (REENGAGE / FALLBACK / recovery) leaves an EvtRow via the gps
        // field so the next outdoor run proves or disproves the diagnosis.
        runJobs += scope.launch {
            ticker.collect { now ->
                val lastLoc = exercise.lastHsLocationAtMs
                val hsHealthy = lastLoc != 0L &&
                    now - lastLoc <= GpsReengageDecider.NO_FIX_THRESHOLD_MS
                if (hsHealthy) {
                    // HS delivering again → clear the attempt clock and tear the fallback down.
                    lastReengageAtMs = 0L
                    if (fallbackActive) stopFallback()
                }
                // Before the first fix, measure silence from run start so a GPS that
                // never acquires still escalates once RUNNING.
                val msSinceLoc = now - (if (lastLoc == 0L) startTime.value else lastLoc)
                val msSinceAttempt = if (lastReengageAtMs == 0L) null else now - lastReengageAtMs
                when (
                    GpsReengageDecider.decide(
                        gpsAvailability = exercise.metrics.value.gps,
                        runState = stateMachine.state,
                        msSinceLastLoc = msSinceLoc,
                        msSinceLastReengageAttempt = msSinceAttempt,
                    )
                ) {
                    GpsReengageAction.REENGAGE -> {
                        lastReengageAtMs = now
                        scope.launch { runCatching { exercise.reengage() } }
                    }
                    GpsReengageAction.FALLBACK -> startFallback()
                    GpsReengageAction.NONE -> Unit
                }
            }
        }
    }

    /** Starts the direct FusedLocationProvider fallback (idempotent). */
    private fun startFallback() {
        if (fallbackActive) return
        fallbackActive = true
        exercise.beginFallback()   // announces gps=FALLBACK_ACTIVE (EvtRow)
        val fb = com.example.runh10.exercise.FusedLocationFallback(appContext) { loc ->
            exercise.pushFallbackLocation(
                lat = loc.latitude,
                lon = loc.longitude,
                alt = if (loc.hasAltitude()) loc.altitude else null,
                acc = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null,
                speed = if (loc.hasSpeed()) loc.speed.toDouble() else null,
            )
        }
        fallback = fb
        fb.start()
    }

    /** Stops the fallback and restores the real HS gps string (idempotent). */
    private fun stopFallback() {
        fallback?.stop()
        fallback = null
        if (fallbackActive) {
            fallbackActive = false
            exercise.endFallback()   // EvtRow transition away from FALLBACK_ACTIVE
        }
    }

    /** Equirectangular distance approximation — plenty for 10 m decimation. */
    private fun roughMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1) * kotlin.math.cos(Math.toRadians((lat1 + lat2) / 2))
        return kotlin.math.sqrt(dLat * dLat + dLon * dLon) * 6_371_000.0
    }

    /** Manual lap from the run-controls screen: closes the in-progress segment immediately. */
    fun manualLap() {
        if (!running.value || stateMachine.state == RunState.WARMUP) return
        val ui = uiState.value
        val runDist = (ui.distanceMeters ?: 0.0) - (warmupDistanceMeters ?: 0.0)
        val split = splitTracker.closeNow(runDist, clock.movingMs())
        _splits.add(split)
        if (currentSettings.announce) voice.say("Lap ${split.index}")
    }

    fun manualPause() {
        stateMachine.manualPause()
        clock.pause()
    }

    fun manualResume() {
        if (stateMachine.manualResume() != null) clock.resume()
    }

    fun startNow() {
        val ev = stateMachine.startNow()
        if (ev == RunEvent.RUN_DETECTED) {
            warmupDistanceMeters = uiState.value.distanceMeters
        }
    }

    fun forgetDevice() {
        ble.disconnect()
        _pendingDevice.value = null
        scope.launch {
            devicePrefs.clear()
            // Null the in-memory value only after the DataStore write completes,
            // so a process death between the two operations cannot leave the old
            // MAC persisted while the UI has already cleared it.
            _rememberedDevice.value = null
        }
        ble.startScan()
    }

    fun stop() {
        // Snapshot before flipping running=false so the aggregates are the run's finals.
        val finalUi = uiState.value
        running.value = false
        runJobs.forEach { it.cancel() }
        runJobs.clear()
        stopFallback()
        ble.disconnect()
        scope.launch { exercise.stop() }
        scope.launch { recorder.stop() }
        voice.shutdown()

        // Persist last-run aggregates for the quick-launch tile's readiness card.
        val avgBpm = finalUi.splits.filter { it.avgBpm != null }.let { valid ->
            val w = valid.sumOf { it.movingDurationMs.toDouble() }
            if (w > 0) (valid.sumOf { it.avgBpm!! * it.movingDurationMs.toDouble() } / w).toInt() else null
        }
        scope.launch {
            historyPrefs.record(
                name = defaultRunName(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)),
                endedAtMs = System.currentTimeMillis(),
                distanceMeters = finalUi.distanceMeters ?: 0.0,
                elapsedSec = finalUi.elapsedSec,
                avgBpm = avgBpm,
                hrvMs = finalUi.hrvMs,
            )
            // The tile renders these aggregates — tell it now, not in 30 minutes.
            runCatching {
                androidx.wear.tiles.TileService.getUpdater(appContext)
                    .requestUpdate(com.example.runh10.tile.HrBridgeTileService::class.java)
            }
        }
    }

    private data class Merged(
        val hr: HrSample?,
        val bleState: HeartRateBleClient.State,
        val metrics: ExerciseMetrics,
        val running: Boolean,
        val start: Long,
    )
}
