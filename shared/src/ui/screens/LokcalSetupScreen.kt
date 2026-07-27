package com.emilflach.groceries.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emilflach.groceries.lokcal.SyncResult
import com.emilflach.groceries.viewmodel.LokcalSetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LokcalSetupScreen(
    viewModel: LokcalSetupViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lokcal setup") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (uiState.folderConfigured) "Backup folder configured" else "No backup folder configured yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                uiState.lastSyncedAt?.let { "Last synced: $it" } ?: "Never synced",
                style = MaterialTheme.typography.bodyMedium,
            )

            Button(onClick = viewModel::chooseFolder, enabled = !uiState.isBusy) {
                Text(if (uiState.folderConfigured) "Change backup folder" else "Choose backup folder")
            }

            if (uiState.folderConfigured) {
                OutlinedButton(onClick = viewModel::syncNow, enabled = !uiState.isBusy) {
                    Text("Sync now")
                }
            }

            OutlinedButton(onClick = viewModel::importFromFile, enabled = !uiState.isBusy) {
                Text("Import a single .db file instead")
            }

            uiState.lastResult?.let { result ->
                Text(
                    when (result) {
                        SyncResult.Success -> "Synced successfully"
                        SyncResult.NoFolderConfigured -> "No folder configured"
                        SyncResult.NoBackupFileFound -> "No backup file found in that folder"
                        is SyncResult.Failed -> "Failed: ${result.message}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (uiState.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
