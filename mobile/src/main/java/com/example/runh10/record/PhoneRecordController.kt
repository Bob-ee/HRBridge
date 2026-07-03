package com.example.runh10.record

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.example.runh10.data.AthleteStore
import com.example.runh10.data.RunRepository
import com.example.runh10.healthconnect.HealthConnectWriter
import com.example.runh10.healthconnect.RmssdCalculator
import com.example.runh10.shared.Constants
import com.example.runh10.shared.ble.HeartRateBleClient
import com.example.runh10.shared.model.HrRow
import com.example.runh10.shared.model.LocRow
import com.example.runh10.shared.model.RrRow
import com.example.runh10.shared.model.SessionBundle
import com.example.runh10.shared.model.SessionMeta
import com.example.runh10.shared.model.SessionState
import com.example.runh10.shared.model.Split
import com.example.runh10.shared.run.LiveLap
import com.example.runh10.shared.run.MotionClassifier
import com.example.runh10.shared.run.RollingPace
import com.example.runh10.shared.run.RollingRmssd
import com.example.runh10.shared.run.RunClock
import com.example.runh10.shared.run.RunEvent
import com.example.runh10.shared.run.RunState
import com.example.runh10.shared.run.RunStateMachine
import com.example.runh10.shared.run.ScanDevice
import com.example.runh10.shared.run.SplitTracker
import com.example.runh10.shared.serial.NdjsonSerializer
import com.example.runh10.shared.zones.ZoneCalculator
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

/** Everything the phone's live record UI renders. */
data class PhoneRunUi(
    val phase: Phase = Phase.READY,
    val bleState: String = "IDLE",
    val bpm: Int? = null,
    val hrZone: Int? = null,
    val zoneEdges: List<Int> = emptyList(),
    val gpsLocked: Boolean = false,
    val gpsAccuracyM: Float? = null,
    val distanceM: Double = 0.0,
    val elapsedSec: Long = 0,
    val movingSec: Long = 0,
    val runState: RunState = RunState.WARMUP,
    val rollingPaceMps: Double? = null,
    val avgPaceMps: Double? = null,
    val cadenceSpm: Double? = null,
    val hrvMs: Double? = null,
    val splits: List<Split> = emptyList(),
    val currentLap: LiveLap? = null,
    val routePoints: List<Pair<Double, Double>> = emptyList(),
    val workoutType: String = "RUN",
) {
    enum class Phase { READY, LIVE, SAVE }
}

/**
 * Phone-side twin of the watch WorkoutController: H10 over BLE + fused GPS +
 * step-counter cadence, writing the same NDJSON session format into the run store.
 */
@SuppressLint("MissingPermission")
object PhoneRecordController {

    private var initialized = false
    private lateinit var appContext: Context
    lateinit var ble: HeartRateBleClient
        private set
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var cadence: CadenceTracker
    private lateinit var devicePrefs: PhoneDevicePrefs
    private lateinit var athleteStore: AthleteStore
    private lateinit var repo: RunRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _ui = MutableStateFlow(PhoneRunUi())
    val ui: StateFlow<PhoneRunUi> = _ui.asStateFlow()

    private val _rememberedDevice = MutableStateFlow<ScanDevice?>(null)
    val rememberedDevice: StateFlow<ScanDevice?> get() = _rememberedDevice

    val devices: StateFlow<List<ScanDevice>> get() = ble.devices

    // Run-scoped state
    private var clock = RunClock()
    private var classifier = MotionClassifier()
    private var stateMachine = RunStateMachine()
    private var splitTracker = SplitTracker()
    private var rollingPace = RollingPace()
    private var rollingRmssd = RollingRmssd()
    private val splits = mutableListOf<Split>()
    private val route = mutableListOf<Pair<Double, Double>>()
    private var distanceM = 0.0
    private var lastFix: Pair<Double, Double>? = null
    private var startMs = 0L
    private var meta: SessionMeta? = null
    private var writer: BufferedWriter? = null
    private var zoneCalc: ZoneCalculator? = null
    private val runJobs = mutableListOf<Job>()
    private var tts: TextToSpeech? = null
    private var pendingBundleRows = mutableListOf<com.example.runh10.shared.model.SampleRow>()

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        ble = HeartRateBleClient(appContext)
        fused = LocationServices.getFusedLocationProviderClient(appContext)
        cadence = CadenceTracker(appContext)
        devicePrefs = PhoneDevicePrefs(appContext)
        athleteStore = AthleteStore(appContext)
        repo = RunRepository.get(appContext)

        scope.launch {
            devicePrefs.lastDevice.collect { saved ->
                _rememberedDevice.value = saved?.let { (mac, name) -> ScanDevice(name, mac, 0) }
            }
        }
        scope.launch {
            ble.state.collect { st ->
                _ui.value = _ui.value.copy(bleState = st.name)
                if (st == HeartRateBleClient.State.CONNECTED) {
                    ble.connectedAddressAndName()?.let { (mac, name) -> devicePrefs.saveLastDevice(mac, name) }
                }
            }
        }
        scope.launch {
            athleteStore.profile.collect { p ->
                zoneCalc = p.maxHr?.let { mx -> p.restingHr?.let { r -> ZoneCalculator(mx, r) } }
                _ui.value = _ui.value.copy(zoneEdges = zoneCalc?.edges ?: emptyList())
            }
        }
        // Live HR into the ready/live UI even before a run starts.
        scope.launch {
            ble.hr.filterNotNull().collect { s ->
                _ui.value = _ui.value.copy(
                    bpm = s.bpm,
                    hrZone = zoneCalc?.zoneFor(s.bpm),
                )
            }
        }
        // 1 Hz ticker for clocks + derived pace fields while live.
        scope.launch {
            while (true) {
                if (_ui.value.phase == PhoneRunUi.Phase.LIVE) tick()
                delay(1000)
            }
        }
    }

    fun startScan() = ble.startScan()
    fun stopScan() = ble.stopScan()

    fun connectStrap(address: String, autoConnect: Boolean) {
        val name = ble.devices.value.find { it.address == address }?.name
            ?: _rememberedDevice.value?.takeIf { it.address == address }?.name
        ble.setTargetName(name)
        ble.connect(address, autoConnect)
    }

    fun forgetDevice() {
        ble.disconnect()
        scope.launch { devicePrefs.clear() }
        ble.startScan()
    }

    fun setWorkoutType(type: String) {
        _ui.value = _ui.value.copy(workoutType = type)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val now = System.currentTimeMillis()
            _ui.value = _ui.value.copy(gpsLocked = loc.accuracy <= 25f, gpsAccuracyM = loc.accuracy)
            if (_ui.value.phase != PhoneRunUi.Phase.LIVE) return

            lastFix?.let { (plat, plon) ->
                val d = roughMeters(plat, plon, loc.latitude, loc.longitude)
                if (d.isFinite() && d < 100) distanceM += d
            }
            lastFix = loc.latitude to loc.longitude
            if (route.isEmpty() || roughMeters(route.last().first, route.last().second, loc.latitude, loc.longitude) >= 10.0) {
                route += loc.latitude to loc.longitude
            }
            writeRow(
                LocRow(
                    ts = now,
                    lat = loc.latitude,
                    lon = loc.longitude,
                    alt = if (loc.hasAltitude()) loc.altitude else null,
                    spd = if (loc.hasSpeed()) loc.speed.toDouble() else null,
                    dist = distanceM,
                ),
            )
        }
    }

    /** Begin the run: session file, GPS stream, cadence, run-logic units. */
    fun beginRun(autoPause: Boolean, voiceCoach: Boolean, mileAnnouncements: Boolean) {
        clock = RunClock()
        classifier = MotionClassifier()
        stateMachine = RunStateMachine()
        splitTracker = SplitTracker()
        rollingPace = RollingPace()
        rollingRmssd = RollingRmssd()
        splits.clear()
        route.clear()
        pendingBundleRows.clear()
        distanceM = 0.0
        lastFix = null
        startMs = System.currentTimeMillis()
        this.voiceEnabled = voiceCoach
        this.mileAnnouncementsEnabled = mileAnnouncements
        this.autoPauseEnabled = autoPause

        val m = SessionMeta(
            sessionId = UUID.randomUUID().toString(),
            startEpochMs = startMs,
            startZoneId = ZoneId.systemDefault().id,
            appVersion = Constants.APP_VERSION,
            state = SessionState.RECORDING,
        )
        meta = m
        writer = repo.fileFor(m.sessionId).bufferedWriter()

        clock.start()
        _ui.value = _ui.value.copy(
            phase = PhoneRunUi.Phase.LIVE,
            runState = RunState.WARMUP,
            distanceM = 0.0,
            elapsedSec = 0,
            movingSec = 0,
            splits = emptyList(),
            routePoints = emptyList(),
            hrvMs = null,
        )

        if (tts == null) tts = TextToSpeech(appContext) { }

        runJobs += scope.launch {
            ble.hr.filterNotNull().collect { s ->
                writeRow(HrRow(ts = s.timestamp, bpm = s.bpm))
                s.rrMs.forEach { rr ->
                    writeRow(RrRow(ts = s.timestamp, rr = rr))
                    rollingRmssd.feed(rr)
                }
            }
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()
        fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        cadence.start()
    }

    private var voiceEnabled = true
    private var mileAnnouncementsEnabled = true
    private var autoPauseEnabled = false

    private fun tick() {
        val now = System.currentTimeMillis()
        val cad = cadence.cadenceSpm
        val speed = rollingPace.paceMps()

        if (autoPauseEnabled || stateMachine.state == RunState.WARMUP) {
            val motion = classifier.feed(cad, speed, now)
            when (stateMachine.onMotion(motion)) {
                RunEvent.RUN_DETECTED -> say("Run detected")
                RunEvent.AUTO_PAUSED -> { clock.pause(); say("Paused") }
                RunEvent.AUTO_RESUMED -> { clock.resume(); say("Resumed") }
                else -> Unit
            }
        }

        if (stateMachine.state != RunState.WARMUP) {
            rollingPace.add(now, distanceM)
            splitTracker.onSample(distanceM, clock.movingMs(), _ui.value.bpm, null)?.let { s ->
                splits += s
                if (mileAnnouncementsEnabled) say(
                    "Mile ${s.index}. ${paceSpoken(s.avgPaceMps)} per mile.",
                )
            }
        }

        val movingMs = clock.movingMs()
        _ui.value = _ui.value.copy(
            distanceM = distanceM,
            elapsedSec = (now - startMs) / 1000,
            movingSec = movingMs / 1000,
            runState = stateMachine.state,
            rollingPaceMps = rollingPace.paceMps(),
            avgPaceMps = if (movingMs > 0) distanceM / (movingMs / 1000.0) else null,
            cadenceSpm = cad,
            hrvMs = rollingRmssd.value(),
            splits = splits.toList(),
            currentLap = if (stateMachine.state != RunState.WARMUP) splitTracker.currentLap(distanceM, movingMs) else null,
            routePoints = route.toList(),
        )
    }

    fun manualPause() { stateMachine.manualPause(); clock.pause(); pushRunState() }
    fun manualResume() { if (stateMachine.manualResume() != null) clock.resume(); pushRunState() }
    fun startNow() { stateMachine.startNow(); pushRunState() }
    private fun pushRunState() { _ui.value = _ui.value.copy(runState = stateMachine.state) }

    /** Stop recording; moves to the Save phase (file stays until save/discard). */
    fun finishRun() {
        runJobs.forEach { it.cancel() }
        runJobs.clear()
        fused.removeLocationUpdates(locationCallback)
        cadence.stop()
        writer?.flush()
        _ui.value = _ui.value.copy(phase = PhoneRunUi.Phase.SAVE)
    }

    /** Persist: summary into Room (+ optional Health Connect write). Returns sessionId. */
    suspend fun saveRun(name: String, feel: String?): String? {
        val m = meta ?: return null
        writer?.flush(); writer?.close(); writer = null
        val endMs = System.currentTimeMillis()
        val bundle = SessionBundle(
            meta = m.copy(endEpochMs = endMs, state = SessionState.FINALIZED),
            samples = pendingBundleRows.sortedBy { it.ts },
            splits = splits.toList(),
        )
        val rmssd = RmssdCalculator.compute(bundle.samples.filterIsInstance<RrRow>())
        runCatching {
            val hcWriter = HealthConnectWriter(appContext)
            if (hcWriter.isAvailable() && hcWriter.hasAllPermissions()) hcWriter.write(bundle, rmssd)
        }
        repo.ingest(
            bundle = bundle,
            source = "phone",
            name = name,
            workoutType = _ui.value.workoutType,
            precomputedHrvMs = rmssd.takeIf { it.isNotEmpty() }?.map { it.rmssdMs }?.average(),
        )
        feel?.let { repo.updateNameFeel(m.sessionId, name, it) }
        resetToReady()
        return m.sessionId
    }

    fun discardRun() {
        meta?.let { repo.fileFor(it.sessionId).delete() }
        writer?.close(); writer = null
        resetToReady()
    }

    private fun resetToReady() {
        meta = null
        pendingBundleRows.clear()
        _ui.value = _ui.value.copy(phase = PhoneRunUi.Phase.READY)
    }

    @Synchronized
    private fun writeRow(row: com.example.runh10.shared.model.SampleRow) {
        pendingBundleRows += row
        val w = writer ?: return
        w.write(NdjsonSerializer.encode(row)); w.newLine()
    }

    private fun say(text: String) {
        if (!voiceEnabled) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "hrbridge")
    }

    private fun paceSpoken(paceMps: Double): String {
        if (paceMps < 0.1) return "unknown pace"
        val secPerMile = (Constants.MILE_METERS / paceMps).toInt()
        return String.format(Locale.US, "%d minutes %d seconds", secPerMile / 60, secPerMile % 60)
    }

    private fun roughMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1) * kotlin.math.cos(Math.toRadians((lat1 + lat2) / 2))
        return kotlin.math.sqrt(dLat * dLat + dLon * dLon) * 6_371_000.0
    }
}
