// File: app/src/main/java/com/music/spotui/engine/archive/NovaAcFramer.kt
package com.music.spotui.engine.archive

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Version-8 framed AES-GCM container for app-owned NovaAc archive payloads. */
class NovaAcFramer {
    /** Encrypts [payload] in independently authenticated v8 frames using [key]. */
    fun encrypt(payload: ByteArray, key: SecretKey, frameBytes: Int = DEFAULT_FRAME_BYTES): ByteArray {
        require(frameBytes in 1..MAX_FRAME_BYTES) { "Frame size is outside safe bounds" }
        return ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { data ->
                data.write(MAGIC)
                data.writeInt(FORMAT_VERSION)
                payload.asList().chunked(frameBytes).forEachIndexed { index, bytes ->
                    val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
                    val cipherText = cipher(Cipher.ENCRYPT_MODE, key, nonce).doFinal(bytes.toByteArray())
                    data.writeInt(index)
                    data.writeInt(nonce.size)
                    data.write(nonce)
                    data.writeInt(cipherText.size)
                    data.write(cipherText)
                }
                data.writeInt(END_OF_FRAMES)
            }
            output.toByteArray()
        }
    }

    /** Authenticates and decrypts a v8 framed archive using [key]. */
    fun decrypt(container: ByteArray, key: SecretKey): ByteArray =
        DataInputStream(ByteArrayInputStream(container)).use { input ->
            val magic = ByteArray(MAGIC.size).also(input::readFully)
            require(magic.contentEquals(MAGIC)) { "Not a NovaAc framed archive" }
            require(input.readInt() == FORMAT_VERSION) { "Unsupported NovaAc frame version" }
            ByteArrayOutputStream().use { output ->
                var expectedIndex = 0
                while (true) {
                    val index = input.readInt()
                    if (index == END_OF_FRAMES) break
                    require(index == expectedIndex++) { "Archive frames are out of order" }
                    val nonceSize = input.readInt()
                    require(nonceSize == NONCE_BYTES) { "Invalid archive nonce" }
                    val nonce = ByteArray(nonceSize).also(input::readFully)
                    val cipherSize = input.readInt()
                    require(cipherSize in 1..MAX_CIPHER_BYTES) { "Invalid archive frame size" }
                    val cipherText = ByteArray(cipherSize).also(input::readFully)
                    output.write(cipher(Cipher.DECRYPT_MODE, key, nonce).doFinal(cipherText))
                }
                output.toByteArray()
            }
        }

    private fun cipher(mode: Int, key: SecretKey, nonce: ByteArray): Cipher = Cipher.getInstance(AES_TRANSFORMATION)
        .apply { init(mode, key, GCMParameterSpec(GCM_TAG_BITS, nonce)) }

    private companion object {
        const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        const val DEFAULT_FRAME_BYTES = 64 * 1024
        const val END_OF_FRAMES = -1
        const val FORMAT_VERSION = 8
        const val GCM_TAG_BITS = 128
        const val MAX_CIPHER_BYTES = DEFAULT_FRAME_BYTES + 128
        const val MAX_FRAME_BYTES = 1024 * 1024
        val MAGIC = "NOVAAC8".encodeToByteArray()
        const val NONCE_BYTES = 12
        val random = SecureRandom()
    }
}
