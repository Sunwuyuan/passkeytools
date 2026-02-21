package passkeytools.wuyuan.dev.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.*
import androidx.credentials.provider.*
import kotlinx.coroutines.*
import passkeytools.wuyuan.dev.data.PasskeyRepository
import passkeytools.wuyuan.dev.model.LogType
import passkeytools.wuyuan.dev.model.RequestLogEntry
import kotlinx.serialization.json.*

private const val TAG = "PasskeyProviderService"

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PasskeyProviderService : CredentialProviderService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val repo: PasskeyRepository by lazy { PasskeyRepository(applicationContext) }

    // ── Create ────────────────────────────────────────────────────────────────

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        Log.d(TAG, "onBeginCreateCredentialRequest: ${request.javaClass.simpleName}")

        if (request !is BeginCreatePublicKeyCredentialRequest) {
            callback.onError(CreateCredentialUnknownException("Only PublicKeyCredential supported"))
            return
        }

        serviceScope.launch {
            try {
                val requestJson = request.requestJson
                val callingPackage = request.callingAppInfo?.packageName ?: "unknown"

                repo.insertLog(
                    RequestLogEntry(
                        type = LogType.CREATE.name,
                        rpId = extractRpId(requestJson),
                        sourcePackage = callingPackage,
                        requestJson = requestJson,
                        responseStatus = "PENDING",
                    )
                )

                val intent = Intent(applicationContext, ProviderCreateActivity::class.java).apply {
                    putExtra(ProviderCreateActivity.EXTRA_REQUEST_JSON, requestJson)
                    putExtra(ProviderCreateActivity.EXTRA_CALLING_PACKAGE, callingPackage)
                }

                val pendingIntent = PendingIntent.getActivity(
                    applicationContext,
                    System.currentTimeMillis().toInt(),
                    intent,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val createEntry = CreateEntry(
                    accountName = "PasskeyTools",
                    pendingIntent = pendingIntent,
                )

                callback.onResult(
                    BeginCreateCredentialResponse(createEntries = listOf(createEntry))
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in onBeginCreateCredentialRequest", e)
                callback.onError(CreateCredentialUnknownException(e.message))
            }
        }
    }

    // ── Get ───────────────────────────────────────────────────────────────────

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        Log.d(TAG, "onBeginGetCredentialRequest")

        serviceScope.launch {
            try {
                val credentialEntries = mutableListOf<CredentialEntry>()
                val callingPackage = request.callingAppInfo?.packageName ?: "unknown"

                for (option in request.beginGetCredentialOptions) {
                    if (option is BeginGetPublicKeyCredentialOption) {
                        val requestJson = option.requestJson
                        val rpId = extractRpId(requestJson)
                        val allowCredIds = extractAllowCredIds(requestJson)

                        repo.insertLog(
                            RequestLogEntry(
                                type = LogType.GET.name,
                                rpId = rpId,
                                sourcePackage = callingPackage,
                                requestJson = requestJson,
                                responseStatus = "PENDING",
                            )
                        )

                        // Query matching passkeys
                        val passkeys = if (allowCredIds.isNotEmpty()) {
                            repo.getPasskeysByCredentialIds(allowCredIds)
                                .ifEmpty { repo.getPasskeysByRpIdSync(rpId) }
                        } else {
                            repo.getPasskeysByRpIdSync(rpId)
                        }

                        for (passkey in passkeys) {
                            val intent = Intent(applicationContext, ProviderGetActivity::class.java).apply {
                                putExtra(ProviderGetActivity.EXTRA_REQUEST_JSON, requestJson)
                                putExtra(ProviderGetActivity.EXTRA_CREDENTIAL_ID, passkey.credentialId)
                                putExtra(ProviderGetActivity.EXTRA_CALLING_PACKAGE, callingPackage)
                            }
                            val pendingIntent = PendingIntent.getActivity(
                                applicationContext,
                                (System.currentTimeMillis() + passkey.credentialId.hashCode()).toInt(),
                                intent,
                                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                            )

                            credentialEntries.add(
                                PublicKeyCredentialEntry(
                                    context = applicationContext,
                                    username = passkey.userName.ifBlank { passkey.userDisplayName },
                                    pendingIntent = pendingIntent,
                                    beginGetPublicKeyCredentialOption = option,
                                    displayName = passkey.userDisplayName.ifBlank { null },
                                    lastUsedTime = if (passkey.lastUsedAt > 0)
                                        java.time.Instant.ofEpochMilli(passkey.lastUsedAt) else null,
                                )
                            )
                        }
                    }
                }

                callback.onResult(BeginGetCredentialResponse(credentialEntries = credentialEntries))
            } catch (e: Exception) {
                Log.e(TAG, "Error in onBeginGetCredentialRequest", e)
                callback.onError(GetCredentialUnknownException(e.message))
            }
        }
    }

    // ── Clear ─────────────────────────────────────────────────────────────────

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    ) {
        Log.d(TAG, "onClearCredentialStateRequest")
        serviceScope.launch {
            repo.insertLog(
                RequestLogEntry(
                    type = LogType.CLEAR.name,
                    sourcePackage = request.callingAppInfo?.packageName ?: "",
                    responseStatus = "OK",
                )
            )
            callback.onResult(null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun extractRpId(json: String): String = runCatching {
        Json.parseToJsonElement(json).jsonObject.let { obj ->
            obj["rpId"]?.jsonPrimitive?.content
                ?: obj["rp"]?.jsonObject?.get("id")?.jsonPrimitive?.content
                ?: ""
        }
    }.getOrDefault("")

    private fun extractAllowCredIds(json: String): List<String> = runCatching {
        Json.parseToJsonElement(json).jsonObject["allowCredentials"]
            ?.jsonArray?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
            ?: emptyList()
    }.getOrDefault(emptyList())
}

