package com.example.drivesync.ui.sync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import com.example.drivesync.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onNavigateToSetup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SyncViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authMode by viewModel.authMode.collectAsStateWithLifecycle()
    val accountEmail by viewModel.accountEmail.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Drive Sync") },
                actions = {
                    IconButton(onClick = onNavigateToSetup) {
                        Icon(Icons.Filled.Settings, contentDescription = "Configuración")
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
            Spacer(Modifier.height(16.dp))

            when (val s = state) {
                is SyncState.Idle -> IdleContent(
                    authMode = authMode,
                    accountEmail = accountEmail,
                    onStart = viewModel::startSync,
                    onNavigateToSetup = onNavigateToSetup,
                )
                is SyncState.Scanning -> ScanningContent(message = s.message, onCancel = viewModel::cancelSync)
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
private fun IdleContent(
    authMode: String,
    accountEmail: String,
    onStart: () -> Unit,
    onNavigateToSetup: () -> Unit,
) {
    // Mode Badge Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Modo de acceso activo:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val modeTitle = when (authMode) {
                    "PUBLIC" -> "🌐 Carpeta Pública (Sin Login)"
                    "OAUTH" -> "🚀 Cuenta Google (${accountEmail.ifEmpty { "OAuth 2.0" }})"
                    else -> "🔑 API Key Personal"
                }
                Text(
                    modeTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onNavigateToSetup) {
                Text("Cambiar", fontSize = 12.sp, color = DriveBlue)
            }
        }
    }

    Spacer(Modifier.height(24.dp))

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
    RateLimitCard(authMode)
}

@Composable
private fun ScanningContent(message: String, onCancel: () -> Unit) {
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
    Spacer(Modifier.height(32.dp))
    OutlinedButton(
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(Icons.Filled.Stop, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Cancelar análisis")
    }
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
        Text("Cancelar descarga")
    }
}

@Composable
private fun PausedContent(state: SyncState.Paused) {
    Icon(Icons.Filled.Timer, null, Modifier.size(64.dp), tint = StatusPending)
    Spacer(Modifier.height(16.dp))
    Text("Sincronización pausada", fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Reanudando en:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                "${state.remainingMinutes} minutos",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Auto-pausa de seguridad para evitar baneos de IP",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun DoneContent(state: SyncState.Done, onSyncAgain: () -> Unit) {
    Icon(Icons.Filled.CheckCircle, null, Modifier.size(80.dp), tint = StatusOk)
    Spacer(Modifier.height(16.dp))
    Text("¡Sincronización completa!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(24.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Resumen:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            SummaryRow("Archivos descargados", "${state.downloaded}")
            SummaryRow("Archivos ignorados (ya existían)", "${state.skipped}")
            SummaryRow("Errores", "${state.errors}")
            SummaryRow("Total transferido", formatSize(state.downloadedBytes))
            SummaryRow("Tiempo total", "${state.durationSeconds}s")
            SummaryRow("Estructura Drive", "${state.totalDriveFiles} archivos, ${state.totalDriveFolders} carpetas")
        }
    }

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = onSyncAgain,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DriveBlue),
    ) {
        Icon(Icons.Filled.Sync, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Sincronizar de nuevo", fontSize = 16.sp)
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Icon(Icons.Filled.Error, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.error)
    Spacer(Modifier.height(16.dp))
    Text("Error de sincronización", fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
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
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DriveBlue),
    ) {
        Icon(Icons.Filled.Refresh, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Reintentar", fontSize = 16.sp)
    }
}

@Composable
private fun StatChip(value: String, label: String, color: Color) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 11.sp, color = color)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RateLimitCard(authMode: String = "PUBLIC") {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            val (title, text) = when (authMode) {
                "API_KEY" -> Pair(
                    "🔑 Modo API Key (Protección Anti-Ban)",
                    "15-25s entre descargas para evitar suspensiones de cuota por IP/Key."
                )
                "OAUTH" -> Pair(
                    "🚀 Modo OAuth 2.0 (Máxima Velocidad)",
                    "Descargas directas e instantáneas sin esperas artificiales."
                )
                else -> Pair(
                    "🌐 Modo Carpeta Pública (Sin Login)",
                    "0 inicio de sesión requerido • Descargas a máxima velocidad."
                )
            }
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
