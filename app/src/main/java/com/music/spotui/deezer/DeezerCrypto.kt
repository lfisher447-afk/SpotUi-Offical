package com.music.spotui.deezer

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic helpers for Deezer's CDN stream encryption:
 *  1. Blowfish-CBC per-track key derivation from trackId MD5 + static HMAC salt.
 *  2. Every 3rd 2048-byte chunk (indices 0, 3, 6, ...) is Blowfish-CBC encrypted.
 */
object DeezerCrypto {
    private const val BF_SECRET = "g4el58wc0zvf9na1"
    private val BF_IV = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
    const val CHUNK_SIZE = 2048
    const val STRIPE_COUNT = 3
    const val STRIPE_BYTES = CHUNK_SIZE * STRIPE_COUNT // 6144 bytes

    fun trackKeyBytes(trackId: String): ByteArray {
        val idMd5 = md5Hex(trackId)
        val out = ByteArray(16)
        for (i in 0 until 16) {
            out[i] = (idMd5[i].code xor idMd5[i + 16].code xor BF_SECRET[i].code).toByte()
        }
        return out
    }

    /**
     * Decrypts [data] starting at absolute stream [streamOffset]. Only full
     * 2048-byte chunks at chunkIndex % 3 == 0 are Blowfish-CBC encrypted;
     * callers must align reads to 2048-byte chunk boundaries when streaming.
     */
    fun decryptChunkInPlace(data: ByteArray, length: Int, chunkIndex: Long, keyBytes: ByteArray) {
        if (length != CHUNK_SIZE || (chunkIndex % STRIPE_COUNT) != 0L) return
        val cipher = Cipher.getInstance("Blowfish/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "Blowfish"), IvParameterSpec(BF_IV))
        val decrypted = cipher.doFinal(data, 0, CHUNK_SIZE)
        System.arraycopy(decrypted, 0, data, 0, CHUNK_SIZE)
    }

    fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
