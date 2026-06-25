package com.example.runh10.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SyncScreen(
    vm: SyncViewModel,
    onRequestPermissions: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        when {
            !state.hcAvailable ->
                Text("Health Connect not available — install/update it to sync.")
            !state.permissionsGranted ->
                Button(onClick = onRequestPermissions) { Text("Grant Health Connect access") }
            else ->
                Button(onClick = vm::syncNow, enabled = !state.syncing) {
                    Text(if (state.syncing) "Syncing…" else "Sync now")
                }
        }
        LazyColumn(Modifier.padding(top = 16.dp)) {
            items(state.log) { line -> Text(line) }
        }
    }
}
