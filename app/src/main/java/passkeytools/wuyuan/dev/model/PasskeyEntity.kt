package passkeytools.wuyuan.dev.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "passkeys")
data class PasskeyEntity(
    // Base64URL encoded credential ID (WebAuthn spec)
    @PrimaryKey
    val credentialId: String,
    // Relying Party info
    val rpId: String,
    val rpName: String,
    // User info — userId is Base64URL encoded bytes
    val userId: String,
    val userName: String,
    val userDisplayName: String,
    // Keys — stored in Base64 encoded DER format (no encryption for debug purposes)
    val privateKeyPkcs8: String,   // PKCS#8 DER → Base64
    val publicKeyCose: String,     // COSE-encoded public key → Base64 (used in attestation)
    val publicKeyX509: String,     // X.509 SubjectPublicKeyInfo → Base64 (for display/import)
    // Counters and metadata
    val counter: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0L,
    // Source tracking
    val sourcePackage: String = "",
    val origin: String = "",
    // Flags
    val backupEligible: Boolean = true,
    val backupState: Boolean = false,
)

