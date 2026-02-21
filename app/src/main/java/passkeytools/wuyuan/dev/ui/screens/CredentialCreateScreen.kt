package passkeytools.wuyuan.dev.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import passkeytools.wuyuan.dev.crypto.KeyManager
import passkeytools.wuyuan.dev.crypto.WebAuthnUtils
import passkeytools.wuyuan.dev.data.PasskeyRepository
import passkeytools.wuyuan.dev.model.PasskeyEntity
import java.security.SecureRandom

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialCreateScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { PasskeyRepository(context) }

    var rpId by remember { mutableStateOf("") }
    var rpName by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var userDisplayName by remember { mutableStateOf("") }
    var sourcePackage by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("手动添加 Passkey") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, null)
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (rpId.isBlank()) { error = "RP ID 不能为空"; return@Button }
                            if (userName.isBlank()) { error = "用户名不能为空"; return@Button }
                            isSaving = true
                            error = null
                            scope.launch {
                                try {
                                    val credIdBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
                                    val credentialId = WebAuthnUtils.base64UrlEncode(credIdBytes)
                                    val (privateKeyBytes, publicKeyBytes) = KeyManager.generateKeyPair()
                                    val privateKeyB64 = android.util.Base64.encodeToString(privateKeyBytes, android.util.Base64.NO_WRAP)
                                    val publicKeyB64 = android.util.Base64.encodeToString(publicKeyBytes, android.util.Base64.NO_WRAP)
                                    val coseBytes = KeyManager.encodeCosePublicKey(publicKeyB64)
                                    val coseB64 = android.util.Base64.encodeToString(coseBytes, android.util.Base64.NO_WRAP)

                                    val resolvedUserId = userId.ifBlank {
                                        WebAuthnUtils.base64UrlEncode(
                                            ByteArray(16).also { SecureRandom().nextBytes(it) }
                                        )
                                    }

                                    val entity = PasskeyEntity(
                                        credentialId = credentialId,
                                        rpId = rpId,
                                        rpName = rpName.ifBlank { rpId },
                                        userId = resolvedUserId,
                                        userName = userName,
                                        userDisplayName = userDisplayName.ifBlank { userName },
                                        privateKeyPkcs8 = privateKeyB64,
                                        publicKeyCose = coseB64,
                                        publicKeyX509 = publicKeyB64,
                                        counter = 0L,
                                        createdAt = System.currentTimeMillis(),
                                        lastUsedAt = 0L,
                                        sourcePackage = sourcePackage,
                                        origin = if (sourcePackage.isNotBlank())
                                            WebAuthnUtils.androidOriginFromPackage(sourcePackage) else "",
                                        backupEligible = true,
                                        backupState = false,
                                    )
                                    repo.savePasskey(entity)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        onSaved(credentialId)
                                    }
                                } catch (e: Exception) {
                                    error = e.message
                                    isSaving = false
                                }
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("创建")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            error?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(it, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Relying Party", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider()
                    OutlinedTextField(
                        value = rpId, onValueChange = { rpId = it; error = null },
                        label = { Text("RP ID *") },
                        placeholder = { Text("example.com") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        isError = error != null && rpId.isBlank()
                    )
                    OutlinedTextField(
                        value = rpName, onValueChange = { rpName = it },
                        label = { Text("RP 名称") },
                        placeholder = { Text("Example Site") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(
                        value = sourcePackage, onValueChange = { sourcePackage = it },
                        label = { Text("来源应用包名") },
                        placeholder = { Text("com.example.app（可选）") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("用户", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider()
                    OutlinedTextField(
                        value = userName, onValueChange = { userName = it; error = null },
                        label = { Text("用户名 *") },
                        placeholder = { Text("user@example.com") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        isError = error != null && userName.isBlank()
                    )
                    OutlinedTextField(
                        value = userDisplayName, onValueChange = { userDisplayName = it },
                        label = { Text("显示名称") },
                        placeholder = { Text("张三（空则使用用户名）") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(
                        value = userId, onValueChange = { userId = it },
                        label = { Text("用户 ID (Base64URL)") },
                        placeholder = { Text("留空则自动生成") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        "密钥对将自动生成（EC P-256），可在创建后进入详情页查看和编辑。",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

