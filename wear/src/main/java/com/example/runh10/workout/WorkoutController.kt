package com.example.runh10.workout

import android.content.Context
import com.example.runh10.shared.ble.HeartRateBleClient
import com.example.runh10.data.DevicePrefs
import com.example.runh10.data.RunSettings
import com.example.runh10.data.SettingsStore
import com.example.runh10.data.effectiveMaxHr
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Process-scoped owner of the live run. Held as a singleton (not in the Activity)
 * so recording survives the UI being backgrounded or swept away — the foreground
 * service keeps the process alive; this object keeps the data and the merged state.
 */
object WorkoutController {

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

    @Volatile private var zoneCalc: ZoneCalculator? = null
    @Volatile private var currentSettings: RunSettings = RunSettings()
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
        devicePrefs = DevicePrefs(ctx)
        store = SessionStore(ctx)
        recorder = SessionRecorder(scope, store)
        settingsStore = SettingsStore(ctx)
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
                        devicePrefs.saveLastDevice(mac, name)
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
                val ev = stateMachine.onMotion(motion)
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
            )
        }.stateIn(scope, SharingStarted.Eagerly, UiState())
    }

    suspend fun measureRestingHr(): Int {
        val readings = mutableListOf<Int>()
        withTimeoutOrNull(60_000L) {
            ble.hr.filterNotNull().collect { sample ->
                readings += sample.bpm
                if (readings.size >= 40) cancel()
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
            name = deviceName ?: "Polar H10",
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
        voice = VoiceCoach(appContext)
        _splits.clear()
        warmupDistanceMeters = null

        clock.start()
        startTime.value = System.currentTimeMillis()
        running.value = true
        scope.launch { runCatching { exercise.start() } }
        scope.launch { recorder.start(ble.hr, exercise.metrics) }
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
        running.value = false
        ble.disconnect()
        scope.launch { exercise.stop() }
        scope.launch { recorder.stop() }
        voice.shutdown()
    }

    private data class Merged(
        val hr: HrSample?,
        val bleState: HeartRateBleClient.State,
        val metrics: ExerciseMetrics,
        val running: Boolean,
        val start: Long,
    )
}
