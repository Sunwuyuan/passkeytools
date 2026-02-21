package passkeytools.wuyuan.dev.ui.screens

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import passkeytools.wuyuan.dev.crypto.WebAuthnUtils
import passkeytools.wuyuan.dev.model.PasskeyEntity
import passkeytools.wuyuan.dev.ui.viewmodel.CredentialDetailViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialDetailScreen(
    credentialId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: CredentialDetailViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var keyDisplayMode by remember { mutableStateOf(KeyDisplayMode.BASE64) }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
            viewModel.clearSaveSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("凭据详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, null)
                    }
                }
            )
        }
    ) { padding ->
        val pk = uiState.passkey
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (pk == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(uiState.error ?: "未找到凭据")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Key display mode toggle
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyDisplayMode.entries.forEach { mode ->
                        FilterChip(
                            selected = keyDisplayMode == mode,
                            onClick = { keyDisplayMode = mode },
                            label = { Text(mode.label) }
                        )
                    }
                }

                DetailSection("基本信息") {
                    DetailField("凭据 ID", formatBytes(pk.credentialId, keyDisplayMode), clipboard, context)
                    DetailField("RP ID", pk.rpId, clipboard, context)
                    DetailField("RP 名称", pk.rpName, clipboard, context)
                    DetailField("来源应用", pk.sourcePackage, clipboard, context)
                    DetailField("Origin", pk.origin, clipboard, context)
                }

                DetailSection("用户信息") {
                    DetailField("用户 ID", formatBytes(pk.userId, keyDisplayMode), clipboard, context)
                    DetailField("用户名", pk.userName, clipboard, context)
                    DetailField("显示名称", pk.userDisplayName, clipboard, context)
                }

                DetailSection("密钥信息") {
                    // Public key
                    KeyField(
                        label = "公钥 (X.509)",
                        base64Value = pk.publicKeyX509,
                        displayMode = keyDisplayMode,
                        clipboard = clipboard,
                        context = context
                    )
                    KeyField(
                        label = "公钥 (COSE)",
                        base64Value = pk.publicKeyCose,
                        displayMode = keyDisplayMode,
                        clipboard = clipboard,
                        context = context
                    )
                    // Private key — shown in full, no restrictions
                    KeyField(
                        label = "私钥 (PKCS#8)",
                        base64Value = pk.privateKeyPkcs8,
                        displayMode = keyDisplayMode,
                        clipboard = clipboard,
                        context = context,
                        isPrivate = true
                    )
                }

                DetailSection("计数器与时间") {
                    val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    DetailField("签名计数器", pk.counter.toString(), clipboard, context)
                    DetailField("创建时间", dateFmt.format(Date(pk.createdAt)), clipboard, context)
                    DetailField("上次使用", if (pk.lastUsedAt > 0) dateFmt.format(Date(pk.lastUsedAt)) else "从未使用", clipboard, context)
                }

                DetailSection("标志位") {
                    DetailField("备份资格 (BE)", if (pk.backupEligible) "✓ 是" else "✗ 否", clipboard, context)
                    DetailField("备份状态 (BS)", if (pk.backupState) "✓ 是" else "✗ 否", clipboard, context)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除凭据") },
            text = { Text("确定删除此 Passkey？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deletePasskey(); showDeleteDialog = false; onBack() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
        )
    }
}

enum class KeyDisplayMode(val label: String) {
    BASE64("Base64"), HEX("Hex"), PEM("PEM")
}

private fun formatBytes(base64OrBase64Url: String, mode: KeyDisplayMode): String {
    return when (mode) {
        KeyDisplayMode.BASE64 -> base64OrBase64Url
        KeyDisplayMode.HEX -> try {
            val bytes = WebAuthnUtils.base64Decode(
                base64OrBase64Url.replace('-', '+').replace('_', '/')
            )
            WebAuthnUtils.toHex(bytes)
        } catch (e: Exception) { base64OrBase64Url }
        KeyDisplayMode.PEM -> base64OrBase64Url // PEM conversion handled in KeyField
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun DetailField(
    label: String,
    value: String,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    context: android.content.Context,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                value.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                fontFamily = if (value.length > 20) FontFamily.Monospace else FontFamily.Default
            )
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(value))
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun KeyField(
    label: String,
    base64Value: String,
    displayMode: KeyDisplayMode,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    context: android.content.Context,
    isPrivate: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (isPrivate) {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
        }

        val displayValue = when (displayMode) {
            KeyDisplayMode.BASE64 -> base64Value
            KeyDisplayMode.HEX -> try {
                val bytes = WebAuthnUtils.base64Decode(base64Value)
                WebAuthnUtils.toHex(bytes).chunked(64).joinToString("\n")
            } catch (e: Exception) { base64Value }
            KeyDisplayMode.PEM -> try {
                val bytes = WebAuthnUtils.base64Decode(base64Value)
                val typeLabel = if (isPrivate) "PRIVATE KEY" else "PUBLIC KEY"
                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    .chunked(64).joinToString("\n")
                "-----BEGIN $typeLabel-----\n$b64\n-----END $typeLabel-----"
            } catch (e: Exception) { base64Value }
        }

        OutlinedCard(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    displayValue,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(displayValue))
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("复制", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(if (expanded) "收起" else "展开", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

