package org.eclipse.Mrbean.client

import android.content.Context

/**
 * Persistent transport tuning for Spotui's media resolver and ranged-download
 * engine. These controls deliberately govern only the app-owned media requests;
 * Android's secure TLS stack remains responsible for protocol negotiation.
 */
object MrbeanNetworkSettings {
    private const val PREFS = "mrbean_network_settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_CANDIDATE_LIMIT = "candidate_limit"
    private const val KEY_CONNECT_TIMEOUT = "connect_timeout_ms"
    private const val KEY_READ_TIMEOUT = "read_timeout_ms"
    private const val KEY_CHUNK_MIB = "chunk_mib"
    private const val KEY_RETRIES = "range_retries"

    data class Snapshot(
        val enabled: Boolean,
        val candidateLimit: Int,
        val connectTimeoutMs: Int,
        val readTimeoutMs: Int,
        val chunkMiB: Int,
        val rangeRetries: Int,
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun snapshot(context: Context): Snapshot = Snapshot(
        enabled = prefs(context).getBoolean(KEY_ENABLED, true),
        candidateLimit = candidateLimit(context),
        connectTimeoutMs = connectTimeoutMs(context),
        readTimeoutMs = readTimeoutMs(context),
        chunkMiB = chunkMiB(context),
        rangeRetries = rangeRetries(context),
    )

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)
    fun setEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()

    fun candidateLimit(context: Context): Int =
        prefs(context).getInt(KEY_CANDIDATE_LIMIT, 6).coerceIn(3, 8)

    fun setCandidateLimit(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_CANDIDATE_LIMIT, value.coerceIn(3, 8)).apply()

    fun connectTimeoutMs(context: Context): Int =
        prefs(context).getInt(KEY_CONNECT_TIMEOUT, 15_000).coerceIn(5_000, 45_000)

    fun setConnectTimeoutMs(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_CONNECT_TIMEOUT, value.coerceIn(5_000, 45_000)).apply()

    fun readTimeoutMs(context: Context): Int =
        prefs(context).getInt(KEY_READ_TIMEOUT, 30_000).coerceIn(10_000, 90_000)

    fun setReadTimeoutMs(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_READ_TIMEOUT, value.coerceIn(10_000, 90_000)).apply()

    fun chunkMiB(context: Context): Int =
        prefs(context).getInt(KEY_CHUNK_MIB, 8).coerceIn(1, 16)

    fun setChunkMiB(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_CHUNK_MIB, value.coerceIn(1, 16)).apply()

    fun rangeRetries(context: Context): Int =
        prefs(context).getInt(KEY_RETRIES, 3).coerceIn(1, 5)

    fun setRangeRetries(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_RETRIES, value.coerceIn(1, 5)).apply()

    fun reset(context: Context) = prefs(context).edit().clear().apply()
}
