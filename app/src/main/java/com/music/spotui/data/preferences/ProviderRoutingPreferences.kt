package com.music.spotui.data.preferences

import android.content.Context

/** User-owned routing policy for the built-in lossless resolver. */
enum class LosslessProviderRoute(val id: String, val label: String, val detail: String) {
    TIDAL("tidal", "Tidal", "Public & Monochrome/Squid.wtf 24-bit/16-bit lossless"),
    QOBUZ("qobuz", "Qobuz", "ISRC-linked hi-res or CD-quality lossless"),
    AMAZON("amazon", "Amazon Music", "Community resolver candidate with full-length validation"),
    DEEZER("deezer", "Deezer", "CD-quality lossless (supports linked Deezer ARL / custom mirror)"),
}

object ProviderRoutingPreferences {
    private const val PREF = "provider_routing"
    private const val KEY_ORDER = "lossless_provider_order"
    private const val KEY_ENABLED = "lossless_enabled_providers"
    private const val KEY_SOLO_PRIMARY = "lossless_solo_primary"
    private const val KEY_STRICT_FULL_LENGTH = "strict_full_length"
    private const val DEFAULT = "tidal,qobuz,amazon,deezer"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun losslessOrder(context: Context): List<String> {
        val saved = prefs(context).getString(KEY_ORDER, DEFAULT).orEmpty()
        val known = LosslessProviderRoute.entries.map { it.id }.toSet()
        val selected = saved.split(',').map(String::trim).filter { it in known }.distinct()
        return (selected + LosslessProviderRoute.entries.map { it.id }).distinct()
    }

    fun setLosslessOrder(context: Context, order: List<String>) {
        val known = LosslessProviderRoute.entries.map { it.id }.toSet()
        val normalized = (order.filter { it in known }.distinct() + LosslessProviderRoute.entries.map { it.id }).distinct()
        prefs(context).edit().putString(KEY_ORDER, normalized.joinToString(",")).apply()
        signalUiSettingsChanged()
    }

    fun enabledProviders(context: Context): Set<String> {
        val known = LosslessProviderRoute.entries.map { it.id }.toSet()
        val raw = prefs(context).getString(KEY_ENABLED, null) ?: return known
        val parsed = raw.split(',').map(String::trim).filter { it in known }.toSet()
        return parsed.ifEmpty { known }
    }

    fun setProviderEnabled(context: Context, providerId: String, enabled: Boolean): Set<String> {
        val known = LosslessProviderRoute.entries.map { it.id }.toSet()
        if (providerId !in known) return enabledProviders(context)
        val current = enabledProviders(context).toMutableSet()
        if (enabled) {
            current.add(providerId)
        } else if (current.size > 1) {
            current.remove(providerId)
        }
        prefs(context).edit().putString(KEY_ENABLED, current.joinToString(",")).apply()
        signalUiSettingsChanged()
        return current
    }

    fun soloProvider(context: Context): Boolean = prefs(context).getBoolean(KEY_SOLO_PRIMARY, false)
    fun setSoloProvider(context: Context, solo: Boolean) {
        prefs(context).edit().putBoolean(KEY_SOLO_PRIMARY, solo).apply()
        signalUiSettingsChanged()
    }

    /** Moves [providerId] to primary priority (index 0) and guarantees it is enabled. */
    fun setPrimaryProvider(context: Context, providerId: String): List<String> {
        val known = LosslessProviderRoute.entries.map { it.id }.toSet()
        if (providerId !in known) return losslessOrder(context)
        val reordered = (listOf(providerId) + losslessOrder(context).filterNot { it == providerId }).distinct()
        setLosslessOrder(context, reordered)
        setProviderEnabled(context, providerId, true)
        return reordered
    }

    fun preferFullLength(context: Context): Boolean = prefs(context).getBoolean(KEY_STRICT_FULL_LENGTH, true)
    fun setPreferFullLength(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_STRICT_FULL_LENGTH, value).apply()
        signalUiSettingsChanged()
    }
}
