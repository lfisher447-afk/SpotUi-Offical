package org.eclipse.Mrbean.client

import android.content.Context
import android.util.Log
import com.metrolist.innertube.network.AuthSessionStore
import com.music.spotui.util.DevConsoleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * High-throughput HTTP/2 + Keep-Alive + Multi-Range download & playback transport
 * for SpotUI's Eclipse MrBean Turbo Engine (v2.5.4).
 *
 * - Shared warm [ConnectionPool] (32 sockets / 5m keep-alive) & raised [Dispatcher]
 *   (`maxRequests = 128`, `maxRequestsPerHost = 32`) eliminate TCP+TLS handshake stalls.
 * - 512 KiB fast initial probe + up to 8 simultaneous HTTP 206 [FileChannel] range workers.
 * - Up to 1 MiB positional I/O buffers & atomic rolling MB/s meter.
 */
object MrbeanDownloadClient {
    private const val TAG = "MrbeanDownloadClient"
    private const val DOWNLOAD_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    private const val PLAYBACK_UA = "Spotui-MrBean/2.5.4 (Android Media3)"

    data class DownloadResult(
        val success: Boolean,
        val bytesWritten: Long = 0L,
        val error: String? = null,
    )

    private data class DnsEntry(val addresses: List<InetAddress>, val expiresAtMs: Long)
    private data class ClientCacheEntry(val fingerprint: String, val client: OkHttpClient)

    private val dnsCache = ConcurrentHashMap<String, DnsEntry>()
    private val clientLock = Any()
    @Volatile private var cachedDownloadClient: ClientCacheEntry? = null
    @Volatile private var cachedPlaybackClient: ClientCacheEntry? = null

    private val sharedConnectionPool = ConnectionPool(32, 5, TimeUnit.MINUTES)

    // Live throughput meter across all concurrent download workers
    private val activeTransfers = AtomicInteger(0)
    private val windowBytes = AtomicLong(0L)
    private val windowStartMs = AtomicLong(System.currentTimeMillis())
    private val _throughputBytesPerSec = MutableStateFlow(0L)
    val throughputBytesPerSec: StateFlow<Long> = _throughputBytesPerSec.asStateFlow()

    private val _peakBytesPerSec = MutableStateFlow(0L)
    val peakBytesPerSec: StateFlow<Long> = _peakBytesPerSec.asStateFlow()

    private fun recordBytesTransferred(bytes: Long) {
        if (bytes <= 0L) return
        val total = windowBytes.addAndGet(bytes)
        val now = System.currentTimeMillis()
        val start = windowStartMs.get()
        val elapsed = now - start
        if (elapsed >= 450L && windowStartMs.compareAndSet(start, now)) {
            val captured = windowBytes.getAndSet(0L)
            val rate = ((captured * 1000L) / elapsed.coerceAtLeast(1L)).coerceAtLeast(0L)
            _throughputBytesPerSec.value = rate
            if (rate > _peakBytesPerSec.value) _peakBytesPerSec.value = rate
        } else if (elapsed in 1L..449L && _throughputBytesPerSec.value == 0L && total > 32_768L) {
            val rate = ((total * 1000L) / elapsed.coerceAtLeast(1L))
            _throughputBytesPerSec.value = rate
            if (rate > _peakBytesPerSec.value) _peakBytesPerSec.value = rate
        }
    }

    private fun beginTransferSlot() {
        if (activeTransfers.incrementAndGet() == 1) {
            windowBytes.set(0L)
            windowStartMs.set(System.currentTimeMillis())
        }
    }

    private fun endTransferSlot() {
        if (activeTransfers.decrementAndGet() <= 0) {
            activeTransfers.set(0)
            windowBytes.set(0L)
            _throughputBytesPerSec.value = 0L
        }
    }

    private fun cachedDns(): Dns {
        val delegate = AuthSessionStore.resolveDns()
        return object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val now = System.currentTimeMillis()
                dnsCache[hostname]?.let { cached ->
                    if (cached.expiresAtMs > now && cached.addresses.isNotEmpty()) {
                        return cached.addresses
                    }
                }
                val resolved = delegate.lookup(hostname)
                if (resolved.isNotEmpty()) {
                    if (dnsCache.size > 256) dnsCache.clear()
                    dnsCache[hostname] = DnsEntry(resolved, now + 5 * 60_000L)
                }
                return resolved
            }
        }
    }

    fun clearDnsCache() {
        dnsCache.clear()
    }

    fun okHttpClient(context: Context): OkHttpClient {
        val appCtx = context.applicationContext
        val tuning = MrbeanNetworkSettings.snapshot(appCtx)
        val fp = buildString {
            append(tuning.enabled).append('|')
            append(tuning.connectTimeoutMs).append('|')
            append(tuning.readTimeoutMs).append('|')
            append(tuning.http2Multiplexing).append('|')
            append(AuthSessionStore.currentNetworkFingerprint())
        }
        cachedDownloadClient?.takeIf { it.fingerprint == fp }?.let { return it.client }
        return synchronized(clientLock) {
            cachedDownloadClient?.takeIf { it.fingerprint == fp }?.let { return@synchronized it.client }
            val connectMs = if (tuning.enabled) tuning.connectTimeoutMs.toLong() else 10_000L
            val readMs = if (tuning.enabled) tuning.readTimeoutMs.toLong() else 20_000L
            val dispatcher = Dispatcher().apply {
                maxRequests = 128
                maxRequestsPerHost = 32
            }
            val protocols = if (tuning.http2Multiplexing) {
                listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
            } else {
                listOf(Protocol.HTTP_1_1)
            }
            val builder = OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(sharedConnectionPool)
                .protocols(protocols)
                .connectTimeout(connectMs, TimeUnit.MILLISECONDS)
                .readTimeout(readMs, TimeUnit.MILLISECONDS)
                .writeTimeout(connectMs, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
            AuthSessionStore.applyOkHttpSettings(builder, forStream = true)
            builder.dns(cachedDns())
            val built = builder.build()
            cachedDownloadClient = ClientCacheEntry(fp, built)
            built
        }
    }

    fun playbackOkHttpClient(context: Context): OkHttpClient {
        val appCtx = context.applicationContext
        val tuning = MrbeanNetworkSettings.snapshot(appCtx)
        val fp = buildString {
            append("play|")
            append(tuning.enabled).append('|')
            append(tuning.connectTimeoutMs).append('|')
            append(tuning.readTimeoutMs).append('|')
            append(tuning.http2Multiplexing).append('|')
            append(AuthSessionStore.currentNetworkFingerprint())
        }
        cachedPlaybackClient?.takeIf { it.fingerprint == fp }?.let { return it.client }
        return synchronized(clientLock) {
            cachedPlaybackClient?.takeIf { it.fingerprint == fp }?.let { return@synchronized it.client }
            val connectMs = if (tuning.enabled) tuning.connectTimeoutMs.toLong() else 12_000L
            val readMs = if (tuning.enabled) tuning.readTimeoutMs.toLong() else 25_000L
            val dispatcher = Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 16
            }
            val protocols = if (tuning.http2Multiplexing) {
                listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
            } else {
                listOf(Protocol.HTTP_1_1)
            }
            val builder = OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(sharedConnectionPool)
                .protocols(protocols)
                .connectTimeout(connectMs, TimeUnit.MILLISECONDS)
                .readTimeout(readMs, TimeUnit.MILLISECONDS)
                .writeTimeout(connectMs, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .addNetworkInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", PLAYBACK_UA)
                        .header("Accept", "*/*")
                        .header("Accept-Encoding", "identity")
                        .header("Cache-Control", "no-transform")
                        .build()
                    chain.proceed(request)
                }
            AuthSessionStore.applyOkHttpSettings(builder, forStream = true)
            builder.dns(cachedDns())
            val built = builder.build()
            cachedPlaybackClient = ClientCacheEntry(fp, built)
            built
        }
    }

    private fun buildMediaRequest(url: String, rangeHeader: String? = null): Request {
        val lower = url.lowercase()
        val isYouTubeHost = lower.contains("googlevideo.com") ||
            lower.contains("youtube.com") ||
            lower.contains("ytimg.com")
        return Request.Builder()
            .url(url)
            .header("User-Agent", DOWNLOAD_UA)
            .header("Accept-Encoding", "identity")
            .header("Accept", "*/*")
            .apply {
                if (isYouTubeHost) {
                    header("Referer", "https://music.youtube.com/")
                    header("Origin", "https://music.youtube.com")
                }
                if (rangeHeader != null) header("Range", rangeHeader)
            }
            .build()
    }

    /**
     * Downloads [url] into [destFile] using HTTP/2 connection pooling and multi-range
     * parallel slice workers.
     */
    suspend fun downloadRangedToFile(
        context: Context,
        url: String,
        destFile: File,
        onProgress: (Int) -> Unit = {},
    ): DownloadResult = withContext(Dispatchers.IO) {
        val tuning = MrbeanNetworkSettings.snapshot(context.applicationContext)
        val client = okHttpClient(context)
        val parallelWorkers = if (tuning.enabled) tuning.parallelChunksPerFile.coerceIn(1, 8) else 1
        val maxRetries = if (tuning.enabled) tuning.rangeRetries.coerceIn(1, 6) else 2
        val chunkBytes = (tuning.chunkMiB.coerceIn(1, 16).toLong()) * 1024L * 1024L
        val ioBufBytes = tuning.ioBufferKb.coerceIn(64, 1024) * 1024

        val startMs = System.currentTimeMillis()
        beginTransferSlot()
        try {
            onProgress(0)
            destFile.parentFile?.mkdirs()

            // Probe 512 KiB first so parallel HTTP/2 range workers spin up immediately
            val firstWindowBytes = if (parallelWorkers > 1) minOf(chunkBytes, 512L * 1024L) else chunkBytes
            val firstEnd = firstWindowBytes - 1L

            var probeTotalBytes = -1L
            var probeWritten = 0L
            var isFull200Body = false
            var lastError = "Download failed"
            var lastReportedPct = 0

            val writtenBytesCounter = AtomicLong(0L)
            fun reportProgressIfChanged(totalLen: Long) {
                if (totalLen <= 0L) return
                val done = writtenBytesCounter.get()
                val pct = ((done * 100L) / totalLen).toInt().coerceIn(0, 100)
                if (pct != lastReportedPct) {
                    lastReportedPct = pct
                    onProgress(pct)
                }
            }

            // Phase 1: initial probe / full-body or first-range write
            var probeOk = false
            for (attempt in 1..maxRetries) {
                currentCoroutineContext().ensureActive()
                val req = buildMediaRequest(url, rangeHeader = "bytes=0-$firstEnd")
                try {
                    client.newCall(req).execute().use { resp ->
                        val code = resp.code
                        if (code !in 200..299) {
                            lastError = "Stream returned HTTP $code"
                            val retryable = code == 408 || code == 425 || code == 429 || code in 500..599
                            if (retryable && attempt < maxRetries) {
                                delay((attempt * 200L).coerceAtMost(1_000L))
                                continue
                            }
                            DevConsoleManager.logWarning(TAG, "Download probe error: $lastError")
                            return@withContext DownloadResult(false, error = lastError)
                        }
                        val body = resp.body ?: run {
                            lastError = "Empty HTTP response body"
                            return@withContext DownloadResult(false, error = lastError)
                        }
                        isFull200Body = code == 200
                        val contentRange = resp.header("Content-Range")
                        probeTotalBytes = when {
                            isFull200Body -> body.contentLength()
                            else -> contentRange?.substringAfter('/')?.toLongOrNull() ?: body.contentLength()
                        }

                        var offset = 0L
                        BufferedOutputStream(FileOutputStream(destFile), ioBufBytes).use { out ->
                            body.byteStream().use { input ->
                                val buf = ByteArray(ioBufBytes)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val r = input.read(buf)
                                    if (r < 0) break
                                    out.write(buf, 0, r)
                                    offset += r
                                    writtenBytesCounter.addAndGet(r.toLong())
                                    recordBytesTransferred(r.toLong())
                                    reportProgressIfChanged(probeTotalBytes)
                                }
                            }
                        }
                        probeWritten = offset
                        probeOk = true
                    }
                    if (probeOk) break
                } catch (e: Exception) {
                    lastError = e.message ?: "Connection error"
                    Log.w(TAG, "Probe failed (attempt $attempt/$maxRetries): $lastError")
                    if (attempt >= maxRetries) {
                        DevConsoleManager.logError(TAG, "Download probe failed after $attempt attempts: $lastError", e)
                        return@withContext DownloadResult(false, error = lastError)
                    }
                    delay((attempt * 200L).coerceAtMost(1_000L))
                }
            }

            if (!probeOk) {
                return@withContext DownloadResult(false, error = lastError)
            }

            // Single-response HTTP 200 or file completely covered by first range
            if (isFull200Body || probeTotalBytes <= 0L || probeWritten >= probeTotalBytes) {
                onProgress(100)
                val dt = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                DevConsoleManager.logInfo(
                    TAG,
                    "Completed ${destFile.name}: ${probeWritten / 1024} KB in ${dt}ms (${formatSpeed((probeWritten * 1000L) / dt)})",
                )
                return@withContext DownloadResult(true, bytesWritten = probeWritten)
            }

            // Phase 2: Remainder over parallel HTTP 206 workers writing via FileChannel
            val remainingStart = probeWritten
            val remainingBytes = probeTotalBytes - remainingStart
            if (remainingBytes <= 0L) {
                onProgress(100)
                return@withContext DownloadResult(true, bytesWritten = probeWritten)
            }

            val sliceCount: Int
            RandomAccessFile(destFile, "rw").use { raf ->
                if (raf.length() < probeTotalBytes) {
                    raf.setLength(probeTotalBytes)
                }
                val channel = raf.channel

                // Allow virtually any audio stream > 384 KiB to fan across parallel HTTP/2 range workers
                val minSliceBytes = 192L * 1024L
                sliceCount = if (parallelWorkers <= 1 || remainingBytes < minSliceBytes * 2L) {
                    1
                } else {
                    (remainingBytes / minSliceBytes).toInt().coerceIn(1, parallelWorkers)
                }

                val sliceSpan = (remainingBytes + sliceCount - 1L) / sliceCount
                val slices = (0 until sliceCount).mapNotNull { idx ->
                    val s = remainingStart + idx.toLong() * sliceSpan
                    val e = minOf(remainingStart + (idx.toLong() + 1L) * sliceSpan - 1L, probeTotalBytes - 1L)
                    if (s <= e) s to e else null
                }

                val sliceResults = coroutineScope {
                    slices.map { (sliceStart, sliceEnd) ->
                        async(Dispatchers.IO) {
                            fetchSliceIntoChannel(
                                client = client,
                                url = url,
                                sliceStart = sliceStart,
                                sliceEnd = sliceEnd,
                                chunkBytes = chunkBytes,
                                maxRetries = maxRetries,
                                ioBufBytes = ioBufBytes,
                                channel = channel,
                                onBytesWritten = { delta ->
                                    writtenBytesCounter.addAndGet(delta)
                                    recordBytesTransferred(delta)
                                    reportProgressIfChanged(probeTotalBytes)
                                },
                            )
                        }
                    }.awaitAll()
                }

                val failedErr = sliceResults.firstOrNull { it != null }
                if (failedErr != null) {
                    DevConsoleManager.logWarning(TAG, "Multi-range transfer failed ($sliceCount workers): $failedErr")
                    return@withContext DownloadResult(false, error = failedErr)
                }
            }

            onProgress(100)
            val finalSize = destFile.length()
            val dt = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val speedStr = formatSpeed((finalSize * 1000L) / dt)
            if (finalSize >= probeTotalBytes) {
                DevConsoleManager.logInfo(
                    TAG,
                    "Turbo HTTP/2 [$sliceCount workers] finished ${destFile.name}: ${finalSize / 1024} KB in ${dt}ms ($speedStr)",
                )
            }
            DownloadResult(
                success = finalSize >= probeTotalBytes,
                bytesWritten = finalSize,
                error = if (finalSize < probeTotalBytes) "Incomplete download ($finalSize/$probeTotalBytes B)" else null,
            )
        } catch (e: Exception) {
            DevConsoleManager.logError(TAG, "Download exception for ${destFile.name}: ${e.message}", e)
            DownloadResult(false, error = e.message ?: "Download error")
        } finally {
            endTransferSlot()
        }
    }

    private suspend fun fetchSliceIntoChannel(
        client: OkHttpClient,
        url: String,
        sliceStart: Long,
        sliceEnd: Long,
        chunkBytes: Long,
        maxRetries: Int,
        ioBufBytes: Int,
        channel: FileChannel,
        onBytesWritten: (Long) -> Unit,
    ): String? {
        var cursor = sliceStart
        val buf = ByteArray(ioBufBytes)
        while (cursor <= sliceEnd) {
            currentCoroutineContext().ensureActive()
            val subEnd = minOf(cursor + chunkBytes - 1L, sliceEnd)
            var attempt = 0
            var subDone = false
            var lastErr = "Slice transfer failed"
            while (attempt < maxRetries && !subDone) {
                attempt++
                currentCoroutineContext().ensureActive()
                val req = buildMediaRequest(url, rangeHeader = "bytes=$cursor-$subEnd")
                try {
                    client.newCall(req).execute().use { resp ->
                        val code = resp.code
                        if (code != 206) {
                            lastErr = if (code == 200) {
                                "Server rejected byte-range mid-stream"
                            } else {
                                "Slice HTTP $code"
                            }
                            val retryable = code == 408 || code == 425 || code == 429 || code in 500..599
                            if (retryable && attempt < maxRetries) {
                                delay((attempt * 200L).coerceAtMost(1_000L))
                                return@use
                            }
                            return lastErr
                        }
                        val contentRange = resp.header("Content-Range")
                        val rangeStart = contentRange
                            ?.substringAfter("bytes ", "")
                            ?.substringBefore('-')
                            ?.toLongOrNull()
                        if (rangeStart != null && rangeStart != cursor) {
                            return "Server returned invalid byte range offset ($rangeStart != $cursor)"
                        }
                        val body = resp.body ?: return "Empty byte-range body"
                        body.byteStream().use { input ->
                            while (cursor <= subEnd) {
                                currentCoroutineContext().ensureActive()
                                val maxRead = minOf(buf.size.toLong(), subEnd - cursor + 1L).toInt()
                                val r = input.read(buf, 0, maxRead)
                                if (r < 0) break
                                val bb = ByteBuffer.wrap(buf, 0, r)
                                var pos = cursor
                                while (bb.hasRemaining()) {
                                    val w = channel.write(bb, pos)
                                    pos += w
                                }
                                cursor += r
                                onBytesWritten(r.toLong())
                            }
                        }
                        if (cursor > subEnd) {
                            subDone = true
                        } else if (attempt < maxRetries) {
                            delay(150L)
                        } else {
                            return "Truncated slice @$cursor/$subEnd"
                        }
                    }
                } catch (e: Exception) {
                    lastErr = e.message ?: "Slice I/O error"
                    Log.w(TAG, "Slice @$cursor failed (attempt $attempt/$maxRetries): $lastErr")
                    if (attempt >= maxRetries) return lastErr
                    delay((attempt * 200L).coerceAtMost(1_000L))
                }
            }
            if (!subDone) return lastErr
        }
        return null
    }

    /** Fast pooled download for artwork/metadata payloads. */
    suspend fun downloadSmallFile(context: Context, url: String, destFile: File): Boolean =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) return@withContext false
            runCatching {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", DOWNLOAD_UA)
                    .build()
                okHttpClient(context).newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use false
                    val body = resp.body ?: return@use false
                    destFile.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(destFile), 64 * 1024).use { out ->
                        body.byteStream().copyTo(out, 32 * 1024)
                    }
                    destFile.length() > 0L
                }
            }.getOrDefault(false)
        }

    fun formatSpeed(bytesPerSec: Long): String = when {
        bytesPerSec <= 0L -> "Idle"
        bytesPerSec >= 1024L * 1024L -> String.format("%.2f MB/s", bytesPerSec / (1024.0 * 1024.0))
        bytesPerSec >= 1024L -> String.format("%.0f KB/s", bytesPerSec / 1024.0)
        else -> "$bytesPerSec B/s"
    }
}
