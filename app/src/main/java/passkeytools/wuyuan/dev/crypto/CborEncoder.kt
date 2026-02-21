package passkeytools.wuyuan.dev.crypto

/**
 * Minimal CBOR encoder for building attestation objects.
 * Only implements the subset needed for WebAuthn attestationObject:
 *   {fmt: "none", attStmt: {}, authData: <bytes>}
 */
object CborEncoder {

    fun encodeAttestationObject(authData: ByteArray): ByteArray {
        val out = mutableListOf<Byte>()

        // Map with 3 entries: 0xa3
        out.add(0xa3.toByte())

        // "fmt" -> "none"
        encodeTextString(out, "fmt")
        encodeTextString(out, "none")

        // "attStmt" -> {} (empty map)
        encodeTextString(out, "attStmt")
        out.add(0xa0.toByte()) // empty map

        // "authData" -> <bytes>
        encodeTextString(out, "authData")
        encodeByteString(out, authData)

        return out.toByteArray()
    }

    private fun encodeTextString(out: MutableList<Byte>, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        encodeLength(out, 0x60, bytes.size) // major type 3
        out.addAll(bytes.toList())
    }

    private fun encodeByteString(out: MutableList<Byte>, data: ByteArray) {
        encodeLength(out, 0x40, data.size) // major type 2
        out.addAll(data.toList())
    }

    private fun encodeLength(out: MutableList<Byte>, majorTypeBits: Int, length: Int) {
        when {
            length <= 23 -> out.add((majorTypeBits or length).toByte())
            length <= 0xFF -> {
                out.add((majorTypeBits or 24).toByte())
                out.add(length.toByte())
            }
            length <= 0xFFFF -> {
                out.add((majorTypeBits or 25).toByte())
                out.add((length shr 8).toByte())
                out.add((length and 0xFF).toByte())
            }
            else -> {
                out.add((majorTypeBits or 26).toByte())
                out.add((length shr 24).toByte())
                out.add((length shr 16 and 0xFF).toByte())
                out.add((length shr 8 and 0xFF).toByte())
                out.add((length and 0xFF).toByte())
            }
        }
    }
}

