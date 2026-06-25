package com.example.runh10

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.runh10.healthconnect.HealthConnectWriter
import com.example.runh10.ui.SyncScreen
import com.example.runh10.ui.SyncViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: SyncViewModel by viewModels()

    private val requestPerms =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            vm.onPermissionsResult()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface {
                    SyncScreen(vm = vm, onRequestPermissions = { requestPerms.launch(HealthConnectWriter.PERMISSIONS) })
                }
            }
        }
        // Auto-sync trigger on each foreground.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.onResume() }
        }
    }
}
