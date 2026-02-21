package passkeytools.wuyuan.dev.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import passkeytools.wuyuan.dev.data.PasskeyRepository
import passkeytools.wuyuan.dev.model.PasskeyEntity

data class ImportExportUiState(
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val lastResult: String? = null,
    val importPreview: List<PasskeyEntity>? = null,
    val importConflicts: Int = 0,
)

class ImportExportViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = PasskeyRepository(application)
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(ImportExportUiState())
    val uiState: StateFlow<ImportExportUiState> = _uiState.asStateFlow()

    fun exportAll(uri: Uri) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isExporting = true, lastResult = null)
        try {
            val passkeys = repo.getAllPasskeysSync()
            val content = json.encodeToString(passkeys)
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
            }
            _uiState.value = _uiState.value.copy(
                isExporting = false,
                lastResult = "✓ 已导出 ${passkeys.size} 个 Passkey"
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isExporting = false,
                lastResult = "✗ 导出失败：${e.message}"
            )
        }
    }

    fun loadImportPreview(uri: Uri) = viewModelScope.launch {
        try {
            val content = getApplication<Application>().contentResolver
                .openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return@launch
            val imported: List<PasskeyEntity> = json.decodeFromString(content)
            val existing = repo.getAllPasskeysSync().map { it.credentialId }.toSet()
            val conflicts = imported.count { it.credentialId in existing }
            _uiState.value = _uiState.value.copy(
                importPreview = imported,
                importConflicts = conflicts,
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(lastResult = "✗ 文件解析失败：${e.message}")
        }
    }

    fun confirmImport(overwrite: Boolean) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isImporting = true)
        val toImport = _uiState.value.importPreview ?: return@launch
        try {
            if (overwrite) {
                repo.savePasskeys(toImport)
            } else {
                val existingIds = repo.getAllPasskeysSync().map { it.credentialId }.toSet()
                val newOnly = toImport.filter { it.credentialId !in existingIds }
                repo.savePasskeys(newOnly)
            }
            _uiState.value = _uiState.value.copy(
                isImporting = false,
                importPreview = null,
                lastResult = "✓ 导入完成，共 ${toImport.size} 个 Passkey"
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isImporting = false,
                lastResult = "✗ 导入失败：${e.message}"
            )
        }
    }

    fun cancelImport() {
        _uiState.value = _uiState.value.copy(importPreview = null)
    }

    fun clearResult() {
        _uiState.value = _uiState.value.copy(lastResult = null)
    }
}

