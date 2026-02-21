package passkeytools.wuyuan.dev.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import passkeytools.wuyuan.dev.crypto.KeyManager
import passkeytools.wuyuan.dev.model.PasskeyEntity
import passkeytools.wuyuan.dev.ui.viewmodel.CredentialDetailViewModel
import java.security.SecureRandom

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialEditScreen(
    credentialId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CredentialDetailViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Local mutable state initialized from loaded passkey
    var rpId by remember { mutableStateOf("") }
    var rpName by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var userDisplayName by remember { mutableStateOf("") }
    var privateKeyPkcs8 by remember { mutableStateOf("") }
    var publicKeyX509 by remember { mutableStateOf("") }
    var publicKeyCose by remember { mutableStateOf("") }
    var counter by remember { mutableStateOf("0") }
    var sourcePackage by remember { mutableStateOf("") }
    var origin by remember { mutableStateOf("") }
    var backupEligible by remember { mutableStateOf(true) }
    var backupState by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }
    var keyError by remember { mutableStateOf<String?>(null) }
    var showRegenerateDialog by remember { mutableStateOf(false) }

    val passkey = uiState.passkey

    // Initialize fields when passkey is loaded
    LaunchedEffect(passkey) {
        if (passkey != null && !initialized) {
            rpId = passkey.rpId
            rpName = passkey.rpName
            userId = passkey.userId
            userName = passkey.userName
            userDisplayName = passkey.userDisplayName
            privateKeyPkcs8 = passkey.privateKeyPkcs8
            publicKeyX509 = passkey.publicKeyX509
            publicKeyCose = passkey.publicKeyCose
            counter = passkey.counter.toString()
            sourcePackage = passkey.sourcePackage
            origin = passkey.origin
            backupEligible = passkey.backupEligible
            backupState = passkey.backupState
            initialized = true
        }
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
            viewModel.clearSaveSuccess()
            onSaved()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { Toast.makeText(context, "错误: $it", Toast.LENGTH_LONG).show() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑凭据") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            // Validate key format before saving
                            keyError = null
                            val counterLong = counter.toLongOrNull()
                            if (counterLong == null) {
                                Toast.makeText(context, "计数器必须为整数", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            if (privateKeyPkcs8.isBlank()) {
                                keyError = "私钥不能为空"
                                return@IconButton
                            }
                            // Try parsing private key
                            try {
                                KeyManager.importPrivateKey(privateKeyPkcs8)
                            } catch (e: Exception) {
                                keyError = "私钥格式错误: ${e.message}"
                                return@IconButton
                            }
                            // Recompute COSE key if public key changed
                            val newCose = if (publicKeyX509 != passkey?.publicKeyX509) {
                                try {
                                    val coseBytes = KeyManager.encodeCosePublicKey(publicKeyX509)
                                    android.util.Base64.encodeToString(coseBytes, android.util.Base64.NO_WRAP)
                                } catch (e: Exception) {
                                    publicKeyCose // keep existing if can't recompute
                                }
                            } else publicKeyCose

                            viewModel.savePasskey(
                                (passkey ?: return@IconButton).copy(
                                    rpId = rpId,
                                    rpName = rpName,
                                    userId = userId,
                                    userName = userName,
                                    userDisplayName = userDisplayName,
                                    privateKeyPkcs8 = privateKeyPkcs8,
                                    publicKeyX509 = publicKeyX509,
                                    publicKeyCose = newCose,
                                    counter = counterLong,
                                    sourcePackage = sourcePackage,
                                    origin = origin,
                                    backupEligible = backupEligible,
                                    backupState = backupState,
                                )
                            )
                        }
                    ) {
                        Icon(Icons.Default.Save, null)
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (passkey == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("未找到凭据")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                EditSection("基本信息") {
                    EditTextField("RP ID", rpId, "android:apk-key-hash:... 或 example.com") { rpId = it }
                    EditTextField("RP 名称", rpName, "Example Site") { rpName = it }
                    EditTextField("来源应用包名", sourcePackage, "com.example.app") { sourcePackage = it }
                    EditTextField("Origin", origin, "android:apk-key-hash:...") { origin = it }
                }

                EditSection("用户信息") {
                    EditTextField("用户 ID (Base64URL)", userId, "") { userId = it }
                    EditTextField("用户名", userName, "user@example.com") { userName = it }
                    EditTextField("显示名称", userDisplayName, "张三") { userDisplayName = it }
                }

                EditSection("密钥") {
                    // Private key field with regenerate option
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("私钥 (PKCS#8 Base64)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(
                                onClick = { showRegenerateDialog = true },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("重新生成密钥对", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        OutlinedTextField(
                            value = privateKeyPkcs8,
                            onValueChange = { privateKeyPkcs8 = it; keyError = null },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            isError = keyError != null,
                            supportingText = keyError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                            minLines = 3,
                            maxLines = 6,
                        )
                    }
                    EditTextField("公钥 (X.509 Base64)", publicKeyX509, "", maxLines = 3) { publicKeyX509 = it }
                }

                EditSection("调试参数") {
                    EditTextField(
                        "签名计数器",
                        counter,
                        "0",
                        keyboardType = KeyboardType.Number
                    ) { counter = it }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("备份资格 (BE)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = backupEligible, onCheckedChange = { backupEligible = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("备份状态 (BS)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = backupState, onCheckedChange = { backupState = it })
                    }
                }

                // Danger zone — reset counter
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("调试操作", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { counter = "0" },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("重置计数器")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRegenerateDialog) {
        AlertDialog(
            onDismissRequest = { showRegenerateDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("重新生成密钥对") },
            text = { Text("将生成全新的 EC P-256 密钥对并替换当前密钥。使用旧密钥注册的网站将无法验证此凭据。确定继续？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val (priv, pub) = KeyManager.generateKeyPair()
                        privateKeyPkcs8 = android.util.Base64.encodeToString(priv, android.util.Base64.NO_WRAP)
                        publicKeyX509 = android.util.Base64.encodeToString(pub, android.util.Base64.NO_WRAP)
                        showRegenerateDialog = false
                        Toast.makeText(context, "密钥对已重新生成", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("重新生成") }
            },
            dismissButton = { TextButton(onClick = { showRegenerateDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun EditSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun EditTextField(
    label: String,
    value: String,
    placeholder: String,
    maxLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = maxLines == 1,
        maxLines = maxLines,
        textStyle = MaterialTheme.typography.bodyMedium.let {
            if (maxLines > 1) it.copy(fontFamily = FontFamily.Monospace) else it
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

