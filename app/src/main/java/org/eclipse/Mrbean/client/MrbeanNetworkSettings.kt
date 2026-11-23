package org.eclipse.Mrbean.client

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistent transport tuning for Spotui's media resolver and high-speed
 * multi-stream ranged-download engine. These controls govern only the
 * app-owned media requests; Android's secure TLS stack remains responsible
 * for protocol negotiation.
 */
object MrbeanNetworkSettings {
    private const val PREFS = "mrbean_network_settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_CANDIDATE_LIMIT = "candidate_limit"
    private const val KEY_CONNECT_TIMEOUT = "connect_timeout_ms"
    private const val KEY_READ_TIMEOUT = "read_timeout_ms"
    private const val KEY_CHUNK_MIB = "chunk_mib"
    private const val KEY_RETRIES = "range_retries"
    private const val KEY_MAX_CONCURRENT_DOWNLOADS = "max_concurrent_downloads"
    private const val KEY_PARALLEL_CHUNKS_PER_FILE = "parallel_chunks_per_file"
    private const val KEY_IO_BUFFER_KB = "io_buffer_kb"
    private const val KEY_HTTP2_MULTIPLEXING = "http2_multiplexing"
    private const val KEY_PREFETCH_METADATA_ASYNC = "prefetch_metadata_async"

    const val MIN_CONCURRENT_DOWNLOADS = 1
    const val MAX_CONCURRENT_DOWNLOADS = 10

    private val _settingsRevision = MutableStateFlow(0)
    val settingsRevision: StateFlow<Int> = _settingsRevision.asStateFlow()

    data class Snapshot(
        val enabled: Boolean,
        val candidateLimit: Int,
        val connectTimeoutMs: Int,
        val readTimeoutMs: Int,
        val chunkMiB: Int,
        val rangeRetries: Int,
        val maxConcurrentDownloads: Int,
        val parallelChunksPerFile: Int,
        val ioBufferKb: Int,
        val http2Multiplexing: Boolean,
        val prefetchMetadataInParallel: Boolean,
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun bumpRevision() {
        _settingsRevision.value = _settingsRevision.value + 1
    }

    fun snapshot(context: Context): Snapshot = Snapshot(
        enabled = isEnabled(context),
        candidateLimit = candidateLimit(context),
        connectTimeoutMs = connectTimeoutMs(context),
        readTimeoutMs = readTimeoutMs(context),
        chunkMiB = chunkMiB(context),
        rangeRetries = rangeRetries(context),
        maxConcurrentDownloads = maxConcurrentDownloads(context),
        parallelChunksPerFile = parallelChunksPerFile(context),
        ioBufferKb = ioBufferKb(context),
        http2Multiplexing = http2Multiplexing(context),
        prefetchMetadataInParallel = prefetchMetadataInParallel(context),
    )

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)
    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
        bumpRevision()
    }

    fun candidateLimit(context: Context): Int =
        prefs(context).getInt(KEY_CANDIDATE_LIMIT, 6).coerceIn(3, 10)

    fun setCandidateLimit(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_CANDIDATE_LIMIT, value.coerceIn(3, 10)).apply()
        bumpRevision()
    }

    fun connectTimeoutMs(context: Context): Int =
        prefs(context).getInt(KEY_CONNECT_TIMEOUT, 10_000).coerceIn(3_000, 45_000)

    fun setConnectTimeoutMs(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_CONNECT_TIMEOUT, value.coerceIn(3_000, 45_000)).apply()
        bumpRevision()
    }

    fun readTimeoutMs(context: Context): Int =
        prefs(context).getInt(KEY_READ_TIMEOUT, 20_000).coerceIn(5_000, 90_000)

    fun setReadTimeoutMs(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_READ_TIMEOUT, value.coerceIn(5_000, 90_000)).apply()
        bumpRevision()
    }

    fun chunkMiB(context: Context): Int =
        prefs(context).getInt(KEY_CHUNK_MIB, 4).coerceIn(1, 16)

    fun setChunkMiB(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_CHUNK_MIB, value.coerceIn(1, 16)).apply()
        bumpRevision()
    }

    fun rangeRetries(context: Context): Int =
        prefs(context).getInt(KEY_RETRIES, 3).coerceIn(1, 6)

    fun setRangeRetries(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_RETRIES, value.coerceIn(1, 6)).apply()
        bumpRevision()
    }

    /** Number of tracks downloaded concurrently during playlist/album/bulk downloads (1..10). */
    fun maxConcurrentDownloads(context: Context): Int =
        prefs(context).getInt(KEY_MAX_CONCURRENT_DOWNLOADS, 4)
            .coerceIn(MIN_CONCURRENT_DOWNLOADS, MAX_CONCURRENT_DOWNLOADS)

    fun setMaxConcurrentDownloads(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(
                KEY_MAX_CONCURRENT_DOWNLOADS,
                value.coerceIn(MIN_CONCURRENT_DOWNLOADS, MAX_CONCURRENT_DOWNLOADS),
            )
            .apply()
        bumpRevision()
    }

    /** Number of parallel HTTP Range workers split per single file download (1..6). */
    fun parallelChunksPerFile(context: Context): Int =
        prefs(context).getInt(KEY_PARALLEL_CHUNKS_PER_FILE, 4).coerceIn(1, 6)

    fun setParallelChunksPerFile(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_PARALLEL_CHUNKS_PER_FILE, value.coerceIn(1, 6)).apply()
        bumpRevision()
    }

    /** File copy/stream I/O buffer in KiB (64..512). */
    fun ioBufferKb(context: Context): Int =
        prefs(context).getInt(KEY_IO_BUFFER_KB, 256).coerceIn(64, 512)

    fun setIoBufferKb(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_IO_BUFFER_KB, value.coerceIn(64, 512)).apply()
        bumpRevision()
    }

    fun http2Multiplexing(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HTTP2_MULTIPLEXING, true)

    fun setHttp2Multiplexing(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HTTP2_MULTIPLEXING, enabled).apply()
        bumpRevision()
    }

    /** Downloads album covers & caches lyrics asynchronously so track workers finish immediately. */
    fun prefetchMetadataInParallel(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PREFETCH_METADATA_ASYNC, true)

    fun setPrefetchMetadataInParallel(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PREFETCH_METADATA_ASYNC, enabled).apply()
        bumpRevision()
    }

    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
        bumpRevision()
    }
}
