package passkeytools.wuyuan.dev.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import passkeytools.wuyuan.dev.model.LogType
import passkeytools.wuyuan.dev.model.RequestLogEntry
import passkeytools.wuyuan.dev.ui.viewmodel.LogViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(viewModel: LogViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("请求日志") },
                actions = {
                    Text(
                        "${uiState.count} 条",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Filter chips
            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = uiState.filterType == null,
                        onClick = { viewModel.setFilter(null) },
                        label = { Text("全部") }
                    )
                }
                items(LogType.entries) { type ->
                    FilterChip(
                        selected = uiState.filterType == type.name,
                        onClick = { viewModel.setFilter(if (uiState.filterType == type.name) null else type.name) },
                        label = { Text(type.displayName) },
                        leadingIcon = {
                            Icon(type.icon, null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.logs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.History, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("暂无日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.logs, key = { it.id }) { log ->
                        LogItemCard(log)
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空日志") },
            text = { Text("确定清除全部请求日志？") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.clearLogs(); showClearDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun LogItemCard(log: RequestLogEntry) {
    var expanded by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    val logType = runCatching { LogType.valueOf(log.type) }.getOrDefault(LogType.ERROR)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Header row
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LogTypeBadge(logType)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        log.rpId.ifBlank { log.sourcePackage.ifBlank { "—" } },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        log.sourcePackage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(fmt.format(Date(log.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StatusBadge(log.responseStatus)
                }
            }

            if (log.credentialId.isNotBlank()) {
                Text(
                    "凭据: ${log.credentialId.take(24)}…",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (log.errorMessage.isNotBlank()) {
                Text(
                    "错误: ${log.errorMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Expanded JSON view
            if (expanded && log.requestJson.isNotBlank()) {
                HorizontalDivider()
                Text("请求 JSON", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        prettyJson(log.requestJson),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // Expand indicator
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun LogTypeBadge(type: LogType) {
    val (bg, fg) = when (type) {
        LogType.CREATE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        LogType.GET -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        LogType.CLEAR -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        LogType.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(color = bg, shape = MaterialTheme.shapes.small) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(type.icon, null, tint = fg, modifier = Modifier.size(12.dp))
            Text(type.displayName, style = MaterialTheme.typography.labelSmall, color = fg)
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val isOk = status == "OK"
    val isPending = status == "PENDING"
    val color = when {
        isOk -> MaterialTheme.colorScheme.primary
        isPending -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Text(status, style = MaterialTheme.typography.labelSmall, color = color)
}

private val LogType.displayName: String get() = when (this) {
    LogType.CREATE -> "创建"
    LogType.GET -> "认证"
    LogType.CLEAR -> "清除"
    LogType.ERROR -> "错误"
}

private val LogType.icon get() = when (this) {
    LogType.CREATE -> Icons.Default.AddCircle
    LogType.GET -> Icons.Default.Login
    LogType.CLEAR -> Icons.Default.ClearAll
    LogType.ERROR -> Icons.Default.Error
}

private fun prettyJson(json: String): String = try {
    // Simple pretty-print without a library
    val sb = StringBuilder()
    var indent = 0
    var inString = false
    for (c in json) {
        when {
            c == '"' && (sb.lastOrNull() != '\\') -> { inString = !inString; sb.append(c) }
            inString -> sb.append(c)
            c == '{' || c == '[' -> { sb.append(c); sb.append('\n'); indent++; repeat(indent) { sb.append("  ") } }
            c == '}' || c == ']' -> { sb.append('\n'); indent--; repeat(indent) { sb.append("  ") }; sb.append(c) }
            c == ',' -> { sb.append(c); sb.append('\n'); repeat(indent) { sb.append("  ") } }
            c == ':' -> sb.append(": ")
            c != ' ' && c != '\n' && c != '\r' && c != '\t' -> sb.append(c)
        }
    }
    sb.toString()
} catch (e: Exception) { json }

