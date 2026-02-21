package passkeytools.wuyuan.dev.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import passkeytools.wuyuan.dev.model.PasskeyEntity
import passkeytools.wuyuan.dev.ui.viewmodel.ImportExportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreen(
    onBack: () -> Unit,
    viewModel: ImportExportViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // SAF launchers
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportAll(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.loadImportPreview(it) } }

    LaunchedEffect(uiState.lastResult) {
        uiState.lastResult?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入 / 导出") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
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
            // Export card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Upload, null, tint = MaterialTheme.colorScheme.primary)
                        Text("导出 Passkey", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "将所有 Passkey（含私钥）导出为 JSON 文件，用于备份或迁移。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    WarningBanner("导出文件包含明文私钥，请妥善保管，勿分享给他人。")
                    Button(
                        onClick = {
                            val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                                .format(java.util.Date())
                            exportLauncher.launch("passkeys_$ts.json")
                        },
                        enabled = !uiState.isExporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(Icons.Default.SaveAlt, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("选择保存位置并导出")
                    }
                }
            }

            // Import card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.secondary)
                        Text("导入 Passkey", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "从 JSON 文件导入 Passkey 列表。文件应为本应用之前导出的格式。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("选择文件")
                    }
                }
            }

            // Import preview
            uiState.importPreview?.let { preview ->
                ImportPreviewCard(
                    preview = preview,
                    conflicts = uiState.importConflicts,
                    isImporting = uiState.isImporting,
                    onConfirmOverwrite = { viewModel.confirmImport(overwrite = true) },
                    onConfirmSkip = { viewModel.confirmImport(overwrite = false) },
                    onCancel = viewModel::cancelImport
                )
            }
        }
    }
}

@Composable
private fun ImportPreviewCard(
    preview: List<PasskeyEntity>,
    conflicts: Int,
    isImporting: Boolean,
    onConfirmOverwrite: () -> Unit,
    onConfirmSkip: () -> Unit,
    onCancel: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("导入预览", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()

            InfoRow2("文件中的凭据", "${preview.size} 个")
            InfoRow2("与现有冲突", "$conflicts 个")
            InfoRow2("新增", "${preview.size - conflicts} 个")

            if (conflicts > 0) {
                WarningBanner("有 $conflicts 个凭据与现有数据冲突（credentialId 相同），请选择处理方式。")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onConfirmSkip,
                        enabled = !isImporting,
                        modifier = Modifier.weight(1f)
                    ) { Text("跳过冲突") }
                    Button(
                        onClick = onConfirmOverwrite,
                        enabled = !isImporting,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isImporting) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("全部覆盖")
                    }
                }
            } else {
                Button(
                    onClick = onConfirmOverwrite,
                    enabled = !isImporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isImporting) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("确认导入 ${preview.size} 个")
                }
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("取消")
            }

            // Preview list (first 5)
            HorizontalDivider()
            Text("前 ${minOf(preview.size, 5)} 个预览：", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            preview.take(5).forEach { pk ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Key, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column {
                        Text(pk.rpId, style = MaterialTheme.typography.bodySmall)
                        Text(pk.userName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (preview.size > 5) {
                Text("… 还有 ${preview.size - 5} 个", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WarningBanner(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun InfoRow2(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

