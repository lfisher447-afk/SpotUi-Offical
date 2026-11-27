package com.music.spotui.ui.screens.SpotifyLogin.helper

import android.content.Context
import com.metrolist.spotify.SpotifyAuth
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.util.UUID

@Serializable
data class BrowserBookmark(
    val id: String,
    val title: String,
    val url: String,
    val folder: String = "Auth",
    val pinned: Boolean = false,
    val createdAtMs: Long = 0L,
)

@Serializable
data class BrowserHistoryEntry(
    val url: String,
    val title: String,
    val visitedAtMs: Long,
)

/**
 * Persistent bookmarks, pinned quick-bar shortcuts, and browsing history store for the
 * SpotUI Auth Browser Engine (`SpotifyLoginScreen`).
 */
object LoginBrowserBookmarkStore {
    private const val PREFS_NAME = "spotui_auth_browser_v1"
    private const val KEY_BOOKMARKS = "bookmarks_json"
    private const val KEY_HISTORY = "history_json"
    private const val KEY_HOME_URL = "home_url"
    private const val KEY_INCOGNITO = "incognito_mode"
    private const val MAX_HISTORY_ITEMS = 80

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    val defaultBookmarks: List<BrowserBookmark> = listOf(
        BrowserBookmark(
            id = "bm_spotify_login",
            title = "Spotify Sign In",
            url = SpotifyAuth.LOGIN_URL,
            folder = "Spotify",
            pinned = true,
            createdAtMs = 1L,
        ),
        BrowserBookmark(
            id = "bm_spotify_web",
            title = "Spotify Web Player",
            url = "https://open.spotify.com/",
            folder = "Spotify",
            pinned = true,
            createdAtMs = 2L,
        ),
        BrowserBookmark(
            id = "bm_spotify_account",
            title = "Spotify Account",
            url = "https://www.spotify.com/account/overview/",
            folder = "Spotify",
            pinned = false,
            createdAtMs = 3L,
        ),
        BrowserBookmark(
            id = "bm_spotify_dev",
            title = "Spotify Dev Dashboard",
            url = "https://developer.spotify.com/dashboard",
            folder = "Spotify",
            pinned = true,
            createdAtMs = 4L,
        ),
        BrowserBookmark(
            id = "bm_ytm",
            title = "YouTube Music",
            url = "https://music.youtube.com/",
            folder = "Streaming",
            pinned = true,
            createdAtMs = 5L,
        ),
        BrowserBookmark(
            id = "bm_deezer",
            title = "Deezer Login",
            url = "https://www.deezer.com/login",
            folder = "Streaming",
            pinned = true,
            createdAtMs = 6L,
        ),
        BrowserBookmark(
            id = "bm_local_portal",
            title = "Local URL Portal",
            url = "http://127.0.0.1:8765/",
            folder = "Tools",
            pinned = true,
            createdAtMs = 7L,
        ),
        BrowserBookmark(
            id = "bm_releases",
            title = "SpotUI Releases",
            url = "https://github.com/lfisher447-afk/SpotUi-Offical/releases",
            folder = "Tools",
            pinned = false,
            createdAtMs = 8L,
        ),
    )

    fun loadBookmarks(context: Context): List<BrowserBookmark> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_BOOKMARKS, null)
        if (raw.isNullOrBlank()) {
            saveBookmarks(context, defaultBookmarks)
            return defaultBookmarks
        }
        return runCatching {
            val parsed = json.decodeFromString<List<BrowserBookmark>>(raw)
            parsed.ifEmpty { defaultBookmarks }
        }.getOrElse {
            defaultBookmarks
        }
    }

    fun saveBookmarks(context: Context, bookmarks: List<BrowserBookmark>) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BOOKMARKS, json.encodeToString(bookmarks)).apply()
    }

    fun addOrUpdateBookmark(
        context: Context,
        title: String,
        url: String,
        folder: String = "Bookmarks",
        pinned: Boolean = true,
        existingId: String? = null,
    ): List<BrowserBookmark> {
        val normalizedUrl = normalizeBrowserInput(url)
        val cleanTitle = title.trim().ifBlank { extractHostLabel(normalizedUrl) }
        val cleanFolder = folder.trim().ifBlank { "Bookmarks" }
        val current = loadBookmarks(context).toMutableList()
        val matchIdx = current.indexOfFirst {
            (existingId != null && it.id == existingId) || urlsEquivalent(it.url, normalizedUrl)
        }
        val item = BrowserBookmark(
            id = existingId ?: current.getOrNull(matchIdx)?.id ?: "bm_${UUID.randomUUID().toString().take(8)}",
            title = cleanTitle,
            url = normalizedUrl,
            folder = cleanFolder,
            pinned = pinned,
            createdAtMs = if (matchIdx >= 0) current[matchIdx].createdAtMs else System.currentTimeMillis(),
        )
        if (matchIdx >= 0) {
            current[matchIdx] = item
        } else {
            current.add(0, item)
        }
        saveBookmarks(context, current)
        return current
    }

    fun removeBookmark(context: Context, id: String): List<BrowserBookmark> {
        val updated = loadBookmarks(context).filterNot { it.id == id }
        saveBookmarks(context, updated)
        return updated
    }

    fun togglePin(context: Context, id: String): List<BrowserBookmark> {
        val updated = loadBookmarks(context).map {
            if (it.id == id) it.copy(pinned = !it.pinned) else it
        }
        saveBookmarks(context, updated)
        return updated
    }

    fun resetDefaultBookmarks(context: Context): List<BrowserBookmark> {
        saveBookmarks(context, defaultBookmarks)
        return defaultBookmarks
    }

    fun isBookmarked(bookmarks: List<BrowserBookmark>, url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return bookmarks.any { urlsEquivalent(it.url, url) }
    }

    fun findByUrl(bookmarks: List<BrowserBookmark>, url: String?): BrowserBookmark? {
        if (url.isNullOrBlank()) return null
        return bookmarks.firstOrNull { urlsEquivalent(it.url, url) }
    }

    fun filterBookmarks(
        bookmarks: List<BrowserBookmark>,
        query: String,
        folder: String = "All",
    ): List<BrowserBookmark> {
        val q = query.trim().lowercase()
        return bookmarks.filter { item ->
            val folderMatches = folder == "All" || item.folder.equals(folder, ignoreCase = true)
            val queryMatches = q.isBlank() ||
                item.title.lowercase().contains(q) ||
                item.url.lowercase().contains(q) ||
                item.folder.lowercase().contains(q)
            folderMatches && queryMatches
        }
    }

    fun exportBookmarksJson(bookmarks: List<BrowserBookmark>): String =
        json.encodeToString(bookmarks)

    fun importBookmarksJson(context: Context, jsonText: String, merge: Boolean = true): Result<List<BrowserBookmark>> =
        runCatching {
            val imported = json.decodeFromString<List<BrowserBookmark>>(jsonText.trim())
                .filter { it.url.isNotBlank() }
                .map {
                    it.copy(
                        id = it.id.ifBlank { "bm_${UUID.randomUUID().toString().take(8)}" },
                        title = it.title.ifBlank { extractHostLabel(it.url) },
                        url = normalizeBrowserInput(it.url),
                        folder = it.folder.ifBlank { "Imported" },
                    )
                }
            if (imported.isEmpty()) throw IllegalArgumentException("No valid bookmarks found in JSON")
            val finalList = if (!merge) {
                imported
            } else {
                val merged = loadBookmarks(context).toMutableList()
                imported.forEach { candidate ->
                    val existingIdx = merged.indexOfFirst { urlsEquivalent(it.url, candidate.url) }
                    if (existingIdx >= 0) merged[existingIdx] = candidate else merged.add(candidate)
                }
                merged
            }
            saveBookmarks(context, finalList)
            finalList
        }

    fun loadHistory(context: Context): List<BrowserHistoryEntry> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<BrowserHistoryEntry>>(raw) }.getOrDefault(emptyList())
    }

    fun recordHistory(context: Context, url: String, title: String): List<BrowserHistoryEntry> {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank() || cleanUrl == "about:blank") return loadHistory(context)
        val cleanTitle = title.trim().ifBlank { extractHostLabel(cleanUrl) }
        val current = loadHistory(context).toMutableList()
        current.removeAll { it.url == cleanUrl }
        current.add(0, BrowserHistoryEntry(cleanUrl, cleanTitle, System.currentTimeMillis()))
        val trimmed = current.take(MAX_HISTORY_ITEMS)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_HISTORY, json.encodeToString(trimmed)).apply()
        return trimmed
    }

    fun clearHistory(context: Context): List<BrowserHistoryEntry> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HISTORY).apply()
        return emptyList()
    }

    fun getHomeUrl(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_HOME_URL, SpotifyAuth.LOGIN_URL)?.takeIf { it.isNotBlank() }
            ?: SpotifyAuth.LOGIN_URL
    }

    fun setHomeUrl(context: Context, url: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_HOME_URL, normalizeBrowserInput(url)).apply()
    }

    fun isIncognito(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_INCOGNITO, false)

    fun setIncognito(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_INCOGNITO, enabled).apply()
    }

    fun normalizeBrowserInput(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return SpotifyAuth.LOGIN_URL
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }
        val lower = trimmed.lowercase()
        if (lower.startsWith("127.0.0.1") ||
            lower.startsWith("localhost") ||
            lower.startsWith("192.168.") ||
            lower.startsWith("10.")
        ) {
            return "http://$trimmed"
        }
        val looksLikeHost = !trimmed.contains(' ') && trimmed.contains('.')
        if (looksLikeHost) {
            return "https://$trimmed"
        }
        val encodedQuery = runCatching { URLEncoder.encode(trimmed, "UTF-8") }.getOrDefault(trimmed)
        return "https://duckduckgo.com/?q=$encodedQuery"
    }

    fun extractHostLabel(url: String): String = runCatching {
        val host = android.net.Uri.parse(url).host.orEmpty()
        host.removePrefix("www.").ifBlank { url.take(28) }
    }.getOrDefault(url.take(28))

    private fun urlsEquivalent(a: String, b: String): Boolean {
        val cleanA = a.trim().trimEnd('/').lowercase()
        val cleanB = b.trim().trimEnd('/').lowercase()
        return cleanA == cleanB
    }
}
