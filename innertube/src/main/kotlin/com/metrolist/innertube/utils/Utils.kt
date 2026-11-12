package com.metrolist.innertube.utils

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.LibraryPage
import com.metrolist.innertube.pages.PlaylistPage
import java.security.MessageDigest

@JvmName("completedLibrary")
suspend fun Result<PlaylistPage>.completed(): Result<PlaylistPage> = runCatching {
    val page = getOrThrow()
    val songs = page.songs.toMutableList()
    var continuation = page.songsContinuation
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0
    val maxRequests = 50
    var consecutiveEmptyResponses = 0
    
    while (continuation != null && requestCount < maxRequests) {
        if (continuation in seenContinuations) {
            break
        }
        seenContinuations.add(continuation)
        requestCount++
        
        val continuationPage = YouTube.playlistContinuation(continuation).getOrNull() ?: break
        
        if (continuationPage.songs.isEmpty()) {
            consecutiveEmptyResponses++
            if (consecutiveEmptyResponses >= 2) break
        } else {
            consecutiveEmptyResponses = 0
            songs += continuationPage.songs
        }
        
        continuation = continuationPage.continuation
    }
    PlaylistPage(
        playlist = page.playlist,
        songs = songs,
        songsContinuation = null,
        continuation = page.continuation
    )
}

@JvmName("completedPlaylist")
suspend fun Result<LibraryPage>.completed(): Result<LibraryPage> = runCatching {
    val page = getOrThrow()
    val items = page.items.toMutableList()
    var continuation = page.continuation
    val seenContinuations = mutableSetOf<String>()
    var requestCount = 0
    val maxRequests = 50
    var consecutiveEmptyResponses = 0
    
    while (continuation != null && requestCount < maxRequests) {
        if (continuation in seenContinuations) {
            break
        }
        seenContinuations.add(continuation)
        requestCount++
        
        val continuationPage = YouTube.libraryContinuation(continuation).getOrNull() ?: break
        
        if (continuationPage.items.isEmpty()) {
            consecutiveEmptyResponses++
            if (consecutiveEmptyResponses >= 2) break
        } else {
            consecutiveEmptyResponses = 0
            items += continuationPage.items
        }
        
        continuation = continuationPage.continuation
    }
    LibraryPage(
        items = items,
        continuation = null
    )
}

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun sha1(str: String): String = MessageDigest.getInstance("SHA-1").digest(str.toByteArray()).toHex()

fun parseCookieString(cookie: String): Map<String, String> =
    cookie.split(";")
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { part ->
            val splitIndex = part.indexOf('=')
            if (splitIndex <= 0) {
                null
            } else {
                val key = part.substring(0, splitIndex).trim()
                val value = part.substring(splitIndex + 1).trim()
                if (key.isEmpty()) null else key to value
            }
        }
        .toMap()

private val YOUTUBE_LOGIN_COOKIE_KEYS = listOf("SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID")

fun hasYouTubeLoginCookie(cookie: String?): Boolean {
    if (cookie.isNullOrBlank()) return false
    val cookieMap = parseCookieString(cookie)
    return YOUTUBE_LOGIN_COOKIE_KEYS.any { key -> !cookieMap[key].isNullOrBlank() }
}

fun youtubeLoginCookieValue(cookie: String?): String? {
    if (cookie.isNullOrBlank()) return null
    val cookieMap = parseCookieString(cookie)
    return YOUTUBE_LOGIN_COOKIE_KEYS.firstNotNullOfOrNull { key ->
        cookieMap[key]?.takeIf(String::isNotBlank)
    }
}

fun sapisidAuthorizationHeader(
    cookie: String?,
    origin: String = com.metrolist.innertube.models.YouTubeClient.ORIGIN_YOUTUBE_MUSIC,
    timestampSeconds: Long = System.currentTimeMillis() / 1000L,
): String? {
    if (cookie.isNullOrBlank()) return null
    val cookieMap = parseCookieString(cookie)
    val parts = mutableListOf<String>()
    cookieMap["SAPISID"]?.takeIf(String::isNotBlank)?.let { sapisid ->
        parts += "SAPISIDHASH ${timestampSeconds}_${sha1("$timestampSeconds $sapisid $origin")}"
    }
    cookieMap["__Secure-1PAPISID"]?.takeIf(String::isNotBlank)?.let { sapisid1p ->
        parts += "SAPISID1PHASH ${timestampSeconds}_${sha1("$timestampSeconds $sapisid1p $origin")}"
    }
    cookieMap["__Secure-3PAPISID"]?.takeIf(String::isNotBlank)?.let { sapisid3p ->
        parts += "SAPISID3PHASH ${timestampSeconds}_${sha1("$timestampSeconds $sapisid3p $origin")}"
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
}

fun String.parseTime(): Int? {
    try {
        // YouTube Music returns duration with locale-dependent separators
        // (":" en-US, "." some locales, "," EU). Accept all.
        val parts = split(Regex("[:.,]")).map { it.toInt() }
        if (parts.size == 2) {
            return parts[0] * 60 + parts[1]
        }
        if (parts.size == 3) {
            return parts[0] * 3600 + parts[1] * 60 + parts[2]
        }
    } catch (e: Exception) {
        return null
    }
    return null
}

fun isPrivateId(browseId: String): Boolean {
    return browseId.contains("privately")
}
