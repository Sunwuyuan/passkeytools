package passkeytools.wuyuan.dev.crypto

import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Builds the authenticatorData byte structure per WebAuthn spec §6.1.
 *
 * Layout:
 *   rpIdHash       [32 bytes]  SHA-256 of rpId
 *   flags          [1 byte]    UP=0x01, UV=0x04, BE=0x08, BS=0x10, AT=0x40
 *   signCount      [4 bytes]   big-endian uint32
 *   [if AT flag set]
 *   aaguid         [16 bytes]  all zeros (no attestation)
 *   credIdLength   [2 bytes]   big-endian uint16
 *   credId         [variable]  raw credential ID bytes
 *   credPubKey     [variable]  COSE-encoded EC public key
 */
object AuthenticatorDataBuilder {

    // AAGUID: "PasskeyTools debug" — can be any 16-byte identifier
    private val AAGUID = byteArrayOf(
        0x50, 0x61, 0x73, 0x73, 0x6b, 0x65, 0x79, 0x54,
        0x6f, 0x6f, 0x6c, 0x73, 0x44, 0x62, 0x67, 0x00
    )

    object Flags {
        const val UP: Int = 0x01  // User Present
        const val UV: Int = 0x04  // User Verified
        const val BE: Int = 0x08  // Backup Eligible
        const val BS: Int = 0x10  // Backup State
        const val AT: Int = 0x40  // Attested Credential Data included
    }

    /**
     * Build authenticatorData for a CREATE (registration) operation.
     */
    fun buildForCreate(
        rpId: String,
        credentialIdBytes: ByteArray,
        cosePublicKeyBytes: ByteArray,
        counter: Long = 0L,
        userVerified: Boolean = true,
        backupEligible: Boolean = true,
        backupState: Boolean = false,
    ): ByteArray {
        val rpIdHash = sha256(rpId.toByteArray(Charsets.UTF_8))
        var flags = Flags.UP or Flags.AT
        if (userVerified) flags = flags or Flags.UV
        if (backupEligible) flags = flags or Flags.BE
        if (backupState) flags = flags or Flags.BS

        val buf = ByteBuffer.allocate(
            32 + 1 + 4 +          // rpIdHash + flags + counter
            16 + 2 +               // aaguid + credIdLen
            credentialIdBytes.size +
            cosePublicKeyBytes.size
        )
        buf.put(rpIdHash)
        buf.put(flags.toByte())
        buf.putInt(counter.toInt())
        buf.put(AAGUID)
        buf.putShort(credentialIdBytes.size.toShort())
        buf.put(credentialIdBytes)
        buf.put(cosePublicKeyBytes)
        return buf.array()
    }

    /**
     * Build authenticatorData for a GET (assertion/authentication) operation.
     */
    fun buildForGet(
        rpId: String,
        counter: Long,
        userVerified: Boolean = true,
        backupEligible: Boolean = true,
        backupState: Boolean = false,
    ): ByteArray {
        val rpIdHash = sha256(rpId.toByteArray(Charsets.UTF_8))
        var flags = Flags.UP
        if (userVerified) flags = flags or Flags.UV
        if (backupEligible) flags = flags or Flags.BE
        if (backupState) flags = flags or Flags.BS

        val buf = ByteBuffer.allocate(32 + 1 + 4)
        buf.put(rpIdHash)
        buf.put(flags.toByte())
        buf.putInt(counter.toInt())
        return buf.array()
    }

    fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)
}

