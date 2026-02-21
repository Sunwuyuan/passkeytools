package passkeytools.wuyuan.dev.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import passkeytools.wuyuan.dev.data.PasskeyRepository
import passkeytools.wuyuan.dev.model.PasskeyEntity

data class CredentialDetailUiState(
    val passkey: PasskeyEntity? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val saveSuccess: Boolean = false,
)

class CredentialDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val repo = PasskeyRepository(application)
    private val credentialId: String = checkNotNull(savedStateHandle["credentialId"])

    private val _uiState = MutableStateFlow(CredentialDetailUiState())
    val uiState: StateFlow<CredentialDetailUiState> = _uiState.asStateFlow()

    init {
        loadPasskey()
    }

    fun loadPasskey() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        val pk = repo.getPasskeyByCredentialId(credentialId)
        _uiState.value = if (pk != null) {
            _uiState.value.copy(passkey = pk, isLoading = false)
        } else {
            _uiState.value.copy(isLoading = false, error = "Credential not found")
        }
    }

    fun savePasskey(updated: PasskeyEntity) = viewModelScope.launch {
        try {
            repo.updatePasskey(updated)
            _uiState.value = _uiState.value.copy(passkey = updated, saveSuccess = true)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = e.message)
        }
    }

    fun deletePasskey() = viewModelScope.launch {
        _uiState.value.passkey?.let { repo.deletePasskey(it) }
    }

    fun clearSaveSuccess() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }
}

/**
 * Factory that injects credentialId into SavedStateHandle so the ViewModel can read it.
 */
class CredentialDetailViewModelFactory(
    private val credentialId: String,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
        val savedStateHandle = SavedStateHandle(mapOf("credentialId" to credentialId))
        @Suppress("UNCHECKED_CAST")
        return CredentialDetailViewModel(app as Application, savedStateHandle) as T
    }
}

