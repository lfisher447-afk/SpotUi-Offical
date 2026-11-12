package com.music.spotui.data.update

import android.content.Context
import android.util.Log
import com.music.spotui.BuildConfig
import com.music.spotui.data.preferences.getUpdateRepoUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val PREFS = "update_prefs"
    private const val KEY_SKIP = "skip_fingerprint"

    data class UpdateInfo(
        val version: String,
        val downloadUrl: String,
        val fingerprint: String,
        val releaseBody: String,
        val isPrerelease: Boolean = false,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    sealed class ManualCheckResult {
        data class UpdateAvailable(val info: UpdateInfo) : ManualCheckResult()
        data class UpToDate(val installedVersion: String) : ManualCheckResult()
        data class Failed(val reason: String) : ManualCheckResult()
    }

    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val info = runCatching { fetchLatestRelease(context) }
            .onFailure { Log.d(TAG, "update check failed: ${it.message}") }
            .getOrNull() ?: return@withContext null
        if (!isNewer(info.version, BuildConfig.VERSION_NAME)) return@withContext null
        val skipped = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SKIP, null)
        if (skipped == info.fingerprint) return@withContext null
        info
    }

    suspend fun checkManual(context: Context): ManualCheckResult = withContext(Dispatchers.IO) {
        val result = runCatching { fetchLatestRelease(context) }
        val info = result.getOrNull()
        when {
            result.isFailure -> ManualCheckResult.Failed(result.exceptionOrNull()?.message ?: "Network error")
            info == null -> ManualCheckResult.UpToDate(BuildConfig.VERSION_NAME)
            isNewer(info.version, BuildConfig.VERSION_NAME) -> ManualCheckResult.UpdateAvailable(info)
            else -> ManualCheckResult.UpToDate(BuildConfig.VERSION_NAME)
        }
    }

    fun skipRelease(context: Context, info: UpdateInfo) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SKIP, info.fingerprint).apply()
    }

    internal fun buildLatestReleaseApiUrl(repoUrl: String): String {
        val clean = repoUrl.trim().trimEnd('/').removeSuffix(".git")
        val repoPath = clean
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .trim('/')
        return "https://api.github.com/repos/$repoPath/releases/latest"
    }

    internal fun selectBestApkUrl(assets: org.json.JSONArray?): String? {
        if (assets == null) return null
        val items = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
        val apkItems = items.filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
        val releaseApk = apkItems.firstOrNull { it.optString("name").contains("release", ignoreCase = true) }
        return (releaseApk ?: apkItems.firstOrNull())?.optString("browser_download_url")?.ifBlank { null }
    }

    internal fun parseReleasesAtom(xml: String, repoUrl: String): UpdateInfo? {
        return runCatching {
            val entryMatch = Regex("""<entry>(.*?)</entry>""", RegexOption.DOT_MATCHES_ALL).find(xml) ?: return null
            val entryContent = entryMatch.groupValues[1]

            val entryTitle = Regex("""<title[^>]*>(.*?)</title>""").find(entryContent)?.groupValues?.get(1)?.trim().orEmpty()
            val link = Regex("""<link[^>]*href=["']([^"']+)["']""").find(entryContent)?.groupValues?.get(1)?.trim().orEmpty()
            val updated = Regex("""<updated>(.*?)</updated>""").find(entryContent)?.groupValues?.get(1)?.trim().orEmpty()
            val content = Regex("""<content[^>]*>(.*?)</content>""", RegexOption.DOT_MATCHES_ALL).find(entryContent)?.groupValues?.get(1)?.trim().orEmpty()

            val version = extractVersion(entryTitle) ?: extractVersion(link) ?: return null
            val cleanBody = content
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace(Regex("""<p[^>]*>"""), "")
                .replace("</p>", "")
                .trim()
            val isPrerelease = entryTitle.contains("pre", ignoreCase = true) ||
                entryTitle.contains("beta", ignoreCase = true) ||
                entryTitle.contains("rc", ignoreCase = true) ||
                link.contains("pre", ignoreCase = true)
            UpdateInfo(
                version = version,
                downloadUrl = link.ifBlank { "$repoUrl/releases/latest" },
                fingerprint = "$version:$updated",
                releaseBody = cleanBody,
                isPrerelease = isPrerelease,
            )
        }.getOrNull()
    }

    private fun fetchLatestRelease(context: Context): UpdateInfo? {
        val repoUrl = getUpdateRepoUrl(context).trim().trimEnd('/').removeSuffix(".git")
        val apiLatest = buildLatestReleaseApiUrl(repoUrl)
        val releasesPage = "$repoUrl/releases/latest"

        val request = Request.Builder()
            .url(apiLatest)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "SpotUI/${BuildConfig.VERSION_NAME}")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string() ?: return@use null
            val json = JSONObject(body)
            val tag = json.optString("tag_name").trim()
            if (tag.isBlank()) return@use null
            val version = extractVersion(tag)
                ?: return@use null
            val releaseBody = json.optString("body", "")
            val htmlUrl = json.optString("html_url", releasesPage)
                .ifBlank { releasesPage }
            val assets = json.optJSONArray("assets")
            val apkUrl = selectBestApkUrl(assets)
            val isPrerelease = json.optBoolean("prerelease", false)
            UpdateInfo(
                version = version,
                downloadUrl = apkUrl ?: htmlUrl,
                fingerprint = "${json.optLong("id")}:${json.optString("updated_at")}:$version",
                releaseBody = releaseBody,
                isPrerelease = isPrerelease,
            )
        }
    }

    private fun extractVersion(text: String): String? =
        Regex("""\d+(?:\.\d+)+""").find(text)?.value

    internal fun isNewer(remote: String, installed: String): Boolean {
        if (remote == installed) return false
        val rBase = remote.split('-', '+').first()
        val iBase = installed.split('-', '+').first()
        val r = rBase.split('.').map { it.toIntOrNull() ?: 0 }
        val i = iBase.split('.').map { it.toIntOrNull() ?: 0 }
        for (n in 0 until maxOf(r.size, i.size)) {
            val a = r.getOrElse(n) { 0 }
            val b = i.getOrElse(n) { 0 }
            if (a != b) return a > b
        }
        if (remote == iBase && installed.contains("-debug", ignoreCase = true)) {
            return true
        }
        return false
    }
}
