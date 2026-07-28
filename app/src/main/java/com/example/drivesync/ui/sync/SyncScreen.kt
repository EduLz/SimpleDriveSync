package com.example.drivesync.ui.sync

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.drivesync.data.DriveItem
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
    val context = LocalContext.current

    var showCancelDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Processed silently */ }

    fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("¿Cancelar sincronización?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "¿Estás seguro de cancelar? Los datos no descargados completamente podrían necesitar descargarse de nuevo.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        viewModel.cancelSync()
                    }
                ) {
                    Text("Sí, cancelar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Continuar sincronización")
                }
            },
            shape = RoundedCornerShape(16.dp),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SimpleDriveSync") },
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
        val isFileSelection = state is SyncState.FileSelection

        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isFileSelection) {
                FileSelectionContent(
                    state = state as SyncState.FileSelection,
                    onToggleFile = viewModel::toggleFileSelection,
                    onSelectAll = viewModel::selectAllFiles,
                    onConfirmDownload = viewModel::startDownloadSelected,
                    onRequestCancel = { showCancelDialog = true },
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(16.dp))

                    when (val s = state) {
                        is SyncState.Idle -> IdleContent(
                            authMode = authMode,
                            accountEmail = accountEmail,
                            onStart = {
                                checkNotificationPermission()
                                viewModel.startSync()
                            },
                            onNavigateToSetup = onNavigateToSetup,
                        )
                        is SyncState.Scanning -> ScanningContent(
                            message = s.message,
                            onRequestCancel = { showCancelDialog = true }
                        )
                        is SyncState.Syncing -> SyncingContent(
                            state = s,
                            onRequestCancel = { showCancelDialog = true }
                        )
                        is SyncState.Paused -> PausedContent(s)
                        is SyncState.Done -> DoneContent(s, onSyncAgain = {
                            checkNotificationPermission()
                            viewModel.startSync()
                        })
                        is SyncState.Error -> ErrorContent(s.message, onRetry = {
                            checkNotificationPermission()
                            viewModel.startSync()
                        })
                        else -> {}
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
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
                    "PUBLIC" -> "Carpeta Pública (Sin Login)"
                    "OAUTH" -> "Cuenta Google (${accountEmail.ifEmpty { "OAuth 2.0" }})"
                    else -> "API Key Personal"
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
        "Escanea Drive y selecciona qué archivos nuevos deseas descargar",
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
private fun ScanningContent(message: String, onRequestCancel: () -> Unit) {
    Spacer(Modifier.height(40.dp))
    CircularProgressIndicator(
        modifier = Modifier.size(64.dp),
        color = DriveBlue,
        strokeWidth = 4.dp,
    )
    Spacer(Modifier.height(24.dp))
    Text("Analizando Google Drive...", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(
        message,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(32.dp))
    OutlinedButton(
        onClick = onRequestCancel,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
    ) {
        Icon(Icons.Filled.Cancel, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Cancelar análisis")
    }
}

@Composable
private fun FileSelectionContent(
    state: SyncState.FileSelection,
    onToggleFile: (fileId: String) -> Unit,
    onSelectAll: (select: Boolean) -> Unit,
    onConfirmDownload: () -> Unit,
    onRequestCancel: () -> Unit,
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val selectedCount = state.newFiles.count { it.isSelected }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("Nuevos (${state.newFiles.size})", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text("En dispositivo (${state.existingFiles.size})") }
            )
        }

        if (selectedTabIndex == 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$selectedCount de ${state.newFiles.size} seleccionados",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Row {
                    TextButton(onClick = { onSelectAll(true) }) {
                        Text("Todos", fontSize = 12.sp)
                    }
                    TextButton(onClick = { onSelectAll(false) }) {
                        Text("Ninguno", fontSize = 12.sp)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.newFiles, key = { it.item.id }) { selectable ->
                    Card(
                        onClick = { onToggleFile(selectable.item.id) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectable.isSelected)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectable.isSelected,
                                onCheckedChange = { onToggleFile(selectable.item.id) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    selectable.item.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    selectable.item.path,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                formatSize(selectable.item.size),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DriveBlue
                            )
                        }
                    }
                }
            }

            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = onConfirmDownload,
                        enabled = selectedCount > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DriveBlue)
                    ) {
                        Icon(Icons.Filled.Download, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Descargar $selectedCount (${formatSize(state.totalSelectedBytes)})",
                            fontSize = 15.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onRequestCancel,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancelar", fontSize = 14.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.existingFiles, key = { it.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                null,
                                tint = StatusOk,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.name,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "Ya en dispositivo • ${formatSize(item.size)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncingContent(
    state: SyncState.Syncing,
    onRequestCancel: () -> Unit,
) {
    val filePercent = if (state.fileTotalBytes > 0) {
        ((state.fileDownloadedBytes * 100) / state.fileTotalBytes).toInt().coerceIn(0, 100)
    } else 0

    val totalPercent = if (state.totalFiles > 0) {
        ((state.downloaded * 100) / state.totalFiles).coerceIn(0, 100)
    } else 0

    Spacer(Modifier.height(16.dp))

    Text("Sincronizando archivos...", fontSize = 20.sp, fontWeight = FontWeight.Bold)

    Spacer(Modifier.height(16.dp))

    LinearProgressIndicator(
        progress = { totalPercent / 100f },
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp),
        color = DriveBlue,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )

    Spacer(Modifier.height(8.dp))

    Text(
        "$totalPercent% completado (${state.downloaded} de ${state.totalFiles})",
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )

    Spacer(Modifier.height(24.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Descargando:",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                state.currentFile,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (state.fileTotalBytes > 0) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { filePercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = DriveBlue,
                    trackColor = MaterialTheme.colorScheme.surface,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${formatSize(state.fileDownloadedBytes)} de ${formatSize(state.fileTotalBytes)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DriveBlue
                    )
                    Text("$filePercent%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (state.waitingMessage.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    state.waitingMessage,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatBadge("Descargados", "${state.downloaded}", StatusOk)
        StatBadge("Omisiones", "${state.skipped}", DriveBlue)
        StatBadge("Errores", "${state.errors}", MaterialTheme.colorScheme.error)
    }

    Spacer(Modifier.height(24.dp))

    OutlinedButton(
        onClick = onRequestCancel,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Icon(Icons.Filled.Cancel, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Cancelar descarga")
    }
}

@Composable
private fun PausedContent(s: SyncState.Paused) {
    Spacer(Modifier.height(40.dp))
    Icon(Icons.Filled.Timer, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary)
    Spacer(Modifier.height(16.dp))
    Text("Sincronización Pausada", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(s.reason, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    Text(
        "Se reanudará automáticamente en ${s.remainingMinutes} min",
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun DoneContent(s: SyncState.Done, onSyncAgain: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Icon(Icons.Filled.CheckCircle, null, Modifier.size(72.dp), tint = StatusOk)
    Spacer(Modifier.height(16.dp))
    Text("¡Sincronización Completa!", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(16.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            SummaryRow("Archivos en Drive", "${s.totalDriveFiles}")
            SummaryRow("Carpetas en Drive", "${s.totalDriveFolders}")
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SummaryRow("Descargados", "${s.downloaded}")
            SummaryRow("Sin cambios (Omisiones)", "${s.skipped}")
            SummaryRow("Errores", "${s.errors}")
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SummaryRow("Datos descargados", formatSize(s.downloadedBytes))
            SummaryRow("Tiempo total", "${s.durationSeconds} seg")
        }
    }

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = onSyncAgain,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DriveBlue),
    ) {
        Icon(Icons.Filled.Sync, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Volver a Sincronizar")
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Spacer(Modifier.height(40.dp))
    Icon(Icons.Filled.Error, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
    Spacer(Modifier.height(16.dp))
    Text("Error de Sincronización", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(
        message,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onRetry,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DriveBlue),
    ) {
        Text("Reintentar")
    }
}

@Composable
private fun StatBadge(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RateLimitCard(authMode: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            val (title, body) = when (authMode) {
                "PUBLIC" -> Pair(
                    "Modo Carpeta Pública Active",
                    "Ritmo adaptativo activo (~500ms) para garantizar descargas sin bloqueos."
                )
                "OAUTH" -> Pair(
                    "Modo OAuth 2.0 Activo",
                    "Descargas continuas sin pausas artificiales conectadas a tu cuenta de Google."
                )
                else -> Pair(
                    "Modo API Key Activo",
                    "Pausa de seguridad de 15-25 segundos entre descargas activada."
                )
            }
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(body, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
}
