package com.example.runh10.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.runh10.sync.PhoneSyncClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SyncUiState(
    val hcAvailable: Boolean = false,
    val permissionsGranted: Boolean = false,
    val syncing: Boolean = false,
    val log: List<String> = emptyList(),
)

class SyncViewModel(app: Application) : AndroidViewModel(app) {
    private val client = PhoneSyncClient(app)
    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    /** Refresh HC + permission gates; auto-sync if ready and idle. */
    fun onResume() {
        viewModelScope.launch {
            val available = client.isHealthConnectReady()
            val granted = available && client.hasPermissions()
            _state.update { it.copy(hcAvailable = available, permissionsGranted = granted) }
            if (available && granted && beginSyncIfIdle()) runSyncBody()
        }
    }

    fun syncNow() {
        if (_state.value.hcAvailable && _state.value.permissionsGranted && beginSyncIfIdle()) {
            viewModelScope.launch { runSyncBody() }
        }
    }

    /** Called by the Activity after the permission request returns. */
    fun onPermissionsResult() = onResume()

    /**
     * Atomic check-and-set on the single-threaded main dispatcher (viewModelScope =
     * Dispatchers.Main.immediate): returns true and marks syncing exactly once when idle. There is no
     * suspension between the read and the set, so two near-simultaneous callers — e.g. repeatOnLifecycle
     * RESUMED and the permission-result callback both calling onResume() — cannot both start a sync.
     */
    private fun beginSyncIfIdle(): Boolean {
        if (_state.value.syncing) return false
        _state.update { it.copy(syncing = true) }
        return true
    }

    /** Runs a sync pass; assumes the caller already won beginSyncIfIdle(). Resets the flag on exit. */
    private suspend fun runSyncBody() {
        try {
            client.sync { line -> _state.update { s -> s.copy(log = s.log + line) } }
        } finally {
            _state.update { it.copy(syncing = false) }
        }
    }
}
