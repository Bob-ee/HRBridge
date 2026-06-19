package com.example.runh10.workout

import android.content.Context
import com.example.runh10.ble.HeartRateBleClient
import com.example.runh10.data.DevicePrefs
import com.example.runh10.exercise.ExerciseClientManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    lateinit var uiState: StateFlow<UiState>
        private set

    val devices: StateFlow<List<ScanDevice>> get() = ble.devices
    val bleState: StateFlow<HeartRateBleClient.State> get() = ble.state

    fun init(appContext: Context) {
        if (initialized) return
        initialized = true
        val ctx = appContext.applicationContext
        ble = HeartRateBleClient(ctx)
        exercise = ExerciseClientManager(ctx)
        devicePrefs = DevicePrefs(ctx)

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

        val merged = combine(
            ble.hr, ble.state, exercise.metrics, running, startTime,
        ) { hr, bleState, metrics, isRunning, start ->
            Merged(hr, bleState, metrics, isRunning, start)
        }

        uiState = combine(merged, ticker) { m, now ->
            val elapsed = if (m.running && m.start > 0L) (now - m.start) / 1000 else 0L
            UiState(
                running = m.running,
                hrState = m.bleState.name,
                bpm = m.hr?.bpm,
                rrMs = m.hr?.rrMs ?: emptyList(),
                distanceMeters = m.metrics.distanceMeters,
                speedMps = m.metrics.speedMps,
                gps = m.metrics.gps,
                lat = m.metrics.lat,
                lon = m.metrics.lon,
                elapsedSec = elapsed,
                exerciseState = m.metrics.exerciseState,
            )
        }.stateIn(scope, SharingStarted.Eagerly, UiState())
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
     * The foreground service and wake lock are already held from the [connectStrap] /
     * ACTION_CONNECT step. A session recorder will be wired in here in Task 8.
     * Must only be called AFTER connectStrap() has established (or is establishing) a link.
     */
    fun beginRun() {
        startTime.value = System.currentTimeMillis()
        running.value = true
        scope.launch { runCatching { exercise.start() } }
    }

    fun forgetDevice() {
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
    }

    private data class Merged(
        val hr: HrSample?,
        val bleState: HeartRateBleClient.State,
        val metrics: ExerciseMetrics,
        val running: Boolean,
        val start: Long,
    )
}
