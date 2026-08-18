package com.music.spotui.data.preferences

import android.content.Context

/** User-owned routing policy for the built-in lossless resolver. */
enum class LosslessProviderRoute(val id: String, val label: String, val detail: String) {
    TIDAL("tidal", "Tidal", "Lossless source when a verified full-length match is available"),
    QOBUZ("qobuz", "Qobuz", "ISRC-linked high-resolution or CD-quality match"),
    AMAZON("amazon", "Amazon Music", "Community resolver candidate with full-length validation"),
    DEEZER("deezer", "Deezer", "ISRC-linked CD-quality candidate"),
}

object ProviderRoutingPreferences {
    private const val PREF = "provider_routing"
    private const val KEY_ORDER = "lossless_provider_order"
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

    fun preferFullLength(context: Context): Boolean = prefs(context).getBoolean(KEY_STRICT_FULL_LENGTH, true)
    fun setPreferFullLength(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_STRICT_FULL_LENGTH, value).apply()
        signalUiSettingsChanged()
    }
}
