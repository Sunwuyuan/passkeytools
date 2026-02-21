package passkeytools.wuyuan.dev.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import passkeytools.wuyuan.dev.model.PasskeyEntity
import passkeytools.wuyuan.dev.ui.viewmodel.CredentialListViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CredentialListScreen(
    onCredentialClick: (String) -> Unit,
    onCreateNew: () -> Unit,
    viewModel: CredentialListViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchActive by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Passkey 凭据") },
                actions = {
                    Text(
                        "${uiState.totalCount} 个",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    IconButton(onClick = { showDeleteAllDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "清空全部")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateNew) {
                Icon(Icons.Default.Add, contentDescription = "手动添加")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = uiState.searchQuery,
                        onQueryChange = viewModel::setSearchQuery,
                        onSearch = { searchActive = false },
                        expanded = searchActive,
                        onExpandedChange = { searchActive = it },
                        placeholder = { Text("搜索 RP、用户名…") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, null)
                                }
                            }
                        }
                    )
                },
                expanded = searchActive,
                onExpandedChange = { searchActive = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                content = {}
            )

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredPasskeys.isEmpty()) {
                EmptyState(hasQuery = uiState.searchQuery.isNotBlank())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    uiState.groupedByRp.forEach { (rpId, passkeys) ->
                        stickyHeader(key = "header_$rpId") {
                            RpGroupHeader(
                                rpId = rpId,
                                rpName = passkeys.first().rpName,
                                count = passkeys.size
                            )
                        }
                        items(passkeys, key = { it.credentialId }) { passkey ->
                            SwipeToDeleteItem(
                                onDelete = { viewModel.deletePasskey(passkey) },
                                modifier = Modifier.animateItem()
                            ) {
                                PasskeyListItem(
                                    passkey = passkey,
                                    onClick = { onCredentialClick(passkey.credentialId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("清空所有凭据") },
            text = { Text("此操作将删除所有 ${uiState.totalCount} 个 Passkey，无法恢复。确定继续？") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteAll(); showDeleteAllDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除全部") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun RpGroupHeader(rpId: String, rpName: String, count: Int) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(rpName.ifBlank { rpId }, style = MaterialTheme.typography.titleSmall)
                Text(rpId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Badge { Text("$count") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteItem(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(true) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                visible = false
                true
            } else false
        }
    )

    AnimatedVisibility(
        visible = visible,
        exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(),
        modifier = modifier
    ) {
        LaunchedEffect(visible) {
            if (!visible) {
                kotlinx.coroutines.delay(300)
                onDelete()
            }
        }
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(end = 24.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            },
            enableDismissFromStartToEnd = false,
        ) {
            content()
        }
    }
}

@Composable
private fun PasskeyListItem(passkey: PasskeyEntity, onClick: () -> Unit) {
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    ListItem(
        headlineContent = {
            Text(
                passkey.userDisplayName.ifBlank { passkey.userName }.ifBlank { "未知用户" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                if (passkey.userName.isNotBlank() && passkey.userName != passkey.userDisplayName) {
                    Text(passkey.userName, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    if (passkey.lastUsedAt > 0) "上次使用 ${fmt.format(Date(passkey.lastUsedAt))}" else "从未使用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "计数器 ${passkey.counter}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
}

@Composable
private fun EmptyState(hasQuery: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                if (hasQuery) Icons.Default.SearchOff else Icons.Outlined.Key,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (hasQuery) "未找到匹配的凭据" else "暂无 Passkey",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!hasQuery) {
                Text(
                    "在支持 Passkey 的应用或网站注册，\n或点击 + 手动添加",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

