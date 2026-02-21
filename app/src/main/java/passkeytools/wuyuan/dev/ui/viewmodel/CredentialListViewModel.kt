package passkeytools.wuyuan.dev.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import passkeytools.wuyuan.dev.data.PasskeyRepository
import passkeytools.wuyuan.dev.model.PasskeyEntity

data class CredentialListUiState(
    val passkeys: List<PasskeyEntity> = emptyList(),
    val filteredPasskeys: List<PasskeyEntity> = emptyList(),
    val groupedByRp: Map<String, List<PasskeyEntity>> = emptyMap(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val totalCount: Int = 0,
)

class CredentialListViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = PasskeyRepository(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val uiState: StateFlow<CredentialListUiState> = combine(
        repo.getAllPasskeys(),
        _searchQuery
    ) { passkeys, query ->
        val filtered = if (query.isBlank()) passkeys
        else passkeys.filter { pk ->
            pk.rpId.contains(query, ignoreCase = true) ||
            pk.rpName.contains(query, ignoreCase = true) ||
            pk.userName.contains(query, ignoreCase = true) ||
            pk.userDisplayName.contains(query, ignoreCase = true) ||
            pk.sourcePackage.contains(query, ignoreCase = true)
        }
        val grouped = filtered.groupBy { it.rpId }
        CredentialListUiState(
            passkeys = passkeys,
            filteredPasskeys = filtered,
            groupedByRp = grouped,
            searchQuery = query,
            isLoading = false,
            totalCount = passkeys.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CredentialListUiState())

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun deletePasskey(passkey: PasskeyEntity) = viewModelScope.launch {
        repo.deletePasskey(passkey)
    }

    fun deleteAll() = viewModelScope.launch {
        repo.deleteAllPasskeys()
    }
}

