package com.example.drivesync.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.drivesync.theme.DriveBlue
import com.example.drivesync.theme.StatusError
import com.example.drivesync.theme.StatusOk
import com.example.drivesync.theme.StatusPending
import com.example.drivesync.theme.StatusSkipped

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
    else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "${m}m ${s}s"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onNavigateToSetup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SyncViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Drive Sync") },
                actions = {
                    IconButton(onClick = onNavigateToSetup) {
                        Icon(Icons.Filled.Settings, "Configuración")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))

            when (val s = state) {
                is SyncState.Idle -> IdleContent(onStart = viewModel::startSync)
                is SyncState.Scanning -> ScanningContent(message = s.message)
                is SyncState.Syncing -> SyncingContent(s, onCancel = viewModel::cancelSync)
                is SyncState.Paused -> PausedContent(s)
                is SyncState.Done -> DoneContent(s, onSyncAgain = viewModel::startSync)
                is SyncState.Error -> ErrorContent(s.message, onRetry = viewModel::startSync)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun IdleContent(onStart: () -> Unit) {
    Icon(Icons.Filled.CloudSync, null, Modifier.size(80.dp), tint = DriveBlue)
    Spacer(Modifier.height(16.dp))
    Text("Listo para sincronizar", fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(
        "Descarga solo los archivos que no existen localmente",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(32.dp))
    Button(
        onClick = onStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DriveBlue),
    ) {
        Icon(Icons.Filled.Sync, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Iniciar Sincronización", fontSize = 16.sp)
    }
    Spacer(Modifier.height(16.dp))
    RateLimitCard()
}

@Composable
private fun ScanningContent(message: String) {
    Spacer(Modifier.height(40.dp))
    CircularProgressIndicator(
        modifier = Modifier.size(64.dp),
        color = DriveBlue,
        strokeWidth = 4.dp,
    )
    Spacer(Modifier.height(24.dp))
    Text("Analizando...", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Text(
        message,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SyncingContent(state: SyncState.Syncing, onCancel: () -> Unit) {
    val progress = if (state.totalFiles > 0) {
        (state.downloaded + state.errors).toFloat() / state.totalFiles
    } else 0f

    Text("Descargando archivos", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(16.dp))

    // Progress bar
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp),
        color = DriveBlue,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "${(progress * 100).toInt()}% — ${state.downloaded + state.errors}/${state.totalFiles}",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(16.dp))

    // Current file
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Descargando:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                state.currentFile,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                state.waitingMessage,
                fontSize = 12.sp,
                color = StatusPending,
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    // Stats row
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        StatChip("${state.downloaded}", "Descargados", StatusOk)
        StatChip("${state.skipped}", "Saltados", StatusSkipped)
        StatChip("${state.errors}", "Errores", StatusError)
    }

    Spacer(Modifier.height(8.dp))
    Text(
        "Descargado: ${formatSize(state.downloadedBytes)}",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(24.dp))

    OutlinedButton(
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(Icons.Filled.Stop, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Cancelar")
    }
}

@Composable
private fun PausedContent(state: SyncState.Paused) {
    Icon(Icons.Filled.PauseCircle, null, Modifier.size(64.dp), tint = StatusPending)
    Spacer(Modifier.height(16.dp))
    Text("Pausa de Seguridad", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(
        state.reason,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = StatusPending.copy(alpha = 0.1f)),
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${state.remainingMinutes} min restantes",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = StatusPending,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Reanudación automática",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Progreso: ${state.syncProgress.downloaded}/${state.syncProgress.totalFiles}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DoneContent(state: SyncState.Done, onSyncAgain: () -> Unit) {
    val icon = if (state.errors == 0) Icons.Filled.CheckCircle else Icons.Filled.Warning
    val color = if (state.errors == 0) StatusOk else StatusPending

    Icon(icon, null, Modifier.size(80.dp), tint = color)
    Spacer(Modifier.height(16.dp))
    Text(
        if (state.errors == 0) "Sincronización Completa!" else "Completado con errores",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Duración: ${formatDuration(state.durationSeconds)}",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(24.dp))

    // Stats card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Resumen", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            StatRow("Drive", "${state.totalDriveFolders} carpetas, ${state.totalDriveFiles} archivos")
            StatRow("Descargados", "${state.downloaded} (${formatSize(state.downloadedBytes)})")
            StatRow("Saltados", "${state.skipped}")
            if (state.errors > 0) StatRow("Errores", "${state.errors}")
        }
    }

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = onSyncAgain,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DriveBlue),
    ) {
        Icon(Icons.Filled.Sync, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Sincronizar de nuevo")
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Icon(Icons.Filled.ErrorOutline, null, Modifier.size(64.dp), tint = StatusError)
    Spacer(Modifier.height(16.dp))
    Text("Error", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontSize = 14.sp,
        )
    }
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onRetry,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Icon(Icons.Filled.Refresh, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Reintentar")
    }
}

@Composable
private fun StatChip(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RateLimitCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("⚡ Modo Prueba Rápido activo", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "0.5-1.5s entre descargas • Auto-pausa 30 min ante error 403",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
