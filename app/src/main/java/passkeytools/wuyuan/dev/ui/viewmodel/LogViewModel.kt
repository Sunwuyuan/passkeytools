package passkeytools.wuyuan.dev.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import passkeytools.wuyuan.dev.data.PasskeyRepository
import passkeytools.wuyuan.dev.model.RequestLogEntry

data class LogUiState(
    val logs: List<RequestLogEntry> = emptyList(),
    val filterType: String? = null,
    val isLoading: Boolean = true,
    val count: Int = 0,
)

class LogViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = PasskeyRepository(application)

    private val _filterType = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LogUiState> = combine(
        repo.getAllLogs(),
        _filterType
    ) { logs, filter ->
        val filtered = if (filter == null) logs else logs.filter { it.type == filter }
        LogUiState(logs = filtered, filterType = filter, isLoading = false, count = logs.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LogUiState())

    fun setFilter(type: String?) { _filterType.value = type }

    fun clearLogs() = viewModelScope.launch { repo.clearLogs() }
}

