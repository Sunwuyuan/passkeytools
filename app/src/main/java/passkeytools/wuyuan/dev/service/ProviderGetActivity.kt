package passkeytools.wuyuan.dev.service

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.PendingIntentHandler
import kotlinx.coroutines.*
import passkeytools.wuyuan.dev.crypto.*
import passkeytools.wuyuan.dev.data.PasskeyRepository
import passkeytools.wuyuan.dev.model.LogType
import passkeytools.wuyuan.dev.model.PasskeyEntity
import passkeytools.wuyuan.dev.model.RequestLogEntry
import passkeytools.wuyuan.dev.ui.theme.PasskeyToolsTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ProviderGetActivity"

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class ProviderGetActivity : ComponentActivity() {

    companion object {
        const val EXTRA_REQUEST_JSON = "extra_request_json"
        const val EXTRA_CREDENTIAL_ID = "extra_credential_id"
        const val EXTRA_CALLING_PACKAGE = "extra_calling_package"
    }

    private val repo by lazy { PasskeyRepository(applicationContext) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val providerRequest = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
        if (providerRequest == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val requestJson = intent.getStringExtra(EXTRA_REQUEST_JSON) ?: ""
        val credentialId = intent.getStringExtra(EXTRA_CREDENTIAL_ID) ?: ""
        val callingPackage = intent.getStringExtra(EXTRA_CALLING_PACKAGE) ?: ""
        val parsed = WebAuthnUtils.parseGetRequest(requestJson)

        scope.launch {
            // Gather candidates: the pre-selected one + all matching the rpId
            val primary = repo.getPasskeyByCredentialId(credentialId)
            val allForRp = if (parsed?.rpId?.isNotBlank() == true)
                repo.getPasskeysByRpIdSync(parsed.rpId) else emptyList()

            // Deduplicate, putting primary first
            val candidates = (listOfNotNull(primary) + allForRp)
                .distinctBy { it.credentialId }

            withContext(Dispatchers.Main) {
                setContent {
                    PasskeyToolsTheme {
                        GetCredentialDialog(
                            candidates = candidates,
                            rpId = parsed?.rpId ?: "",
                            callingPackage = callingPackage,
                            onSelect = { chosen ->
                                scope.launch {
                                    handleGet(providerRequest, requestJson, callingPackage, chosen, parsed)
                                }
                            },
                            onCancel = {
                                setResult(Activity.RESULT_CANCELED)
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    private suspend fun handleGet(
        providerRequest: androidx.credentials.provider.ProviderGetCredentialRequest,
        requestJson: String,
        callingPackage: String,
        passkey: PasskeyEntity,
        parsed: WebAuthnUtils.ParsedGetRequest?,
    ) {
        try {
            val updatedPasskey = repo.incrementCounter(passkey.credentialId) ?: passkey

            // Build authenticatorData (no AT flag for assertion)
            val authData = AuthenticatorDataBuilder.buildForGet(
                rpId = passkey.rpId,
                counter = updatedPasskey.counter,
                userVerified = true,
                backupEligible = passkey.backupEligible,
                backupState = passkey.backupState,
            )

            // Build clientDataJSON
            val origin = WebAuthnUtils.androidOriginFromPackage(callingPackage)
            val challenge = parsed?.challenge ?: ""
            val clientDataJson = WebAuthnUtils.buildGetClientDataJson(challenge, origin)

            // Sign: hash(clientDataJSON) || authData → signature over hash(clientDataJSON + authData)
            val clientDataHash = AuthenticatorDataBuilder.sha256(
                clientDataJson.toByteArray(Charsets.UTF_8)
            )
            val signedData = authData + clientDataHash
            val signature = KeyManager.sign(passkey.privateKeyPkcs8, signedData)

            // Build assertion response JSON
            val credIdBytes = WebAuthnUtils.base64UrlDecode(passkey.credentialId)
            val userIdBytes = WebAuthnUtils.base64UrlDecode(passkey.userId)
            val responseJson = WebAuthnUtils.buildAssertionResponse(
                credentialId = credIdBytes,
                clientDataJson = clientDataJson,
                authData = authData,
                signature = signature,
                userHandle = userIdBytes,
            )

            // Log success
            repo.insertLog(
                RequestLogEntry(
                    type = LogType.GET.name,
                    rpId = passkey.rpId,
                    sourcePackage = callingPackage,
                    requestJson = requestJson,
                    responseStatus = "OK",
                    credentialId = passkey.credentialId,
                )
            )

            val credentialResponse = GetCredentialResponse(
                PublicKeyCredential(responseJson)
            )
            val resultData = Intent()
            PendingIntentHandler.setGetCredentialResponse(resultData, credentialResponse)
            withContext(Dispatchers.Main) {
                setResult(Activity.RESULT_OK, resultData)
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting credential", e)
            repo.insertLog(
                RequestLogEntry(
                    type = LogType.ERROR.name,
                    rpId = passkey.rpId,
                    sourcePackage = callingPackage,
                    requestJson = requestJson,
                    responseStatus = "ERROR",
                    errorMessage = e.message ?: "Unknown error",
                    credentialId = passkey.credentialId,
                )
            )
            val resultData = Intent()
            PendingIntentHandler.setGetCredentialException(
                resultData,
                GetCredentialUnknownException(e.message)
            )
            withContext(Dispatchers.Main) {
                setResult(Activity.RESULT_OK, resultData)
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

@Composable
private fun GetCredentialDialog(
    candidates: List<PasskeyEntity>,
    rpId: String,
    callingPackage: String,
    onSelect: (PasskeyEntity) -> Unit,
    onCancel: () -> Unit,
) {
    var selected by remember { mutableStateOf<PasskeyEntity?>(null) }
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("使用 Passkey 登录", style = MaterialTheme.typography.titleLarge)
                            Text(rpId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("来源应用：$callingPackage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()

                    if (candidates.isEmpty()) {
                        Text("未找到匹配的 Passkey", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(candidates) { passkey ->
                                val isSelected = selected?.credentialId == passkey.credentialId
                                ListItem(
                                    headlineContent = {
                                        Text(passkey.userDisplayName.ifBlank { passkey.userName })
                                    },
                                    supportingContent = {
                                        Text(
                                            if (passkey.lastUsedAt > 0)
                                                "上次使用：${fmt.format(Date(passkey.lastUsedAt))}"
                                            else "从未使用",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    leadingContent = {
                                        Icon(Icons.Default.Person, contentDescription = null)
                                    },
                                    trailingContent = {
                                        RadioButton(selected = isSelected, onClick = { selected = passkey })
                                    },
                                    modifier = Modifier
                                        .clickable { selected = passkey }
                                        .then(
                                            if (isSelected)
                                                Modifier // could add background highlight
                                            else Modifier
                                        )
                                )
                                HorizontalDivider()
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onCancel) { Text("取消") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                (selected ?: candidates.firstOrNull())?.let { onSelect(it) }
                            },
                            enabled = candidates.isNotEmpty()
                        ) {
                            Text("验证")
                        }
                    }
                }
            }
        }
    }
}

