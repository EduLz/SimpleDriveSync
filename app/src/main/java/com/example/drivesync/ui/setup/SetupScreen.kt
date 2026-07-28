package com.example.drivesync.ui.setup

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.drivesync.theme.DriveBlue
import com.example.drivesync.theme.StatusOk

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetupViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showWebOAuthDialog by remember { mutableStateOf(false) }

    var hasStoragePermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true
            }
        )
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onFolderSelected(uri)
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasStoragePermission = Environment.isExternalStorageManager()
        }
    }

    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) {
            viewModel.consumeSavedSuccessfully()
            onSetupComplete()
        }
    }

    if (showWebOAuthDialog) {
        WebOAuthDialog(
            onTokenCaptured = { token ->
                viewModel.onWebTokenCaptured(token)
                showWebOAuthDialog = false
            },
            onDismiss = { showWebOAuthDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
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

            if (!hasStoragePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Permiso de Almacenamiento Requerido",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Para guardar archivos en la memoria interna, concede el permiso de acceso total a archivos.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                storagePermissionLauncher.launch(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Filled.Security, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Conceder permiso")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Auth Mode Selection (PUBLIC, OAUTH, API_KEY)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.authMode == "PUBLIC",
                    onClick = { viewModel.setAuthMode("PUBLIC") },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    icon = {},
                    label = { Text("Pública", fontSize = 12.sp) }
                )
                SegmentedButton(
                    selected = state.authMode == "OAUTH",
                    onClick = { viewModel.setAuthMode("OAUTH") },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    icon = {},
                    label = { Text("OAuth 2.0", fontSize = 12.sp) }
                )
                SegmentedButton(
                    selected = state.authMode == "API_KEY",
                    onClick = { viewModel.setAuthMode("API_KEY") },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    icon = {},
                    label = { Text("API Key", fontSize = 12.sp) }
                )
            }

            Spacer(Modifier.height(16.dp))

            if (state.authMode == "PUBLIC") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Carpeta Pública (Sin Login)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Permite descargar carpetas compartidas públicas por enlace sin inicio de sesión.",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (state.authMode == "OAUTH") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (state.accountEmail.isNotBlank() && state.oauthToken.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, null, tint = StatusOk, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Conectado: ${state.accountEmail}",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showWebOAuthDialog = true },
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("Reconectar cuenta")
                            }
                        } else {
                            Text(
                                "Inicia sesión para descargas continuas de alta velocidad.",
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { showWebOAuthDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DriveBlue),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.Language, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Iniciar sesión con Google")
                            }

                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value = state.oauthToken,
                                onValueChange = viewModel::updateOAuthToken,
                                label = { Text("Bearer Token manual (opcional)") },
                                leadingIcon = { Icon(Icons.Filled.Key, null) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = viewModel::updateApiKey,
                    label = { Text("API Key de Google Cloud") },
                    leadingIcon = { Icon(Icons.Filled.Key, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Drive URL field
            OutlinedTextField(
                value = state.driveUrl,
                onValueChange = viewModel::updateDriveUrl,
                label = { Text("URL de la carpeta de Drive") },
                leadingIcon = { Icon(Icons.Filled.Cloud, null) },
                placeholder = { Text("https://drive.google.com/drive/folders/...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(16.dp))

            // Local path field
            OutlinedTextField(
                value = state.localPath,
                onValueChange = viewModel::updateLocalPath,
                label = { Text("Carpeta local de destino") },
                leadingIcon = { Icon(Icons.Filled.FolderOpen, null) },
                trailingIcon = {
                    IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                        Icon(
                            Icons.Filled.FolderOpen,
                            contentDescription = "Seleccionar carpeta",
                            tint = DriveBlue,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 2,
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(4.dp))

            OutlinedButton(
                onClick = { folderPickerLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Filled.FolderOpen, null, Modifier.size(18.dp), tint = DriveBlue)
                Spacer(Modifier.width(8.dp))
                Text("Seleccionar carpeta", fontSize = 14.sp)
            }

            Spacer(Modifier.height(16.dp))

            // Periodic Schedule Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Sincronización Programada en Segundo Plano", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = state.syncInterval == "OFF",
                            onClick = { viewModel.setSyncInterval("OFF") },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4),
                            icon = {},
                            label = { Text("Off", fontSize = 11.sp) }
                        )
                        SegmentedButton(
                            selected = state.syncInterval == "6H",
                            onClick = { viewModel.setSyncInterval("6H") },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4),
                            icon = {},
                            label = { Text("6 hrs", fontSize = 11.sp) }
                        )
                        SegmentedButton(
                            selected = state.syncInterval == "12H",
                            onClick = { viewModel.setSyncInterval("12H") },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4),
                            icon = {},
                            label = { Text("12 hrs", fontSize = 11.sp) }
                        )
                        SegmentedButton(
                            selected = state.syncInterval == "24H",
                            onClick = { viewModel.setSyncInterval("24H") },
                            shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4),
                            icon = {},
                            label = { Text("Diario", fontSize = 11.sp) }
                        )
                    }

                    if (state.syncInterval != "OFF") {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Sincronizar solo con conexión Wi-Fi", fontSize = 13.sp)
                            Switch(
                                checked = state.wifiOnly,
                                onCheckedChange = viewModel::setWifiOnly
                            )
                        }
                    }
                }
            }

            state.errorMessage?.let { error ->
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 14.sp,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = viewModel::saveAndValidate,
                enabled = state.isValid && !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DriveBlue),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Filled.Sync, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Guardar y Sincronizar", fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            // Mode info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    val title = when (state.authMode) {
                        "PUBLIC" -> "Modo Carpeta Pública"
                        "OAUTH" -> "Modo Cuenta Google (OAuth 2.0)"
                        else -> "Modo API Key Personal"
                    }
                    Text(
                        title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    when (state.authMode) {
                        "PUBLIC" -> {
                            Text("Acceso por enlace público sin inicio de sesión.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Aplica ritmo adaptativo (~500ms) para mantener la estabilidad de descargas.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        "OAUTH" -> {
                            Text("Recomendado para descargas continuas sin pausas artificiales.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Autenticado mediante cuenta personal de Google.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> {
                            Text("Uso avanzado con API Key de Google Cloud.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Protección anti-ban activa de 15-25s entre descargas.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun WebOAuthDialog(
    onTokenCaptured: (token: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Iniciar sesión con Google", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = DriveBlue)
                }

                val authUrl = "https://accounts.google.com/o/oauth2/v2/auth?" +
                        "client_id=997977700161-bi5qhdmb6ic64uaikbkjjgu2j5onisrc.apps.googleusercontent.com&" +
                        "redirect_uri=https://oauth.pstmn.io/v1/browser-callback&" +
                        "response_type=token&" +
                        "scope=https://www.googleapis.com/auth/drive.readonly"

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    if (url.contains("access_token=")) {
                                        val token = url.substringAfter("access_token=").substringBefore("&")
                                        if (token.isNotBlank()) {
                                            onTokenCaptured(token)
                                            return true
                                        }
                                    }
                                    return false
                                }
                            }
                            loadUrl(authUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
