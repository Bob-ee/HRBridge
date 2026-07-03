package com.example.runh10.shared.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.example.runh10.shared.run.HrSample
import com.example.runh10.shared.run.ScanDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Raw-GATT client for a standard BLE Heart Rate device (Polar H10).
 * Scans by the Heart Rate service UUID, subscribes to Heart Rate Measurement (0x2A37),
 * parses HR + RR per the BLE Heart Rate Profile, and auto-reconnects on dropout.
 * Shared between the watch app and the phone record flow.
 *
 * Permissions (BLUETOOTH_SCAN/CONNECT) are requested by the host app before any call
 * here, so the calls are annotated MissingPermission.
 */
@SuppressLint("MissingPermission")
class HeartRateBleClient(private val context: Context) {

    enum class State { IDLE, SCANNING, CONNECTING, CONNECTED, DISCONNECTED }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _devices = MutableStateFlow<List<ScanDevice>>(emptyList())
    val devices: StateFlow<List<ScanDevice>> = _devices.asStateFlow()

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    // SharedFlow, NOT StateFlow: a conflated StateFlow can silently drop an HrSample
    // (and its RR intervals) when two GATT notifications land before the main-thread
    // collector runs — corrupting the session file and HRV. replay=1 keeps late
    // subscribers (combine) primed; the buffer absorbs bursts; the initial null
    // emission preserves the old StateFlow "starts with null" contract.
    private val _hr = MutableSharedFlow<HrSample?>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    ).apply { tryEmit(null) }
    val hr: SharedFlow<HrSample?> = _hr.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var gatt: BluetoothGatt? = null
    private var targetAddress: String? = null
    private var targetName: String? = null
    private var wantConnected = false
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = device.name ?: result.scanRecord?.deviceName ?: "Unknown HR"
            val entry = ScanDevice(name, device.address, result.rssi)
            _devices.update { list ->
                if (list.any { it.address == entry.address }) {
                    list.map { if (it.address == entry.address) entry else it }
                } else {
                    list + entry
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "scan failed: $errorCode")
            _state.value = State.IDLE
        }
    }

    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        _devices.value = emptyList()
        _state.value = State.SCANNING
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(HR_SERVICE)).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    fun stopScan() {
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        if (_state.value == State.SCANNING) _state.value = State.IDLE
    }

    fun connect(address: String, autoConnect: Boolean = false) {
        stopScan()
        targetAddress = address
        wantConnected = true
        reconnectAttempts = 0
        openGatt(autoConnect = autoConnect)
    }

    fun setTargetName(name: String?) {
        targetName = name
    }

    fun connectedAddressAndName(): Pair<String, String>? {
        val addr = targetAddress ?: return null
        return addr to (targetName ?: "Polar H10")
    }

    fun disconnect() {
        wantConnected = false
        reconnectJob?.cancel()
        reconnectJob = null
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _state.value = State.IDLE
    }

    private fun openGatt(autoConnect: Boolean) {
        val device = adapter?.getRemoteDevice(targetAddress ?: return) ?: return
        // Always start from a clean GATT instance; lingering ones cause error 133.
        gatt?.close()
        _state.value = State.CONNECTING
        gatt = device.connectGatt(context, autoConnect, gattCallback, BluetoothDevice.TRANSPORT_LE)
        Log.d(TAG, "connectGatt(autoConnect=$autoConnect) attempt=$reconnectAttempts")
    }

    /**
     * Reconnect with backoff. Reconnects use autoConnect=true so the BT stack re-attaches
     * opportunistically the moment the strap advertises again — far more robust to the
     * frequent brief dropouts a chest strap produces than a one-shot direct connect.
     */
    private fun scheduleReconnect() {
        if (!wantConnected) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val backoffMs = minOf(1000L * (1 shl minOf(reconnectAttempts, 4)), 10_000L)
            reconnectAttempts++
            Log.d(TAG, "reconnect in ${backoffMs}ms (attempt $reconnectAttempts)")
            delay(backoffMs)
            if (wantConnected) openGatt(autoConnect = true)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        _state.value = State.CONNECTED
                        reconnectAttempts = 0
                        g.discoverServices()
                    } else {
                        // Connected event with an error status (e.g. 133): tear down and retry.
                        g.close()
                        gatt = null
                        _state.value = State.DISCONNECTED
                        scheduleReconnect()
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    _state.value = State.DISCONNECTED
                    g.close()
                    gatt = null
                    scheduleReconnect()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val characteristic = g.getService(HR_SERVICE)?.getCharacteristic(HR_MEASUREMENT)
            if (characteristic == null) {
                Log.w(TAG, "HR measurement characteristic not found")
                return
            }
            g.setCharacteristicNotification(characteristic, true)
            val cccd = characteristic.getDescriptor(CCCD) ?: return
            // API 33+ overload (minSdk 34): pass the value explicitly.
            g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            Log.d(TAG, "subscribed to HR notifications")
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == HR_MEASUREMENT) parseHeartRate(value)
        }
    }

    /** Parse a Heart Rate Measurement (0x2A37) packet: flags, HR, optional RR intervals. */
    private fun parseHeartRate(value: ByteArray) {
        if (value.isEmpty()) return
        val flags = value[0].toInt() and 0xFF
        val hrIs16Bit = flags and 0x01 != 0
        var i = 1
        val bpm: Int
        if (hrIs16Bit) {
            if (value.size < 3) return
            bpm = (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
            i = 3
        } else {
            if (value.size < 2) return
            bpm = value[1].toInt() and 0xFF
            i = 2
        }
        // Bit 3: energy-expended field present (2 bytes) before any RR intervals.
        if (flags and 0x08 != 0) i += 2
        val rrs = mutableListOf<Int>()
        // Bit 4: one or more RR intervals, uint16 LE in 1/1024 s.
        if (flags and 0x10 != 0) {
            while (i + 1 < value.size) {
                val raw = (value[i].toInt() and 0xFF) or ((value[i + 1].toInt() and 0xFF) shl 8)
                rrs.add(raw * 1000 / 1024)
                i += 2
            }
        }
        _hr.tryEmit(HrSample(bpm = bpm, rrMs = rrs, timestamp = System.currentTimeMillis()))
        Log.d(TAG, "HR=$bpm rr=$rrs")
    }

    companion object {
        private const val TAG = "HeartRateBleClient"
        val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
