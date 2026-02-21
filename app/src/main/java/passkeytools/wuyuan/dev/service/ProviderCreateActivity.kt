package passkeytools.wuyuan.dev.service

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.provider.PendingIntentHandler
import androidx.credentials.provider.ProviderCreateCredentialRequest
import androidx.credentials.webauthn.PublicKeyCredentialCreationOptions
import kotlinx.coroutines.*
import passkeytools.wuyuan.dev.crypto.*
import passkeytools.wuyuan.dev.data.PasskeyRepository
import passkeytools.wuyuan.dev.model.LogType
import passkeytools.wuyuan.dev.model.PasskeyEntity
import passkeytools.wuyuan.dev.model.RequestLogEntry
import passkeytools.wuyuan.dev.ui.theme.PasskeyToolsTheme
import java.security.SecureRandom

private const val TAG = "ProviderCreateActivity"

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class ProviderCreateActivity : ComponentActivity() {

    companion object {
        const val EXTRA_REQUEST_JSON = "extra_request_json"
        const val EXTRA_CALLING_PACKAGE = "extra_calling_package"
    }

    private val repo by lazy { PasskeyRepository(applicationContext) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val providerRequest = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
        if (providerRequest == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val requestJson = intent.getStringExtra(EXTRA_REQUEST_JSON) ?: ""
        val callingPackage = intent.getStringExtra(EXTRA_CALLING_PACKAGE) ?: ""
        val parsed = WebAuthnUtils.parseCreateRequest(requestJson)

        setContent {
            PasskeyToolsTheme {
                CreateCredentialDialog(
                    rpId = parsed?.rpId ?: "unknown",
                    rpName = parsed?.rpName ?: "Unknown",
                    userName = parsed?.userName ?: "Unknown",
                    userDisplayName = parsed?.userDisplayName ?: "",
                    callingPackage = callingPackage,
                    onConfirm = {
                        scope.launch {
                            handleCreate(providerRequest, requestJson, callingPackage, parsed)
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

    private suspend fun handleCreate(
        providerRequest: ProviderCreateCredentialRequest,
        requestJson: String,
        callingPackage: String,
        parsed: WebAuthnUtils.ParsedCreateRequest?,
    ) {
        try {
            // Generate credential ID (16 random bytes)
            val credIdBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val credentialId = WebAuthnUtils.base64UrlEncode(credIdBytes)

            // Generate key pair
            val (privateKeyBytes, publicKeyBytes) = KeyManager.generateKeyPair()
            val privateKeyB64 = WebAuthnUtils.base64Encode(privateKeyBytes)
            val publicKeyB64 = WebAuthnUtils.base64Encode(publicKeyBytes)

            // COSE-encode the public key
            val cosePublicKey = KeyManager.encodeCosePublicKey(publicKeyB64)

            // Build authenticatorData
            val authData = AuthenticatorDataBuilder.buildForCreate(
                rpId = parsed?.rpId ?: "",
                credentialIdBytes = credIdBytes,
                cosePublicKeyBytes = cosePublicKey,
                userVerified = true,
                backupEligible = true,
            )

            // Build attestationObject (CBOR encoded, fmt=none)
            val attestationObject = CborEncoder.encodeAttestationObject(authData)

            // Build clientDataJSON
            val origin = WebAuthnUtils.androidOriginFromPackage(callingPackage)
            val challenge = parsed?.challenge ?: ""
            val clientDataJson = WebAuthnUtils.buildCreateClientDataJson(challenge, origin)

            // Build registration response JSON
            val responseJson = WebAuthnUtils.buildRegistrationResponse(
                credentialId = credIdBytes,
                clientDataJson = clientDataJson,
                authData = authData,
                attestationObject = attestationObject,
            )

            // Save to database
            val entity = PasskeyEntity(
                credentialId = credentialId,
                rpId = parsed?.rpId ?: "",
                rpName = parsed?.rpName ?: "",
                userId = parsed?.userId ?: "",
                userName = parsed?.userName ?: "",
                userDisplayName = parsed?.userDisplayName ?: "",
                privateKeyPkcs8 = privateKeyB64,
                publicKeyCose = WebAuthnUtils.base64Encode(cosePublicKey),
                publicKeyX509 = publicKeyB64,
                counter = 0L,
                createdAt = System.currentTimeMillis(),
                lastUsedAt = System.currentTimeMillis(),
                sourcePackage = callingPackage,
                origin = origin,
                backupEligible = true,
                backupState = false,
            )
            repo.savePasskey(entity)

            // Update log
            repo.insertLog(
                RequestLogEntry(
                    type = LogType.CREATE.name,
                    rpId = parsed?.rpId ?: "",
                    sourcePackage = callingPackage,
                    requestJson = requestJson,
                    responseStatus = "OK",
                    credentialId = credentialId,
                )
            )

            // Build and return the Credential Manager response
            val credResponse = androidx.credentials.CreatePublicKeyCredentialResponse(responseJson)
            val resultData = Intent()
            PendingIntentHandler.setCreateCredentialResponse(resultData, credResponse)
            withContext(Dispatchers.Main) {
                setResult(Activity.RESULT_OK, resultData)
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating credential", e)
            repo.insertLog(
                RequestLogEntry(
                    type = LogType.ERROR.name,
                    sourcePackage = callingPackage,
                    requestJson = requestJson,
                    responseStatus = "ERROR",
                    errorMessage = e.message ?: "Unknown error",
                )
            )
            val resultData = Intent()
            PendingIntentHandler.setCreateCredentialException(
                resultData,
                CreateCredentialUnknownException(e.message)
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
private fun CreateCredentialDialog(
    rpId: String,
    rpName: String,
    userName: String,
    userDisplayName: String,
    callingPackage: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    var confirmed by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ElevatedCard(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("创建 Passkey", style = MaterialTheme.typography.titleLarge)
                    }
                    HorizontalDivider()
                    InfoRow("应用", callingPackage)
                    InfoRow("网站", rpName.ifBlank { rpId })
                    InfoRow("域名", rpId)
                    InfoRow("用户", userDisplayName.ifBlank { userName })
                    HorizontalDivider()
                    Text(
                        "PasskeyTools 将为此账号生成新密钥并保存，私钥以明文存储供调试使用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onCancel) { Text("取消") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (!confirmed) {
                                    confirmed = true
                                    onConfirm()
                                }
                            },
                            enabled = !confirmed
                        ) {
                            if (confirmed) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("创建")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            "$label：",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(60.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

