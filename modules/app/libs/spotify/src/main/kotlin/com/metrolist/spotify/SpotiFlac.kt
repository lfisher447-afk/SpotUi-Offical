package com.metrolist.spotify

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.MessageDigest

/**
 * Lossless (FLAC) resolver, ported from the open-source **SpotiFLAC** project.
 *
 * SpotiFLAC is *not* a Spotify ripper — it takes a Spotify track, finds the same
 * recording on a lossless service (Tidal / Qobuz / Amazon Music) by id/ISRC, and
 * returns a directly streamable/downloadable FLAC URL from that service via the
 * project's free "community" proxy servers.
 *
 * Pipeline:
 *  1. Resolve the Spotify track to provider ids via Odesli (song.link) — gives
 *     Tidal + Amazon ids directly. Qobuz is resolved from the track's ISRC via
 *     Qobuz's signed public search API.
 *  2. Ask the community proxy (`/api/dl`) for a FLAC URL, trying providers in
 *     order and skipping any that are on a rotating cooldown (HTTP 503).
 *
 * The proxy base URLs + API key are the same ones SpotiFLAC ships (obfuscated in
 * its binary); they are free community servers and are frequently rate-limited,
 * hence the multi-provider fallback and explicit [Result.Cooldown] state.
 */
object SpotiFlac {

    data class LosslessTrack(
        val url: String,
        val provider: String,   // "tidal" | "qobuz" | "amazon"
        val quality: String,    // "24" (hi-res) | "16" (CD)
        val container: String = "flac",
    )

    sealed interface Result {
        data class Success(val track: LosslessTrack) : Result
        /** Every candidate provider is currently throttled — try again later. */
        data class Cooldown(val message: String) : Result
        /** No lossless match exists for this track on any provider. */
        data object NotFound : Result
        data class Error(val message: String) : Result
    }

    // ── Community proxy config (from SpotiFLAC) ──────────────────────────────
    private const val API_KEY = "explore-obscure-chivalry-travesty-blinks"
    private const val TIDAL_BASE = "https://tdl-foss.spotbye.qzz.io"
    private const val QOBUZ_BASE = "https://qbz-foss.spotbye.qzz.io"
    private const val AMAZON_BASE = "https://amz-foss.spotbye.qzz.io"
    private const val DL_PATH = "/api/dl"
    private const val UA = "SpotiFLAC"

    // ── Qobuz public API (for ISRC -> qobuz track id) ────────────────────────
    private const val QOBUZ_APP_ID = "712109809"
    private const val QOBUZ_APP_SECRET = "589be88e4538daea11f509d29e4a23b1"
    private const val QOBUZ_API_BASE = "https://www.qobuz.com/api.json/0.2"

    private val json = Json { isLenient = true; ignoreUnknownKeys = true; coerceInputValues = true }

    @Volatile
    var logger: ((level: String, message: String) -> Unit)? = null
    private fun log(level: String, msg: String) = logger?.invoke(level, "SpotiFlac: $msg")

    private val client by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                }
            }
            expectSuccess = false
        }
    }

    /**
     * Resolve a lossless FLAC URL for a Spotify track.
     * @param isrc the track's ISRC (used for Qobuz matching); may be null.
     * @param preferHiRes request 24-bit where available, else CD-quality 16-bit.
     */
    suspend fun resolve(
        spotifyTrackId: String,
        isrc: String?,
        preferHiRes: Boolean = true,
    ): Result {
        val quality = if (preferHiRes) "24" else "16"
        val ids = runCatching { resolveProviderIds(spotifyTrackId, isrc) }
            .getOrElse {
                log("E", "id resolution failed: ${it.message}")
                ProviderIds()
            }

        var sawCooldown: String? = null
        var sawMatch = false

        // Order: Tidal & Amazon need only Odesli; Qobuz needs an ISRC match.
        val attempts = listOf(
            Triple("tidal", TIDAL_BASE, ids.tidalId),
            Triple("qobuz", QOBUZ_BASE, ids.qobuzId),
            Triple("amazon", AMAZON_BASE, ids.amazonId),
        )
        for ((provider, base, id) in attempts) {
            if (id.isNullOrBlank()) continue
            sawMatch = true
            when (val r = communityDownload(provider, base, id, quality)) {
                is Result.Success -> return r
                is Result.Cooldown -> sawCooldown = r.message
                is Result.NotFound -> Unit
                is Result.Error -> log("W", "$provider error: ${r.message}")
            }
        }

        return when {
            sawCooldown != null -> Result.Cooldown(sawCooldown)
            !sawMatch -> Result.NotFound
            else -> Result.NotFound
        }
    }

    private data class ProviderIds(
        val tidalId: String? = null,
        val amazonId: String? = null,
        val qobuzId: String? = null,
    )

    private suspend fun resolveProviderIds(spotifyTrackId: String, isrc: String?): ProviderIds {
        var tidalId: String? = null
        var amazonId: String? = null

        // Odesli: spotify track -> all-platform links/ids.
        val odesli = runCatching {
            val resp = client.get("https://api.song.link/v1-alpha.1/links") {
                parameter("url", "spotify:track:$spotifyTrackId")
                header("User-Agent", "Mozilla/5.0")
            }
            json.parseToJsonElement(resp.bodyAsText()).jsonObject
        }.getOrNull()

        odesli?.get("linksByPlatform")?.jsonObject?.let { platforms ->
            tidalId = entityId(platforms, "tidal", "TIDAL_SONG::")
            amazonId = entityId(platforms, "amazonMusic", "AMAZON_SONG::")
        }

        // Qobuz: resolve via ISRC signed search.
        val qobuzId = isrc?.takeIf { it.isNotBlank() }?.let { qobuzIdForIsrc(it) }

        log("D", "ids tidal=$tidalId amazon=$amazonId qobuz=$qobuzId")
        return ProviderIds(tidalId = tidalId, amazonId = amazonId, qobuzId = qobuzId)
    }

    private fun entityId(platforms: JsonObject, platform: String, prefix: String): String? {
        val unique = platforms[platform]?.jsonObject?.get("entityUniqueId")
            ?.jsonPrimitive?.contentOrNull ?: return null
        return unique.substringAfter("::").takeIf { it.isNotBlank() && it != unique }
    }

    private suspend fun qobuzIdForIsrc(isrc: String): String? = runCatching {
        val params = sortedMapOf("query" to isrc.trim(), "limit" to "1")
        val ts = (System.currentTimeMillis() / 1000).toString()
        val sigPayload = buildString {
            append("tracksearch") // normalized "track/search" with slashes removed
            params.forEach { (k, v) -> append(k); append(v) }
            append(ts)
            append(QOBUZ_APP_SECRET)
        }
        val sig = md5Hex(sigPayload)
        val resp = client.get("$QOBUZ_API_BASE/track/search") {
            parameter("query", isrc.trim())
            parameter("limit", "1")
            parameter("app_id", QOBUZ_APP_ID)
            parameter("request_ts", ts)
            parameter("request_sig", sig)
            header("User-Agent", "Mozilla/5.0")
            header("X-App-Id", QOBUZ_APP_ID)
            header("Accept", "application/json")
        }
        val items = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            .get("tracks")?.jsonObject?.get("items")?.let { it as? kotlinx.serialization.json.JsonArray }
        items?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
    }.getOrElse { log("W", "qobuz isrc search failed: ${it.message}"); null }

    private suspend fun communityDownload(
        provider: String,
        base: String,
        id: String,
        quality: String,
    ): Result {
        val resp: HttpResponse = runCatching {
            client.post("$base$DL_PATH") {
                header("x-api-key", API_KEY)
                header("User-Agent", UA)
                header("Accept", "application/json")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(JsonObject.serializer(), buildJsonObject {
                    put("id", id)
                    put("quality", quality)
                }))
            }
        }.getOrElse { return Result.Error(it.message ?: "network error") }

        val bodyText = runCatching { resp.bodyAsText() }.getOrDefault("")
        if (resp.status.value == 503) {
            val msg = runCatching {
                json.parseToJsonElement(bodyText).jsonObject["detail"]?.jsonPrimitive?.contentOrNull
            }.getOrNull() ?: "Lossless servers are busy. Try again shortly."
            log("W", "$provider on cooldown (503)")
            return Result.Cooldown(msg)
        }
        if (resp.status.value !in 200..299) {
            return Result.Error("$provider HTTP ${resp.status.value}")
        }

        val url = extractStreamUrl(bodyText)
            ?: return Result.NotFound.also { log("W", "$provider: no url in response") }
        val q = if (quality == "24") "24-bit" else "16-bit"
        log("D", "$provider FLAC resolved ($q)")
        return Result.Success(LosslessTrack(url = url, provider = provider, quality = quality))
    }

    /** Pull a streamable URL out of the varied community-response shapes. */
    private fun extractStreamUrl(body: String): String? {
        if (body.isBlank()) return null
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        fun JsonObject.url(key: String) = this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("http") }
        obj.url("url")?.let { return it }
        obj.url("download_url")?.let { return it }
        obj["data"]?.let { it as? JsonObject }?.let { data ->
            data.url("url")?.let { return it }
            data.url("download_url")?.let { return it }
        }
        return null
    }

    private fun md5Hex(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
