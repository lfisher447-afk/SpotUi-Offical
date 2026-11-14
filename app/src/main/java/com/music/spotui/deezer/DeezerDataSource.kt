package com.music.spotui.deezer

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import okhttp3.Request
import okhttp3.Response
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

/**
 * Media3 [DataSource] that streams `deezer://<trackId>?enc=1&u=<cdnUrl>` URIs and
 * decrypts every 3rd 2048-byte chunk using Blowfish-CBC on the fly. Seeking aligns HTTP
 * Range requests to the start of the enclosing 2048-byte chunk and discards leading bytes.
 */
@OptIn(UnstableApi::class)
class DeezerDataSource : BaseDataSource(/* isNetwork = */ true) {
    private var activeUri: Uri? = null
    private var activeResponse: Response? = null
    private var inputStream: InputStream? = null
    private var encrypted: Boolean = false
    private var keyBytes: ByteArray? = null

    private var currentChunkIndex: Long = 0L
    private val chunkBuf = ByteArray(DeezerCrypto.CHUNK_SIZE)
    private var chunkValidLen = 0
    private var chunkCursor = 0
    private var bytesRemaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        activeUri = uri
        val trackId = uri.host.orEmpty()
        val cdnUrl = uri.getQueryParameter("u")
            ?: throw IOException("Missing Deezer CDN url")
        encrypted = uri.getQueryParameter("enc") == "1"
        keyBytes = if (encrypted && trackId.isNotBlank()) DeezerCrypto.trackKeyBytes(trackId) else null

        val requestedPos = dataSpec.position.coerceAtLeast(0L)
        val alignedChunkIndex = requestedPos / DeezerCrypto.CHUNK_SIZE
        val alignedOffset = alignedChunkIndex * DeezerCrypto.CHUNK_SIZE
        val skipInsideFirstChunk = (requestedPos - alignedOffset).toInt()

        currentChunkIndex = alignedChunkIndex
        chunkValidLen = 0
        chunkCursor = 0

        transferInitializing(dataSpec)

        val reqBuilder = Request.Builder().url(cdnUrl).get()
        if (alignedOffset > 0L) {
            reqBuilder.header("Range", "bytes=$alignedOffset-")
        }
        val resp = DeezerSession.httpClient().newCall(reqBuilder.build()).execute()
        activeResponse = resp
        if (!resp.isSuccessful) {
            resp.close()
            throw IOException("Deezer CDN HTTP ${resp.code}")
        }
        val body = resp.body ?: run {
            resp.close()
            throw IOException("Empty Deezer CDN response")
        }
        inputStream = body.byteStream()

        // Skip leading bytes if open() requested a non-chunk-aligned offset.
        if (skipInsideFirstChunk > 0) {
            if (!fillNextChunk()) {
                close()
                throw EOFException()
            }
            if (skipInsideFirstChunk <= chunkValidLen) {
                chunkCursor = skipInsideFirstChunk
            } else {
                close()
                throw EOFException()
            }
        }

        val contentLen = body.contentLength()
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else if (contentLen >= 0L) {
            (contentLen - skipInsideFirstChunk).coerceAtLeast(0L)
        } else {
            C.LENGTH_UNSET.toLong()
        }

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val toReadMax = if (bytesRemaining == C.LENGTH_UNSET.toLong()) length else min(length.toLong(), bytesRemaining).toInt()
        if (chunkCursor >= chunkValidLen) {
            if (!fillNextChunk()) return C.RESULT_END_OF_INPUT
        }

        val available = chunkValidLen - chunkCursor
        val copyLen = min(available, toReadMax)
        System.arraycopy(chunkBuf, chunkCursor, buffer, offset, copyLen)
        chunkCursor += copyLen
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= copyLen
        }
        bytesTransferred(copyLen)
        return copyLen
    }

    private fun fillNextChunk(): Boolean {
        val stream = inputStream ?: return false
        var total = 0
        while (total < DeezerCrypto.CHUNK_SIZE) {
            val r = stream.read(chunkBuf, total, DeezerCrypto.CHUNK_SIZE - total)
            if (r < 0) break
            total += r
        }
        if (total == 0) return false
        val k = keyBytes
        if (encrypted && k != null) {
            DeezerCrypto.decryptChunkInPlace(chunkBuf, total, currentChunkIndex, k)
        }
        currentChunkIndex++
        chunkValidLen = total
        chunkCursor = 0
        return true
    }

    override fun getUri(): Uri? = activeUri

    override fun close() {
        activeUri = null
        runCatching { inputStream?.close() }
        runCatching { activeResponse?.close() }
        inputStream = null
        activeResponse = null
        keyBytes = null
        chunkValidLen = 0
        chunkCursor = 0
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}

/**
 * Routes `deezer://` URIs straight to [DeezerDataSource] (bypassing Media3 `SimpleCache`
 * so Blowfish streams stay independent of cache keys) and delegates all other URIs to [upstream].
 */
@OptIn(UnstableApi::class)
class DeezerAwareDataSourceFactory(
    private val upstream: DataSource.Factory,
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        val httpOrCache = upstream.createDataSource()
        val deezer = DeezerDataSource()
        return object : DataSource {
            private var active: DataSource = httpOrCache

            override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
                httpOrCache.addTransferListener(transferListener)
                deezer.addTransferListener(transferListener)
            }

            override fun open(dataSpec: DataSpec): Long {
                active = if (dataSpec.uri.scheme.equals("deezer", ignoreCase = true)) deezer else httpOrCache
                return active.open(dataSpec)
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                active.read(buffer, offset, length)

            override fun getUri(): Uri? = active.uri

            override fun getResponseHeaders(): MutableMap<String, MutableList<String>> =
                active.responseHeaders

            override fun close() {
                active.close()
            }
        }
    }
}
