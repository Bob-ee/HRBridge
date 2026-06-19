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

    fun start(deviceAddress: String) {
        val isRemembered = deviceAddress == _rememberedDevice.value?.address
        // Look up name from scan list or remembered device.
        val deviceName = ble.devices.value.find { it.address == deviceAddress }?.name
            ?: _rememberedDevice.value?.takeIf { it.address == deviceAddress }?.name
        ble.setTargetName(deviceName)
        ble.connect(deviceAddress, autoConnect = isRemembered)
        startTime.value = System.currentTimeMillis()
        running.value = true
        scope.launch { runCatching { exercise.start() } }
    }

    fun forgetDevice() {
        scope.launch { devicePrefs.clear() }
        _rememberedDevice.value = null
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
