package passkeytools.wuyuan.dev.crypto

import android.util.Base64
import kotlinx.serialization.json.*

/**
 * WebAuthn utilities: JSON parsing, response building, and credential helpers.
 */
object WebAuthnUtils {

    // ── Base64URL helpers ─────────────────────────────────────────────────────

    fun base64UrlEncode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    fun base64UrlDecode(s: String): ByteArray =
        Base64.decode(s.replace('-', '+').replace('_', '/'), Base64.NO_WRAP)

    fun base64Encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun base64Decode(s: String): ByteArray =
        Base64.decode(s, Base64.NO_WRAP)

    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun fromHex(hex: String): ByteArray {
        val cleaned = hex.replace(" ", "").replace("\n", "")
        return ByteArray(cleaned.length / 2) { i ->
            cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    // ── Registration (Create) ─────────────────────────────────────────────────

    /**
     * Build clientDataJSON for a create operation.
     */
    fun buildCreateClientDataJson(challenge: String, origin: String): String {
        return buildJsonObject {
            put("type", "webauthn.create")
            put("challenge", challenge) // as received (Base64URL)
            put("origin", origin)
            put("crossOrigin", false)
        }.toString()
    }

    /**
     * Build clientDataJSON for a get operation.
     */
    fun buildGetClientDataJson(challenge: String, origin: String): String {
        return buildJsonObject {
            put("type", "webauthn.get")
            put("challenge", challenge)
            put("origin", origin)
            put("crossOrigin", false)
        }.toString()
    }

    /**
     * Build the full registration response JSON (PublicKeyCredential with attestationResponse).
     */
    fun buildRegistrationResponse(
        credentialId: ByteArray,
        clientDataJson: String,
        authData: ByteArray,
        attestationObject: ByteArray,
        transports: List<String> = listOf("internal"),
    ): String {
        val clientDataBytes = clientDataJson.toByteArray(Charsets.UTF_8)
        return buildJsonObject {
            put("id", base64UrlEncode(credentialId))
            put("rawId", base64UrlEncode(credentialId))
            put("type", "public-key")
            putJsonObject("response") {
                put("clientDataJSON", base64UrlEncode(clientDataBytes))
                put("attestationObject", base64UrlEncode(attestationObject))
                put("transports", JsonArray(transports.map { JsonPrimitive(it) }))
            }
            putJsonObject("authenticatorAttachment") { } // removed per spec but some RPs expect it
        }.toString()
    }

    /**
     * Build the full authentication response JSON (PublicKeyCredential with assertionResponse).
     */
    fun buildAssertionResponse(
        credentialId: ByteArray,
        clientDataJson: String,
        authData: ByteArray,
        signature: ByteArray,
        userHandle: ByteArray,
    ): String {
        val clientDataBytes = clientDataJson.toByteArray(Charsets.UTF_8)
        return buildJsonObject {
            put("id", base64UrlEncode(credentialId))
            put("rawId", base64UrlEncode(credentialId))
            put("type", "public-key")
            putJsonObject("response") {
                put("clientDataJSON", base64UrlEncode(clientDataBytes))
                put("authenticatorData", base64UrlEncode(authData))
                put("signature", base64UrlEncode(signature))
                put("userHandle", base64UrlEncode(userHandle))
            }
        }.toString()
    }

    // ── Request JSON Parsing ──────────────────────────────────────────────────

    data class ParsedCreateRequest(
        val rpId: String,
        val rpName: String,
        val userId: String,       // Base64URL
        val userName: String,
        val userDisplayName: String,
        val challenge: String,    // Base64URL
        val requiresResidentKey: Boolean,
        val userVerification: String,
    )

    fun parseCreateRequest(json: String): ParsedCreateRequest? = runCatching {
        val obj = Json.parseToJsonElement(json).jsonObject
        val rp = obj["rp"]?.jsonObject
        val user = obj["user"]?.jsonObject
        val auth = obj["authenticatorSelection"]?.jsonObject
        ParsedCreateRequest(
            rpId = rp?.get("id")?.jsonPrimitive?.content ?: "",
            rpName = rp?.get("name")?.jsonPrimitive?.content ?: "",
            userId = user?.get("id")?.jsonPrimitive?.content ?: "",
            userName = user?.get("name")?.jsonPrimitive?.content ?: "",
            userDisplayName = user?.get("displayName")?.jsonPrimitive?.content ?: "",
            challenge = obj["challenge"]?.jsonPrimitive?.content ?: "",
            requiresResidentKey = auth?.get("residentKey")?.jsonPrimitive?.content == "required",
            userVerification = auth?.get("userVerification")?.jsonPrimitive?.content ?: "preferred",
        )
    }.getOrNull()

    data class ParsedGetRequest(
        val rpId: String,
        val challenge: String,
        val allowCredentialIds: List<String>,   // Base64URL credential IDs
        val userVerification: String,
    )

    fun parseGetRequest(json: String): ParsedGetRequest? = runCatching {
        val obj = Json.parseToJsonElement(json).jsonObject
        val allowList = obj["allowCredentials"]?.jsonArray?.mapNotNull {
            it.jsonObject["id"]?.jsonPrimitive?.content
        } ?: emptyList()
        ParsedGetRequest(
            rpId = obj["rpId"]?.jsonPrimitive?.content ?: "",
            challenge = obj["challenge"]?.jsonPrimitive?.content ?: "",
            allowCredentialIds = allowList,
            userVerification = obj["userVerification"]?.jsonPrimitive?.content ?: "preferred",
        )
    }.getOrNull()

    // ── Origin helpers ────────────────────────────────────────────────────────

    /** Build an android:apk-key-hash: origin from a package name for use in clientDataJSON. */
    fun androidOriginFromPackage(packageName: String): String = "android:apk-key-hash:$packageName"
}

