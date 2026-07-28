package com.emilflach.groceries.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emilflach.groceries.lokcal.SyncResult
import com.emilflach.groceries.ui.util.PlatformBackHandler
import com.emilflach.groceries.viewmodel.LokcalSetupViewModel

@Composable
fun LokcalSetupScreen(
    viewModel: LokcalSetupViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    // Route the system back gesture to the same destination as the on-screen back button, so it
    // returns to the list instead of falling through to the OS and closing the app.
    PlatformBackHandler { onBack() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Lokcal setup",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            // Status card
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    Icon(
                        if (uiState.folderConfigured) Icons.Filled.CheckCircle else Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = if (uiState.folderConfigured) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = if (uiState.folderConfigured) "Backup folder configured" else "No backup folder yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = uiState.lastSyncedAt?.let { "Last synced: $it" } ?: "Never synced",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Button(
                onClick = viewModel::chooseFolder,
                enabled = !uiState.isBusy,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (uiState.folderConfigured) "Change backup folder" else "Choose backup folder",
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (uiState.folderConfigured) {
                OutlinedButton(
                    onClick = viewModel::syncNow,
                    enabled = !uiState.isBusy,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Sync now") }
            }

            OutlinedButton(
                onClick = viewModel::importFromFile,
                enabled = !uiState.isBusy,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import a single .db file instead") }

            uiState.lastResult?.let { result ->
                Text(
                    text = when (result) {
                        SyncResult.Success -> "Synced successfully"
                        SyncResult.NoFolderConfigured -> "No folder configured"
                        SyncResult.NoBackupFileFound -> "No backup file found in that folder"
                        is SyncResult.Failed -> "Failed: ${result.message}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (result is SyncResult.Failed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }

            if (uiState.isBusy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Working…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
