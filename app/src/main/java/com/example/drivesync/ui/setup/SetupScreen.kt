package com.example.drivesync.ui.setup

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

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

    val clientId = "997977700161-bi5qhdmb6ic64uaikbkjjgu2j5onisrc.apps.googleusercontent.com"

    // Native Google Sign-In setup
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId)
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.readonly"))
            .requestEmail()
            .build()
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            if (account != null) {
                val acc = account.account ?: android.accounts.Account(account.email ?: "", "com.google")
                viewModel.onGoogleSignInSuccess(acc, account.email ?: "Cuenta de Google")
            } else {
                viewModel.setAuthError("No se pudo obtener la cuenta de Google.")
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            // If native sign-in fails due to status 10 (unregistered app), launch Web OAuth
            showWebOAuthDialog = true
        } catch (e: Exception) {
            showWebOAuthDialog = true
        }
    }

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

    // In-App Web OAuth Dialog
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
                title = { Text("Drive Sync") },
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

            // Header
            Icon(
                Icons.Filled.Sync,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = DriveBlue,
            )
            Spacer(Modifier.height(8.dp))
            Text("Configuración", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "Conecta tu cuenta de Google u obtén acceso para sincronizar",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(20.dp))

            // Storage permission card (Android 11+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasStoragePermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Permiso de almacenamiento requerido",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Para guardar archivos en la carpeta de Descargas, necesitas conceder acceso al almacenamiento.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
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
                ) {
                    Icon(Icons.Filled.Public, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pública", fontSize = 11.sp)
                }
                SegmentedButton(
                    selected = state.authMode == "OAUTH",
                    onClick = { viewModel.setAuthMode("OAUTH") },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                ) {
                    Icon(Icons.Filled.AccountCircle, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("OAuth", fontSize = 11.sp)
                }
                SegmentedButton(
                    selected = state.authMode == "API_KEY",
                    onClick = { viewModel.setAuthMode("API_KEY") },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                ) {
                    Icon(Icons.Filled.Key, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("API Key", fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (state.authMode == "PUBLIC") {
                // Public mode card
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Public, null, tint = DriveBlue, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Modo Carpeta Pública (Sin Login)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Ideal para descargar carpetas públicas compartidas por enlace. 0 inicio de sesión requerido, usuarios ilimitados.",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (state.authMode == "OAUTH") {
                // OAuth 2.0 Google Sign-In Card
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
                                Icon(Icons.Filled.CheckCircle, null, tint = StatusOk, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Conectado: ${state.accountEmail}",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "✅ OAuth 2.0 activo. Sin congelamiento de cuota.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { showWebOAuthDialog = true },
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("Reconectar cuenta de Google")
                            }
                        } else {
                            Text(
                                "Inicia sesión con Google para descargas continuas sin congelamiento.",
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    showWebOAuthDialog = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DriveBlue),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.Language, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Iniciar sesión con Google (Web)")
                            }

                            Spacer(Modifier.height(12.dp))

                            Text(
                                "o ingresa el Token OAuth manualmente:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = state.oauthToken,
                                onValueChange = viewModel::updateOAuthToken,
                                label = { Text("OAuth 2.0 Bearer Token") },
                                leadingIcon = { Icon(Icons.Filled.Key, null) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    }
                }
            } else {
                // API Key field
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = viewModel::updateApiKey,
                    label = { Text("API Key de Google (Opcional/Personal)") },
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
                label = { Text("URL de carpeta de Drive") },
                leadingIcon = { Icon(Icons.Filled.Cloud, null) },
                placeholder = { Text("https://drive.google.com/drive/folders/...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(16.dp))

            // Local path field with folder picker button
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

            Spacer(Modifier.height(4.dp))

            Text(
                "Por defecto: Descargas/Tamashis Project",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Error message
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

            // Save button
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

            Spacer(Modifier.height(24.dp))

            // Rate limiting & Mode info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    val title = when (state.authMode) {
                        "PUBLIC" -> "🌐 Modo Carpeta Pública (Sin Login)"
                        "OAUTH" -> "🚀 Modo Cuenta Google (OAuth 2.0)"
                        else -> "🔑 Modo API Key Personal (Con Anti-Ban)"
                    }
                    Text(
                        title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    when (state.authMode) {
                        "PUBLIC" -> {
                            Text("• Alcance: Enlaces públicos compartidos (0 login requerido).", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("• Ritmo adaptativo inteligente (~500ms) para evitar bloqueos por ráfagas anónimas de Google.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("⚠️ Advertencia: Google limita las ráfagas anónimas. Recomendado usar OAuth para sincronizaciones de cientos de archivos.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f), fontWeight = FontWeight.Medium)
                        }
                        "OAUTH" -> {
                            Text("• Alcance: Recomendado para sincronización completa e ilimitada.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("• 0 esperas artificiales (descargas directas a velocidad máxima de tu conexión).", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("✅ Autenticado con tu cuenta de Google • Sin cuotas ni bloqueos por IP.", fontSize = 12.sp, color = StatusOk, fontWeight = FontWeight.Medium)
                        }
                        else -> {
                            Text("• Alcance: Uso con tu propia API Key de Google Cloud.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("• Protección Anti-Ban activa de 15-25s entre descargas.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("⚠️ Advertencia: Tráfico anónimo limitado por Google (~32 descargas continuas por ráfaga).", fontSize = 12.sp, color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f), fontWeight = FontWeight.Medium)
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
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Iniciar sesión con Google",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }
                Divider()

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    checkUrl(url)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                    checkUrl(url)
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val url = request?.url?.toString() ?: ""
                                    return checkUrl(url)
                                }

                                private fun checkUrl(url: String?): Boolean {
                                    if (url == null) return false
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

                            val clientId = "997977700161-bi5qhdmb6ic64uaikbkjjgu2j5onisrc.apps.googleusercontent.com"
                            val oauthUrl = "https://accounts.google.com/o/oauth2/v2/auth" +
                                    "?client_id=$clientId" +
                                    "&redirect_uri=https://oauth.pstmn.io/v1/browser-callback" +
                                    "&response_type=token" +
                                    "&scope=https://www.googleapis.com/auth/drive.readonly"

                            loadUrl(oauthUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
