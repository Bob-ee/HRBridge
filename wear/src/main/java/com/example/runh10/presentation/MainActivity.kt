package com.example.runh10.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.MaterialTheme
import com.example.runh10.data.SettingsStore
import com.example.runh10.presentation.theme.RunH10Theme
import com.example.runh10.service.WorkoutForegroundService
import com.example.runh10.workout.RunState
import com.example.runh10.workout.WorkoutController
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requiredPermissions: Array<String> = buildList {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.BODY_SENSORS)
        add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private fun hasAllPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WorkoutController.init(applicationContext)
        val settingsStore = SettingsStore(applicationContext)

        setContent {
            RunH10Theme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colors.background),
                ) {
                    var granted by remember { mutableStateOf(hasAllPermissions()) }
                    val launcher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions(),
                    ) { granted = hasAllPermissions() }

                    LaunchedEffect(Unit) {
                        if (!granted) launcher.launch(requiredPermissions)
                    }

                    if (!granted) {
                        PermissionScreen(onRequest = { launcher.launch(requiredPermissions) })
                    } else {
                        val ui by WorkoutController.uiState.collectAsStateWithLifecycle()
                        val devices by WorkoutController.devices.collectAsStateWithLifecycle()
                        val remembered by WorkoutController.rememberedDevice.collectAsStateWithLifecycle()
                        val pendingDevice by WorkoutController.pendingDevice.collectAsStateWithLifecycle()
                        val settings by settingsStore.settings.collectAsStateWithLifecycle(
                            initialValue = com.example.runh10.data.RunSettings()
                        )

                        val scope = rememberCoroutineScope()

                        // Auto-connect to the remembered strap on first composition
                        // (connect-only — does NOT start the run).
                        var autoConnected by rememberSaveable { mutableStateOf(false) }
                        LaunchedEffect(remembered) {
                            if (!autoConnected && remembered != null) {
                                autoConnected = true
                                connectStrap(remembered!!.address, autoConnect = true)
                            }
                        }

                        WorkoutFlow(
                            ui = ui,
                            devices = devices,
                            remembered = pendingDevice,
                            settings = settings,
                            onScan = { WorkoutController.startScan() },
                            onPick = { address -> connectStrap(address, autoConnect = false) },
                            onForget = { WorkoutController.forgetDevice() },
                            onStart = { beginRun() },
                            onEnd = { stopWorkout() },
                            onDone = { finishWorkout() },
                            onPauseToggle = {
                                if (ui.runState == RunState.MANUAL_PAUSED || ui.runState == RunState.AUTO_PAUSED)
                                    WorkoutController.manualResume()
                                else
                                    WorkoutController.manualPause()
                            },
                            onLap = { WorkoutController.lap() },
                            onStartNow = { WorkoutController.startNow() },
                            onAge = { v -> scope.launch { settingsStore.setAge(v) } },
                            onMaxHr = { v -> scope.launch { settingsStore.setMaxHr(v) } },
                            onMeasureResting = {
                                val bpm = WorkoutController.measureRestingHr()
                                if (bpm > 0) settingsStore.setRestingHr(bpm)
                                bpm
                            },
                            onToggleAnnounce = { v -> scope.launch { settingsStore.setAnnounce(v) } },
                            onToggleAnnounceSplit = { v -> scope.launch { settingsStore.setAnnounceSplitTime(v) } },
                            onToggleAnnouncePace = { v -> scope.launch { settingsStore.setAnnouncePace(v) } },
                            onToggleAnnounceZone = { v -> scope.launch { settingsStore.setAnnounceHrZone(v) } },
                            onToggleAutoPause = { v -> scope.launch { settingsStore.setAutoPause(v) } },
                        )
                    }
                }
            }
        }
    }

    private fun connectStrap(address: String, autoConnect: Boolean) {
        val intent = Intent(this, WorkoutForegroundService::class.java).apply {
            action = WorkoutForegroundService.ACTION_CONNECT
            putExtra(WorkoutForegroundService.EXTRA_DEVICE, address)
            putExtra(WorkoutForegroundService.EXTRA_AUTO_CONNECT, autoConnect)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun beginRun() {
        val intent = Intent(this, WorkoutForegroundService::class.java).apply {
            action = WorkoutForegroundService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopWorkout() {
        val intent = Intent(this, WorkoutForegroundService::class.java).apply {
            action = WorkoutForegroundService.ACTION_STOP
        }
        startService(intent)
    }

    private fun finishWorkout() {
        finishAndRemoveTask()
    }
}
