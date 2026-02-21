package passkeytools.wuyuan.dev.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import passkeytools.wuyuan.dev.ui.viewmodel.CredentialListViewModel
import passkeytools.wuyuan.dev.ui.viewmodel.LogViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToImportExport: () -> Unit,
    credentialVm: CredentialListViewModel = viewModel(),
    logVm: LogViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by credentialVm.uiState.collectAsStateWithLifecycle()
    val logState by logVm.uiState.collectAsStateWithLifecycle()
    var showClearAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("设置") })
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
            // System integration card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionHeader(Icons.Default.Shield, "系统集成")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    SettingsItem(
                        title = "注册为密码管理器",
                        subtitle = "前往系统设置将 PasskeyTools 设为首选凭据提供方",
                        icon = Icons.Default.ManageAccounts,
                    ) {
                        Button(
                            onClick = {
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                        context.startActivity(
                                            Intent(Settings.ACTION_CREDENTIAL_PROVIDER)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    } else {
                                        context.startActivity(
                                            Intent(Settings.ACTION_SETTINGS)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "无法打开系统设置", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("打开设置")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    SettingsItem(
                        title = "自动填充服务设置",
                        subtitle = "管理 Android 自动填充框架（用于密码管理）",
                        icon = Icons.Default.AutoMode,
                    ) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                                            .setData(Uri.parse("package:${context.packageName}"))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(context, "无法打开自动填充设置", Toast.LENGTH_SHORT).show()
                                }
                            },
                        ) { Text("打开") }
                    }
                }
            }

            // Data management card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionHeader(Icons.Default.Storage, "数据管理")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    SettingsItem(
                        title = "导入 / 导出",
                        subtitle = "备份 Passkey 数据或从文件恢复",
                        icon = Icons.Default.SwapHoriz,
                        onClick = onNavigateToImportExport
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    SettingsItem(
                        title = "清空所有数据",
                        subtitle = "删除全部 ${uiState.totalCount} 个 Passkey 和 ${logState.count} 条日志",
                        icon = Icons.Default.DeleteForever,
                        isDestructive = true,
                    ) {
                        OutlinedButton(
                            onClick = { showClearAllDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("清空")
                        }
                    }
                }
            }

            // About card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionHeader(Icons.Default.Info, "关于")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    val pm = context.packageManager
                    val pi = runCatching { pm.getPackageInfo(context.packageName, 0) }.getOrNull()

                    InfoTile("应用名称", "PasskeyTools")
                    InfoTile("版本", "${pi?.versionName ?: "1.0"} (${pi?.longVersionCode ?: 1})")
                    InfoTile("包名", context.packageName)
                    InfoTile("目标 API", Build.VERSION.SDK_INT.toString())
                    InfoTile("存储方式", "Room 数据库（明文，含私钥）")
                    InfoTile("密钥算法", "EC P-256 (secp256r1 / ES256)")
                    InfoTile("Passkey 数量", "${uiState.totalCount} 个")
                    InfoTile("日志条数", "${logState.count} 条")
                }
            }
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("清空所有数据") },
            text = { Text("将删除全部 ${uiState.totalCount} 个 Passkey 和 ${logState.count} 条日志，此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        credentialVm.deleteAll()
                        logVm.clearLogs()
                        showClearAllDialog = false
                        Toast.makeText(context, "已清空", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("确认清空") }
            },
            dismissButton = { TextButton(onClick = { showClearAllDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isDestructive: Boolean = false,
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val titleColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp).padding(top = 2.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke()
        }
        if (onClick != null) {
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun InfoTile(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}


