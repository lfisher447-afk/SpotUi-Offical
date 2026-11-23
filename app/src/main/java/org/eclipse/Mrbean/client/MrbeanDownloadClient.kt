package org.eclipse.Mrbean.client

import android.content.Context
import android.util.Log
import com.metrolist.innertube.network.AuthSessionStore
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
 * for SpotUI's Eclipse MrBean network engine.
 *
 * Key performance wins over legacy per-chunk HttpURLConnection:
 * 1. Shared warm [ConnectionPool] (24 sockets / 5m keep-alive) and raised [Dispatcher]
 *    (`maxRequests = 96`, `maxRequestsPerHost = 24`) eliminate repeated TCP+TLS handshakes.
 * 2. 5-minute TTL DNS cache wrapping DoH/System DNS removes repeated lookup stalls.
 * 3. Adaptive multi-range parallel chunk workers split large media fetches across multiple
 *    simultaneous byte ranges writing thread-safely via [FileChannel.write] positional I/O.
 * 4. Atomic 1-second rolling bandwidth meter exposes live MB/s speed telemetry.
 */
object MrbeanDownloadClient {
    private const val TAG = "MrbeanDownloadClient"
    private const val DOWNLOAD_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    private const val PLAYBACK_UA = "Spotui-MrBean/2.0 (Android Media3)"

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

    private val sharedConnectionPool = ConnectionPool(24, 5, TimeUnit.MINUTES)

    // Live throughput meter across all concurrent download workers
    private val activeTransfers = AtomicInteger(0)
    private val windowBytes = AtomicLong(0L)
    private val windowStartMs = AtomicLong(System.currentTimeMillis())
    private val _throughputBytesPerSec = MutableStateFlow(0L)
    val throughputBytesPerSec: StateFlow<Long> = _throughputBytesPerSec.asStateFlow()

    private fun recordBytesTransferred(bytes: Long) {
        if (bytes <= 0L) return
        val total = windowBytes.addAndGet(bytes)
        val now = System.currentTimeMillis()
        val start = windowStartMs.get()
        val elapsed = now - start
        if (elapsed >= 600L && windowStartMs.compareAndSet(start, now)) {
            val captured = windowBytes.getAndSet(0L)
            val rate = ((captured * 1000L) / elapsed.coerceAtLeast(1L)).coerceAtLeast(0L)
            _throughputBytesPerSec.value = rate
        } else if (elapsed in 1L..599L && _throughputBytesPerSec.value == 0L && total > 65_536L) {
            _throughputBytesPerSec.value = ((total * 1000L) / elapsed.coerceAtLeast(1L))
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
            val connectMs = if (tuning.enabled) tuning.connectTimeoutMs.toLong() else 12_000L
            val readMs = if (tuning.enabled) tuning.readTimeoutMs.toLong() else 25_000L
            val dispatcher = Dispatcher().apply {
                maxRequests = 96
                maxRequestsPerHost = 24
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
            val connectMs = if (tuning.enabled) tuning.connectTimeoutMs.toLong() else 15_000L
            val readMs = if (tuning.enabled) tuning.readTimeoutMs.toLong() else 30_000L
            val dispatcher = Dispatcher().apply {
                maxRequests = 48
                maxRequestsPerHost = 12
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

    private fun buildMediaRequest(url: String, rangeHeader: String? = null): Request =
        Request.Builder()
            .url(url)
            .header("User-Agent", DOWNLOAD_UA)
            .header("Accept-Encoding", "identity")
            .header("Accept", "*/*")
            .header("Referer", "https://music.youtube.com/")
            .header("Origin", "https://music.youtube.com")
            .apply {
                if (rangeHeader != null) header("Range", rangeHeader)
            }
            .build()

    /**
     * Downloads [url] into [destFile] using HTTP/2 connection pooling and optional multi-range
     * parallel slice workers. Automatically handles HTTP 200 (single-stream fallback) and
     * HTTP 206 Partial Content (parallel chunk workers writing to [FileChannel] offsets).
     */
    suspend fun downloadRangedToFile(
        context: Context,
        url: String,
        destFile: File,
        onProgress: (Int) -> Unit = {},
    ): DownloadResult = withContext(Dispatchers.IO) {
        val tuning = MrbeanNetworkSettings.snapshot(context.applicationContext)
        val client = okHttpClient(context)
        val parallelWorkers = if (tuning.enabled) tuning.parallelChunksPerFile.coerceIn(1, 6) else 1
        val maxRetries = if (tuning.enabled) tuning.rangeRetries.coerceIn(1, 6) else 2
        val chunkBytes = (tuning.chunkMiB.coerceIn(1, 16).toLong()) * 1024L * 1024L
        val ioBufBytes = tuning.ioBufferKb.coerceIn(64, 512) * 1024

        beginTransferSlot()
        try {
            onProgress(0)
            destFile.parentFile?.mkdirs()

            // When parallel slicing is active, probe a compact 1 MiB initial window so remaining
            // slices dispatch across concurrent HTTP/2 streams immediately.
            val firstWindowBytes = if (parallelWorkers > 1) minOf(chunkBytes, 1024L * 1024L) else chunkBytes
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
                                delay((attempt * 250L).coerceAtMost(1_200L))
                                continue
                            }
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
                        return@withContext DownloadResult(false, error = lastError)
                    }
                    delay((attempt * 250L).coerceAtMost(1_200L))
                }
            }

            if (!probeOk) {
                return@withContext DownloadResult(false, error = lastError)
            }

            // Single-response HTTP 200 or file completely covered by first range
            if (isFull200Body || probeTotalBytes <= 0L || probeWritten >= probeTotalBytes) {
                onProgress(100)
                return@withContext DownloadResult(true, bytesWritten = probeWritten)
            }

            // Phase 2: Remainder over parallel or chunked byte-ranges written directly to FileChannel
            val remainingStart = probeWritten
            val remainingBytes = probeTotalBytes - remainingStart
            if (remainingBytes <= 0L) {
                onProgress(100)
                return@withContext DownloadResult(true, bytesWritten = probeWritten)
            }

            RandomAccessFile(destFile, "rw").use { raf ->
                if (raf.length() < probeTotalBytes) {
                    raf.setLength(probeTotalBytes)
                }
                val channel = raf.channel

                // Choose slice count: allow even 3-5 MiB files to fan across 2-4 workers
                val minSliceBytes = 256L * 1024L
                val sliceCount = if (parallelWorkers <= 1 || remainingBytes < minSliceBytes * 2L) {
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
                    return@withContext DownloadResult(false, error = failedErr)
                }
            }

            onProgress(100)
            val finalSize = destFile.length()
            DownloadResult(
                success = finalSize >= probeTotalBytes,
                bytesWritten = finalSize,
                error = if (finalSize < probeTotalBytes) "Incomplete download ($finalSize/$probeTotalBytes B)" else null,
            )
        } catch (e: Exception) {
            DownloadResult(false, error = e.message ?: "Download error")
        } finally {
            endTransferSlot()
        }
    }

    /**
     * Fetches `[sliceStart .. sliceEnd]` in up to [chunkBytes] sub-ranges, writing thread-safely
     * at exact offsets in [channel]. Returns `null` on success, or an error string on failure.
     */
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
                                delay((attempt * 250L).coerceAtMost(1_200L))
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
                            delay(200L)
                        } else {
                            return "Truncated slice @$cursor/$subEnd"
                        }
                    }
                } catch (e: Exception) {
                    lastErr = e.message ?: "Slice I/O error"
                    Log.w(TAG, "Slice @$cursor failed (attempt $attempt/$maxRetries): $lastErr")
                    if (attempt >= maxRetries) return lastErr
                    delay((attempt * 250L).coerceAtMost(1_200L))
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
