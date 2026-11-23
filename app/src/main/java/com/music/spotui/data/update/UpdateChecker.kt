package com.music.spotui.data.update

import android.content.Context
import android.os.Build
import android.util.Log
import com.music.spotui.BuildConfig
import com.music.spotui.data.preferences.getUpdateRepoUrl
import com.music.spotui.data.preferences.isStableUpdatesOnly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
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
        val apkFileName: String = "",
        val apkSizeBytes: Long = -1L,
        val sha256Url: String = "",
        val expectedSha256: String = "",
        val releasePageUrl: String = "",
    )

    private val client = OkHttpClient.Builder()
        .dispatcher(
            okhttp3.Dispatcher().apply {
                maxRequests = 16
                maxRequestsPerHost = 6
            },
        )
        .connectionPool(okhttp3.ConnectionPool(6, 2, TimeUnit.MINUTES))
        .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
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
        val repoPath = extractRepoPath(repoUrl)
        return "https://api.github.com/repos/$repoPath/releases/latest"
    }

    internal fun buildReleasesListApiUrl(repoUrl: String): String {
        val repoPath = extractRepoPath(repoUrl)
        return "https://api.github.com/repos/$repoPath/releases?per_page=10"
    }

    private fun extractRepoPath(repoUrl: String): String {
        val clean = repoUrl.trim().trimEnd('/').removeSuffix(".git")
        return clean
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .trim('/')
    }

    internal data class ApkAssetMeta(
        val name: String,
        val url: String,
        val sizeBytes: Long,
        val sha256Url: String,
        val directDigestSha256: String = "",
    )

    internal fun selectBestApkUrl(assets: JSONArray?): String? =
        selectBestApkAsset(assets)?.url

    internal fun selectBestApkAsset(assets: JSONArray?): ApkAssetMeta? {
        if (assets == null) return null
        val items = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
        val apkItems = items.filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
        if (apkItems.isEmpty()) return null

        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull()?.lowercase().orEmpty()
        val chosen = apkItems.maxByOrNull { obj ->
            val name = obj.optString("name").lowercase()
            var score = 0
            if (primaryAbi.isNotBlank() && name.contains(primaryAbi)) score += 100
            if (name.contains("universal") || name.contains("all")) score += 40
            if (name.contains("release")) score += 30
            if (name.contains("unsigned")) score -= 20
            if (name.contains("debug")) score -= 15
            score
        } ?: apkItems.first()

        val url = chosen.optString("browser_download_url").ifBlank { return null }
        val name = chosen.optString("name")
        val size = chosen.optLong("size", -1L)
        val rawDigest = chosen.optString("digest").trim()
        val directDigestSha256 = rawDigest
            .removePrefix("sha256:")
            .takeIf { Regex("""^[A-Fa-f0-9]{64}$""").matches(it) }
            .orEmpty()
        val shaAsset = items.firstOrNull {
            val n = it.optString("name")
            n.equals("$name.sha256", ignoreCase = true) ||
                n.equals("$name.sha256sum", ignoreCase = true) ||
                n.endsWith(".sha256", ignoreCase = true)
        }?.optString("browser_download_url").orEmpty()
        return ApkAssetMeta(
            name = name,
            url = url,
            sizeBytes = size,
            sha256Url = shaAsset,
            directDigestSha256 = directDigestSha256,
        )
    }

    private fun fetchSha256Digest(url: String): String {
        if (url.isBlank()) return ""
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "SpotUI/${BuildConfig.VERSION_NAME}")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use ""
                val text = resp.body?.string().orEmpty().trim()
                Regex("""([A-Fa-f0-9]{64})""").find(text)?.groupValues?.getOrNull(1).orEmpty()
            }
        }.getOrDefault("")
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
            val pageUrl = link.ifBlank { "$repoUrl/releases/latest" }
            UpdateInfo(
                version = version,
                downloadUrl = pageUrl,
                fingerprint = "$version:$updated",
                releaseBody = cleanBody,
                isPrerelease = isPrerelease,
                releasePageUrl = pageUrl,
            )
        }.getOrNull()
    }

    private fun parseReleaseJson(json: JSONObject, releasesPage: String): UpdateInfo? {
        val tag = json.optString("tag_name").trim()
        if (tag.isBlank()) return null
        val version = extractVersion(tag) ?: return null
        val releaseBody = json.optString("body", "")
        val htmlUrl = json.optString("html_url", releasesPage).ifBlank { releasesPage }
        val assets = json.optJSONArray("assets")
        val apkMeta = selectBestApkAsset(assets)
        val inlineSha = Regex("""SHA-?256[^A-Fa-f0-9\r\n]{0,16}([A-Fa-f0-9]{64})""", RegexOption.IGNORE_CASE)
            .find(releaseBody)?.groupValues?.getOrNull(1).orEmpty()
        val digest = when {
            apkMeta?.directDigestSha256?.isNotBlank() == true -> apkMeta.directDigestSha256
            apkMeta?.sha256Url?.isNotBlank() == true -> fetchSha256Digest(apkMeta.sha256Url).ifBlank { inlineSha }
            else -> inlineSha
        }
        val isPrerelease = json.optBoolean("prerelease", false)
        return UpdateInfo(
            version = version,
            downloadUrl = apkMeta?.url ?: htmlUrl,
            fingerprint = "${json.optLong("id")}:${json.optString("updated_at")}:$version",
            releaseBody = releaseBody,
            isPrerelease = isPrerelease,
            apkFileName = apkMeta?.name.orEmpty(),
            apkSizeBytes = apkMeta?.sizeBytes ?: -1L,
            sha256Url = apkMeta?.sha256Url.orEmpty(),
            expectedSha256 = digest,
            releasePageUrl = htmlUrl,
        )
    }

    private fun fetchLatestRelease(context: Context): UpdateInfo? {
        val repoUrl = getUpdateRepoUrl(context).trim().trimEnd('/').removeSuffix(".git")
        val stableOnly = isStableUpdatesOnly(context)
        val apiLatest = buildLatestReleaseApiUrl(repoUrl)
        val releasesPage = "$repoUrl/releases/latest"

        if (stableOnly) {
            val request = Request.Builder()
                .url(apiLatest)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "SpotUI/${BuildConfig.VERSION_NAME}")
                .build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use null
                        parseReleaseJson(JSONObject(body), releasesPage)
                    } else {
                        null
                    }
                }
            }.getOrNull()?.let { return it }
        }

        // Fallback / prerelease-enabled / rate-limit fallback: list recent releases JSON
        val listUrl = buildReleasesListApiUrl(repoUrl)
        val listReq = Request.Builder()
            .url(listUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "SpotUI/${BuildConfig.VERSION_NAME}")
            .build()
        runCatching {
            client.newCall(listReq).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val array = JSONArray(body)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    if (obj.optBoolean("draft", false)) continue
                    if (stableOnly && obj.optBoolean("prerelease", false)) continue
                    val parsed = parseReleaseJson(obj, releasesPage)
                    if (parsed != null) return@use parsed
                }
                null
            }
        }.getOrNull()?.let { return it }

        // Final fallback: GitHub Atom feed (works even when REST API is unauthenticated rate-limited)
        val atomUrl = "$repoUrl/releases.atom"
        val atomReq = Request.Builder()
            .url(atomUrl)
            .header("Accept", "application/atom+xml, application/xml")
            .header("User-Agent", "SpotUI/${BuildConfig.VERSION_NAME}")
            .build()
        return runCatching {
            client.newCall(atomReq).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val xml = response.body?.string() ?: return@use null
                val parsed = parseReleasesAtom(xml, repoUrl) ?: return@use null
                if (stableOnly && parsed.isPrerelease) null else parsed
            }
        }.getOrNull()
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
