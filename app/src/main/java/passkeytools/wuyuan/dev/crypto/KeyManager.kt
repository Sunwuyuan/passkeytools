package passkeytools.wuyuan.dev.crypto

import android.util.Base64
import java.security.*
import java.security.interfaces.ECPublicKey
import java.security.spec.*
import java.math.BigInteger

/**
 * Key management using pure Java Security API (no AndroidKeyStore).
 * All keys are stored as plain Base64-encoded DER bytes — fully exportable and importable.
 */
object KeyManager {

    private const val ALGORITHM = "EC"
    private const val CURVE = "secp256r1"
    private const val SIGN_ALGORITHM = "SHA256withECDSA"

    /**
     * Generate a new P-256 key pair.
     * @return Pair of (PKCS8 private key bytes, X.509 public key bytes)
     */
    fun generateKeyPair(): Pair<ByteArray, ByteArray> {
        val kpg = KeyPairGenerator.getInstance(ALGORITHM)
        kpg.initialize(ECGenParameterSpec(CURVE), SecureRandom())
        val keyPair = kpg.generateKeyPair()
        return Pair(
            keyPair.private.encoded,   // PKCS#8 DER
            keyPair.public.encoded     // X.509 SubjectPublicKeyInfo DER
        )
    }

    /**
     * Encode a key pair to Base64 strings suitable for storage.
     */
    fun encodeKeyPair(privateKeyBytes: ByteArray, publicKeyBytes: ByteArray): Pair<String, String> {
        return Pair(
            Base64.encodeToString(privateKeyBytes, Base64.NO_WRAP),
            Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)
        )
    }

    /**
     * Import a private key from Base64-encoded PKCS#8 DER bytes.
     */
    fun importPrivateKey(base64Pkcs8: String): PrivateKey {
        val bytes = Base64.decode(base64Pkcs8, Base64.NO_WRAP)
        return importPrivateKeyBytes(bytes)
    }

    fun importPrivateKeyBytes(pkcs8Bytes: ByteArray): PrivateKey {
        val spec = PKCS8EncodedKeySpec(pkcs8Bytes)
        return KeyFactory.getInstance(ALGORITHM).generatePrivate(spec)
    }

    /**
     * Import a public key from Base64-encoded X.509 SubjectPublicKeyInfo DER bytes.
     */
    fun importPublicKey(base64X509: String): PublicKey {
        val bytes = Base64.decode(base64X509, Base64.NO_WRAP)
        return importPublicKeyBytes(bytes)
    }

    fun importPublicKeyBytes(x509Bytes: ByteArray): PublicKey {
        val spec = X509EncodedKeySpec(x509Bytes)
        return KeyFactory.getInstance(ALGORITHM).generatePublic(spec)
    }

    /**
     * Sign data with a PKCS#8 private key (Base64-encoded DER).
     * Returns the DER-encoded ASN.1 signature.
     */
    fun sign(privateKeyBase64: String, data: ByteArray): ByteArray {
        val privateKey = importPrivateKey(privateKeyBase64)
        return signWithKey(privateKey, data)
    }

    fun signWithKey(privateKey: PrivateKey, data: ByteArray): ByteArray {
        val sig = Signature.getInstance(SIGN_ALGORITHM)
        sig.initSign(privateKey)
        sig.update(data)
        return sig.sign()
    }

    /**
     * Encode the EC public key in COSE format (used in attestedCredentialData).
     * COSE_Key map for EC2 key type (kty=2, alg=-7/ES256, crv=1/P-256):
     *   {1: 2, 3: -7, -1: 1, -2: x, -3: y}
     */
    fun encodeCosePublicKey(publicKeyX509Base64: String): ByteArray {
        val publicKey = importPublicKey(publicKeyX509Base64) as ECPublicKey
        return encodeCosePublicKeyFromECKey(publicKey)
    }

    fun encodeCosePublicKeyFromECKey(publicKey: ECPublicKey): ByteArray {
        val w = publicKey.w
        val x = w.affineX.toByteArrayUnsigned(32)
        val y = w.affineY.toByteArrayUnsigned(32)

        // Manual CBOR encoding of COSE_Key map {1:2, 3:-7, -1:1, -2:x_bytes, -3:y_bytes}
        val cbor = mutableListOf<Byte>()

        // Map with 5 entries: 0xa5
        cbor.add(0xa5.toByte())

        // 1: 2  (kty: EC2)
        cbor.add(0x01.toByte()); cbor.add(0x02.toByte())
        // 3: -7  (alg: ES256) → CBOR: 0x26
        cbor.add(0x03.toByte()); cbor.add(0x26.toByte())
        // -1: 1  (crv: P-256) → key -1 is 0x20
        cbor.add(0x20.toByte()); cbor.add(0x01.toByte())
        // -2: x  → key -2 is 0x21
        cbor.add(0x21.toByte())
        cbor.add((0x40 + 32).toByte()) // bstr, 32 bytes
        cbor.addAll(x.toList())
        // -3: y  → key -3 is 0x22
        cbor.add(0x22.toByte())
        cbor.add((0x40 + 32).toByte()) // bstr, 32 bytes
        cbor.addAll(y.toList())

        return cbor.toByteArray()
    }

    /**
     * Get the raw X and Y bytes from a Base64 X.509 public key for display purposes.
     */
    fun getPublicKeyCoordinates(base64X509: String): Pair<ByteArray, ByteArray> {
        val pub = importPublicKey(base64X509) as ECPublicKey
        return Pair(
            pub.w.affineX.toByteArrayUnsigned(32),
            pub.w.affineY.toByteArrayUnsigned(32)
        )
    }

    private fun BigInteger.toByteArrayUnsigned(length: Int): ByteArray {
        val bytes = toByteArray()
        return when {
            bytes.size == length -> bytes
            bytes.size == length + 1 && bytes[0] == 0.toByte() -> bytes.copyOfRange(1, bytes.size)
            bytes.size < length -> ByteArray(length - bytes.size) + bytes
            else -> bytes
        }
    }
}

