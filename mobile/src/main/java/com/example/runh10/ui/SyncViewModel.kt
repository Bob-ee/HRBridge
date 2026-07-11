package com.example.runh10.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.runh10.data.RunRepository
import com.example.runh10.healthconnect.RestingHrUpdater
import com.example.runh10.record.PhoneRecordController
import com.example.runh10.sync.PhoneSyncClient
import kotlinx.coroutines.Job
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
    private val repo = RunRepository.get(app)
    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    // Started once, at ViewModel construction (i.e. process/first-screen startup) — a mid-run
    // process death leaves a meta sidecar + ndjson with no Room row (F1). onResume() joins this
    // before syncing so a recovered run is already in Room when sync updates the feed.
    private val recoveryJob: Job = viewModelScope.launch {
        runCatching { repo.recoverOrphans(PhoneRecordController.activeSessionId) }
    }

    // Same one-shot-per-process idiom as recoveryJob: fills in kcal for runs ingested
    // before the athlete's body profile was set (or before this feature existed). Cheap
    // and idempotent (rows with a real kcal are skipped), so running it once per process
    // launch — rather than gating on some "have I ever run this" flag — is simplest.
    private val calorieBackfillJob: Job = viewModelScope.launch {
        runCatching { repo.backfillCalories() }
    }

    /** Refresh HC + permission gates; auto-sync if ready and idle. */
    fun onResume() {
        viewModelScope.launch {
            recoveryJob.join()
            // Cheap and self-gated (toggle + its own permission check inside), so this can
            // join the startup chain unconditionally — same idiom as the recovery/repush
            // calls that bracket it. Covers the case where the phone was asleep overnight
            // and the WorkManager job hasn't fired yet by the time the user opens the app.
            runCatching { RestingHrUpdater.checkOnce(getApplication()) }
            val available = client.isHealthConnectReady()
            val granted = available && client.hasPermissions()
            _state.update { it.copy(hcAvailable = available, permissionsGranted = granted) }
            if (available && granted) {
                // Re-push phone-recorded runs whose original HC write failed (F5) — safe to
                // retry on every resume: idempotent, and a no-op once nothing is pending.
                runCatching { repo.repushHealthConnect(getApplication<Application>()) }
                if (beginSyncIfIdle()) runSyncBody()
            }
        }
    }

    fun syncNow() {
        if (_state.value.hcAvailable && _state.value.permissionsGranted && beginSyncIfIdle()) {
            viewModelScope.launch {
                recoveryJob.join()
                runCatching { repo.repushHealthConnect(getApplication<Application>()) }
                runSyncBody()
            }
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
