package com.example.runh10.presentation

import android.Manifest
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.MaterialTheme
import com.example.runh10.presentation.theme.RunH10Theme
import com.example.runh10.service.WorkoutForegroundService
import com.example.runh10.workout.WorkoutController

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

                        // Auto-connect to the remembered device on first composition.
                        var autoStarted by rememberSaveable { mutableStateOf(false) }
                        LaunchedEffect(remembered) {
                            if (!autoStarted && remembered != null) {
                                autoStarted = true
                                startWorkout(remembered!!.address)
                            }
                        }

                        WorkoutFlow(
                            ui = ui,
                            devices = devices,
                            remembered = remembered,
                            onScan = { WorkoutController.startScan() },
                            onPick = { address -> startWorkout(address) },
                            onForget = { WorkoutController.forgetDevice() },
                            onEnd = { endWorkout() },
                        )
                    }
                }
            }
        }
    }

    private fun startWorkout(address: String) {
        val intent = Intent(this, WorkoutForegroundService::class.java).apply {
            action = WorkoutForegroundService.ACTION_START
            putExtra(WorkoutForegroundService.EXTRA_DEVICE, address)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun endWorkout() {
        val intent = Intent(this, WorkoutForegroundService::class.java).apply {
            action = WorkoutForegroundService.ACTION_STOP
        }
        startService(intent)
        finishAndRemoveTask()
    }
}
