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
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
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
    private const val DEEZER_BASE = "https://dz-foss.spotbye.qzz.io"
    private const val DL_PATH = "/api/dl"
    private const val UA = "SpotiFLAC"

    // ── Monochrome / squid.wtf TIDAL backends (public, no login required) ────
    // These proxy TIDAL and return a lossless FLAC URL for a TIDAL track id —
    // no user account or subscription needed. Instances are frequently up/down,
    // so we fail over across the list. (github.com/monochrome-music/monochrome)
    // Live JSON list of currently-up Hi-Fi API instances (fetched at runtime).
    private const val HIFI_UPTIME_URL = "https://tidal-uptime.geeked.wtf"

    // Static fallback Hi-Fi API instances (from monochrome INSTANCES.md + community).
    // The live uptime list is merged in front of this so the pool stays fresh/large.
    private val TIDAL_MONOCHROME_INSTANCES = listOf(
        "https://api.monochrome.tf",
        "https://monochrome-api.samidy.com",
        "https://hifi.geeked.wtf",
        "https://wolf.qqdl.site",
        "https://maus.qqdl.site",
        "https://vogel.qqdl.site",
        "https://katze.qqdl.site",
        "https://hund.qqdl.site",
        "https://tidal.kinoplus.online",
        "https://eu-central.monochrome.tf",
        "https://us-west.monochrome.tf",
        "https://arran.monochrome.tf",
        "https://triton.squid.wtf",
    )

    // Cache the live instance list for a few minutes so we don't refetch per track.
    @Volatile private var cachedInstances: List<String>? = null
    @Volatile private var cachedInstancesAt: Long = 0L

    /**
     * Current Hi-Fi API instances: the live "up" list from the uptime tracker merged
     * in front of the static fallback. Cached for 5 minutes. This is what lets the
     * resolver reach as many working providers as possible without a rebuild.
     */
    private suspend fun monochromeInstances(): List<String> {
        cachedInstances?.let {
            if (System.currentTimeMillis() - cachedInstancesAt < 5 * 60_000L) return it
        }
        val live = runCatching {
            val resp = client.get(HIFI_UPTIME_URL) { header("User-Agent", UA) }
            if (resp.status.value !in 200..299) return@runCatching emptyList<String>()
            val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            val arr = (root["api"] as? JsonArray) ?: (root["apis"] as? JsonArray)
                ?: (root["instances"] as? JsonArray) ?: JsonArray(emptyList())
            arr.mapNotNull { el ->
                when (el) {
                    is JsonObject -> {
                        val up = el["up"]?.jsonPrimitive?.booleanOrNull
                            ?: el["online"]?.jsonPrimitive?.booleanOrNull ?: true
                        if (up) el["url"]?.jsonPrimitive?.contentOrNull else null
                    }
                    else -> el.jsonPrimitive.contentOrNull
                }
            }.mapNotNull { it.takeIf { u -> u.startsWith("http") }?.trimEnd('/') }
        }.getOrNull().orEmpty()
        return (live + TIDAL_MONOCHROME_INSTANCES).distinct().also {
            cachedInstances = it
            cachedInstancesAt = System.currentTimeMillis()
        }
    }

    // ── Qobuz public API (for ISRC -> qobuz track id) ────────────────────────
    private const val QOBUZ_APP_ID = "712109809"
    private const val QOBUZ_APP_SECRET = "589be88e4538daea11f509d29e4a23b1"
    private const val QOBUZ_API_BASE = "https://www.qobuz.com/api.json/0.2"

    private val json = Json { isLenient = true; ignoreUnknownKeys = true; coerceInputValues = true }

    @Volatile
    var logger: ((level: String, message: String) -> Unit)? = null
    private fun log(level: String, msg: String) = logger?.invoke(level, "SpotiFlac: $msg")

    @Volatile
    var deezerArl: String? = null
        set(value) {
            field = value?.trim()?.takeIf { it.isNotEmpty() }
        }

    @Volatile
    var customMirrorBaseUrl: String? = null
        set(value) {
            val cleaned = value?.trim()?.trimEnd('/')
            field = cleaned?.takeIf { it.startsWith("https://", ignoreCase = true) }
        }

    data class DeezerAccountStatus(
        val valid: Boolean,
        val userName: String = "",
        val losslessCapable: Boolean = false,
        val detail: String = "",
    )

    private val client by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    dispatcher(
                        okhttp3.Dispatcher().apply {
                            maxRequests = 48
                            maxRequestsPerHost = 12
                        },
                    )
                    connectionPool(okhttp3.ConnectionPool(16, 3, java.util.concurrent.TimeUnit.MINUTES))
                    protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
                    retryOnConnectionFailure(true)
                    connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                    readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    writeTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                }
            }
            expectSuccess = false
        }
    }

    // ── Live server status (spotbye.qzz.io/api/status → "spotiflac" section) ──
    private const val STATUS_URL = "https://spotbye.qzz.io/api/status"
    private val allProviders = setOf("tidal", "qobuz", "amazon", "deezer")
    private val providerCooldowns = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile private var upProvidersCache: Set<String>? = null
    @Volatile private var upProvidersAt = 0L

    /**
     * Which SpotiFLAC (not "next") providers are currently up, per the status API.
     * Cached 60s. Fail-open: if the status endpoint itself is unreachable we return
     * all providers so lossless still gets a chance rather than being wrongly blocked.
     */
    suspend fun upLosslessProviders(): Set<String> {
        upProvidersCache?.let { if (System.currentTimeMillis() - upProvidersAt < 60_000L) return it }
        val up = runCatching {
            val r = client.get(STATUS_URL) { header("User-Agent", UA) }
            if (r.status.value !in 200..299) return@runCatching null
            val obj = json.parseToJsonElement(r.bodyAsText()).jsonObject

            val spotiflacUp = obj["spotiflac"]
                ?.jsonObject?.get("status")?.jsonObject
                ?.filterValues { it.jsonPrimitive.contentOrNull.equals("up", true) }
                ?.keys?.toSet().orEmpty()

            val nextUp = obj["next"]
                ?.jsonObject?.get("status")?.jsonObject
                ?.filterValues { it.jsonPrimitive.contentOrNull.equals("up", true) }
                ?.keys?.map { key -> key.substringBefore("_") }?.toSet().orEmpty()

            val combined = spotiflacUp + nextUp
            if (combined.isEmpty()) null else combined
        }.getOrNull()

        val result = up ?: allProviders // unreachable or empty status API → fail open to allProviders
        upProvidersCache = result
        upProvidersAt = System.currentTimeMillis()
        return result
    }

    data class ProviderStatus(
        val id: String,
        val name: String,
        val isUp: Boolean,
        val isCooldown: Boolean,
        val cooldownRemainingSec: Int = 0,
        val detail: String = "",
    )

    fun clearStatusCache() {
        upProvidersCache = null
        upProvidersAt = 0L
    }

    fun clearCooldowns() {
        providerCooldowns.clear()
        clearStatusCache()
    }

    /**
     * Validates a user-provided Deezer ARL cookie against Deezer's session endpoint.
     * Never logs or leaks the ARL value.
     */
    suspend fun validateDeezerArl(arl: String): DeezerAccountStatus {
        val cleanArl = arl.trim()
        if (cleanArl.isEmpty()) {
            return DeezerAccountStatus(valid = false, detail = "Enter a Deezer ARL cookie.")
        }
        return runCatching {
            val resp = client.get("https://www.deezer.com/ajax/gw-light.php") {
                parameter("method", "deezer.getUserData")
                parameter("input", "3")
                parameter("api_version", "1.0")
                parameter("api_token", "")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36")
                header("Cookie", "arl=$cleanArl")
                header("Accept", "application/json")
            }
            if (resp.status.value !in 200..299) {
                return@runCatching DeezerAccountStatus(
                    valid = false,
                    detail = "Deezer responded HTTP ${resp.status.value}",
                )
            }
            val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            val results = root["results"] as? JsonObject
            val user = results?.get("USER") as? JsonObject
            val userId = user?.get("USER_ID")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
            if (userId <= 0L) {
                return@runCatching DeezerAccountStatus(
                    valid = false,
                    detail = "ARL cookie is invalid or expired. Sign in again to refresh it.",
                )
            }
            val blogName = user?.get("BLOG_NAME")?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "Deezer User #$userId" }
            val options = user?.get("OPTIONS") as? JsonObject
            val lossless = options?.get("web_lossless")?.jsonPrimitive?.booleanOrNull
                ?: options?.get("mobile_lossless")?.jsonPrimitive?.booleanOrNull
                ?: false
            DeezerAccountStatus(
                valid = true,
                userName = blogName,
                losslessCapable = lossless,
                detail = if (lossless) "Signed in as $blogName (HiFi FLAC eligible)" else "Signed in as $blogName (Standard tier)",
            )
        }.getOrElse { err ->
            DeezerAccountStatus(valid = false, detail = "Deezer check failed: ${err.message ?: "network error"}")
        }
    }

    /**
     * Live status of each individual provider (Tidal, Qobuz, Amazon, Deezer, Monochrome)
     * including up/down status and active 503 cooldowns.
     */
    suspend fun getProviderStatuses(): List<ProviderStatus> {
        val up = upLosslessProviders()
        val now = System.currentTimeMillis()
        val arlReady = !deezerArl.isNullOrBlank()
        val mirrorActive = !customMirrorBaseUrl.isNullOrBlank()

        val providers = listOf(
            Triple("tidal", "Tidal (Community)", "High-res & CD quality FLAC via spotbye mirror"),
            Triple("qobuz", "Qobuz (Community)", "24-bit Hi-Res & CD quality FLAC via ISRC"),
            Triple("amazon", "Amazon Music (Community)", "24-bit Ultra HD FLAC"),
            Triple(
                "deezer",
                "Deezer (Community${if (arlReady) " + ARL" else ""})",
                if (arlReady) {
                    "16-bit CD FLAC via ISRC · Session ARL linked"
                } else {
                    "16-bit CD FLAC via ISRC · Link Deezer ARL in Settings if proxy requires login"
                },
            ),
        )

        val list = mutableListOf<ProviderStatus>()

        for ((id, name, desc) in providers) {
            val isUp = id in up || mirrorActive
            val cooldownUntil = providerCooldowns[id] ?: 0L
            val isCooldown = cooldownUntil > now
            val cdSec = if (isCooldown) ((cooldownUntil - now) / 1000).toInt() else 0
            list.add(
                ProviderStatus(
                    id = id,
                    name = name,
                    isUp = isUp,
                    isCooldown = isCooldown,
                    cooldownRemainingSec = cdSec,
                    detail = desc,
                )
            )
        }

        val monoInstances = runCatching { monochromeInstances() }.getOrDefault(emptyList())
        list.add(
            ProviderStatus(
                id = "monochrome",
                name = "Tidal (Monochrome / Squid.wtf)",
                isUp = monoInstances.isNotEmpty(),
                isCooldown = false,
                detail = "${monoInstances.size} live no-login public mirrors",
            )
        )

        return list
    }

    data class SpotFlacStageCheck(
        val id: String,
        val label: String,
        val passed: Boolean,
        val latencyMs: Long,
        val detail: String,
    )

    data class SpotFlacProviderProbe(
        val providerId: String,
        val providerName: String,
        val resolvedId: String?,
        val streamFound: Boolean,
        val container: String,
        val qualityBitDepth: String,
        val headerSignature: String,
        val contentLengthBytes: Long?,
        val httpStatus: Int?,
        val latencyMs: Long,
        val detail: String,
    )

    data class SpotFlacVerificationReport(
        val spotifyTrackId: String,
        val isrcUsed: String?,
        val totalDurationMs: Long,
        val stages: List<SpotFlacStageCheck>,
        val providerProbes: List<SpotFlacProviderProbe>,
        val verifiedStreamsCount: Int,
        val summary: String,
    )

    private const val CANONICAL_VERIFY_TRACK_ID = "0VjIjW4GlUZAMYd2vX5bxg" // Blinding Lights (well-indexed across mirrors)
    private const val CANONICAL_VERIFY_ISRC = "USUG11904206"

    /** Extract a 22-char Spotify track ID from a track URL, URI, or bare ID. */
    fun extractSpotifyTrackId(rawInput: String?): String? {
        val raw = rawInput?.trim().orEmpty()
        if (raw.isBlank()) return null
        Regex("(?:spotify:track:|open\\.spotify\\.com/(?:intl-[a-z-]+/)?track/)([A-Za-z0-9]{22})")
            .find(raw)?.groupValues?.getOrNull(1)?.let { return it }
        Regex("^[A-Za-z0-9]{22}$").find(raw)?.value?.let { return it }
        return null
    }

    /** Sniff binary container signature from an initial audio byte slice (fLaC, MP4 ftyp, ID3, or <MPD>). */
    fun inspectBinaryMagicSignature(bytes: ByteArray): String {
        if (bytes.isEmpty()) return "Empty response"
        if (bytes.size >= 4 &&
            bytes[0] == 0x66.toByte() && bytes[1] == 0x4C.toByte() &&
            bytes[2] == 0x61.toByte() && bytes[3] == 0x43.toByte()
        ) {
            return "FLAC verified (fLaC 0x664C6143)"
        }
        if (bytes.size >= 8 &&
            bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() &&
            bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte()
        ) {
            return "ISO-BMFF Hi-Res container (ftyp)"
        }
        val textPrefix = bytes.decodeToString(0, minOf(bytes.size, 64))
        if (textPrefix.contains("<MPD", ignoreCase = true) || textPrefix.contains("<?xml", ignoreCase = true)) {
            return "DASH FLAC manifest (<MPD>)"
        }
        if (bytes.size >= 3 &&
            bytes[0] == 0x49.toByte() && bytes[1] == 0x44.toByte() && bytes[2] == 0x33.toByte()
        ) {
            return "ID3v2 stream (non-FLAC)"
        }
        return "Stream 200/206 (${bytes.take(4).joinToString("") { "%02X".format(it) }})"
    }

    /**
     * Runs a comprehensive multi-stage verification across the entire SpotiFLAC stack:
     * 1. SpotBye community status API (`/api/status`)
     * 2. Monochrome / Squid.wtf live Hi-Fi uptime tracker (`tidal-uptime.geeked.wtf`)
     * 3. Odesli (`api.song.link`) cross-platform ID resolver
     * 4. Qobuz signed ISRC search API (`md5Hex` signature)
     * 5. Deezer public ISRC + optional ARL account check
     * 6. End-to-end sample stream resolution + HTTP Range `bytes=0-63` binary header sniff per provider
     */
    suspend fun verifyLosslessPipeline(
        trackUrlOrId: String? = null,
        isrc: String? = null,
        enabledProviders: Set<String> = allProviders,
        preferHiRes: Boolean = true,
    ): SpotFlacVerificationReport = coroutineScope {
        val suiteStart = System.currentTimeMillis()
        val trackId = extractSpotifyTrackId(trackUrlOrId) ?: CANONICAL_VERIFY_TRACK_ID
        val effectiveIsrc = isrc?.trim()?.takeIf { it.isNotBlank() }
            ?: if (trackId == CANONICAL_VERIFY_TRACK_ID) CANONICAL_VERIFY_ISRC else null
        val quality = if (preferHiRes) "24" else "16"
        val allowed = enabledProviders.map { it.lowercase() }.toSet().ifEmpty { allProviders }

        // Clear caches so verification reflects live network truth
        clearStatusCache()

        val statusStageDeferred = async {
            val t0 = System.currentTimeMillis()
            val up = runCatching { upLosslessProviders() }.getOrDefault(emptySet())
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            SpotFlacStageCheck(
                id = "spotbye_status",
                label = "SpotBye Proxy Status API",
                passed = up.isNotEmpty(),
                latencyMs = dt,
                detail = if (up.isNotEmpty()) "${up.size}/4 proxies reported online (${up.sorted().joinToString(", ")})" else "Unreachable",
            )
        }

        val monochromeStageDeferred = async {
            val t0 = System.currentTimeMillis()
            cachedInstancesAt = 0L
            val instances = runCatching { monochromeInstances() }.getOrDefault(emptyList())
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            SpotFlacStageCheck(
                id = "monochrome_uptime",
                label = "Monochrome / Squid.wtf Uptime",
                passed = instances.isNotEmpty(),
                latencyMs = dt,
                detail = "${instances.size} no-login TIDAL Hi-Fi mirrors available",
            )
        }

        val idsStart = System.currentTimeMillis()
        val ids = runCatching { resolveProviderIds(trackId, effectiveIsrc) }.getOrDefault(ProviderIds())
        val idsDt = (System.currentTimeMillis() - idsStart).coerceAtLeast(1L)

        val statusStage = statusStageDeferred.await()
        val monochromeStage = monochromeStageDeferred.await()

        val odesliPassed = !ids.tidalId.isNullOrBlank() || !ids.amazonId.isNullOrBlank() || !ids.deezerId.isNullOrBlank()
        val odesliStage = SpotFlacStageCheck(
            id = "odesli_bridge",
            label = "Odesli (song.link) ID Bridge",
            passed = odesliPassed,
            latencyMs = idsDt,
            detail = "Tidal=${ids.tidalId ?: "—"} · Amazon=${ids.amazonId ?: "—"} · Deezer=${ids.deezerId ?: "—"}",
        )

        val qobuzStage = SpotFlacStageCheck(
            id = "qobuz_isrc",
            label = "Qobuz Signed ISRC Search",
            passed = !ids.qobuzId.isNullOrBlank() || effectiveIsrc == null,
            latencyMs = idsDt,
            detail = when {
                !ids.qobuzId.isNullOrBlank() -> "ISRC $effectiveIsrc → Qobuz track #${ids.qobuzId}"
                effectiveIsrc != null -> "No signed match for ISRC $effectiveIsrc"
                else -> "Skipped (no ISRC supplied for track)"
            },
        )

        val arlSnapshot = deezerArl
        val deezerArlStage = if (!arlSnapshot.isNullOrBlank()) {
            val t0 = System.currentTimeMillis()
            val arlStatus = validateDeezerArl(arlSnapshot)
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            SpotFlacStageCheck(
                id = "deezer_arl",
                label = "Deezer Direct HiFi Session",
                passed = arlStatus.valid,
                latencyMs = dt,
                detail = arlStatus.detail,
            )
        } else {
            SpotFlacStageCheck(
                id = "deezer_arl",
                label = "Deezer Direct HiFi Session",
                passed = !ids.deezerId.isNullOrBlank(),
                latencyMs = 0L,
                detail = if (!ids.deezerId.isNullOrBlank()) "Public proxy mode (ISRC #${ids.deezerId}); link ARL for direct CDN" else "No ARL configured",
            )
        }

        val customBase = customMirrorBaseUrl
        val probeJobs = buildList {
            if ("tidal" in allowed) {
                add(async {
                    probeSingleProvider(
                        providerId = "tidal_mono",
                        providerName = "Tidal (Monochrome Direct)",
                        resolvedId = ids.tidalId,
                        quality = quality,
                    ) { id -> resolveTidalMonochrome(id, preferHiRes, rejectPreviewLength = false) }
                })
                add(async {
                    probeSingleProvider(
                        providerId = "tidal",
                        providerName = "Tidal (SpotBye / Custom)",
                        resolvedId = ids.tidalId,
                        quality = quality,
                    ) { id -> communityDownload("tidal", customBase ?: TIDAL_BASE, id, quality, rejectPreviewLength = false) }
                })
            }
            if ("qobuz" in allowed) {
                add(async {
                    probeSingleProvider(
                        providerId = "qobuz",
                        providerName = "Qobuz (${quality}-bit Hi-Res)",
                        resolvedId = ids.qobuzId,
                        quality = quality,
                    ) { id -> communityDownload("qobuz", customBase ?: QOBUZ_BASE, id, quality, rejectPreviewLength = false) }
                })
            }
            if ("amazon" in allowed) {
                add(async {
                    probeSingleProvider(
                        providerId = "amazon",
                        providerName = "Amazon Music UHD",
                        resolvedId = ids.amazonId,
                        quality = quality,
                    ) { id -> communityDownload("amazon", customBase ?: AMAZON_BASE, id, quality, rejectPreviewLength = false) }
                })
            }
            if ("deezer" in allowed) {
                add(async {
                    probeSingleProvider(
                        providerId = "deezer",
                        providerName = if (arlSnapshot.isNullOrBlank()) "Deezer (Community)" else "Deezer (+ARL)",
                        resolvedId = ids.deezerId,
                        quality = "16",
                    ) { id -> communityDownload("deezer", customBase ?: DEEZER_BASE, id, "16", rejectPreviewLength = false, arl = arlSnapshot) }
                })
            }
        }

        val probes = probeJobs.map { it.await() }
        val verifiedCount = probes.count { it.streamFound }
        val totalMs = (System.currentTimeMillis() - suiteStart).coerceAtLeast(1L)
        val stages = listOf(statusStage, monochromeStage, odesliStage, qobuzStage, deezerArlStage)

        val summary = buildString {
            append("$verifiedCount/${probes.size} providers verified live")
            append(" · ${stages.count { it.passed }}/${stages.size} pipeline checks OK")
            append(" (${totalMs}ms)")
        }

        SpotFlacVerificationReport(
            spotifyTrackId = trackId,
            isrcUsed = effectiveIsrc,
            totalDurationMs = totalMs,
            stages = stages,
            providerProbes = probes,
            verifiedStreamsCount = verifiedCount,
            summary = summary,
        )
    }

    private suspend fun probeSingleProvider(
        providerId: String,
        providerName: String,
        resolvedId: String?,
        quality: String,
        resolver: suspend (String) -> Result,
    ): SpotFlacProviderProbe {
        if (resolvedId.isNullOrBlank()) {
            return SpotFlacProviderProbe(
                providerId = providerId,
                providerName = providerName,
                resolvedId = null,
                streamFound = false,
                container = "—",
                qualityBitDepth = "$quality-bit",
                headerSignature = "No provider ID",
                contentLengthBytes = null,
                httpStatus = null,
                latencyMs = 0L,
                detail = "Track ID bridge did not return an ID for this service",
            )
        }
        val t0 = System.currentTimeMillis()
        val result = withTimeoutOrNull(6500L) { resolver(resolvedId) } ?: Result.Error("Timed out (6.5s)")
        val resolveMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L)

        return when (result) {
            is Result.Success -> {
                val track = result.track
                if (track.url.startsWith("data:application/dash+xml")) {
                    val decoded = dashMpdFromUrl(track.url).orEmpty()
                    val dur = mpdDurationSeconds(decoded)
                    val durLabel = dur?.let { "${it.toInt()}s DASH" } ?: "Segmented DASH"
                    SpotFlacProviderProbe(
                        providerId = providerId,
                        providerName = providerName,
                        resolvedId = resolvedId,
                        streamFound = true,
                        container = "DASH-FLAC",
                        qualityBitDepth = "${track.quality}-bit",
                        headerSignature = if (decoded.contains("<MPD")) "DASH FLAC manifest (<MPD> · $durLabel)" else "DASH manifest",
                        contentLengthBytes = null,
                        httpStatus = 200,
                        latencyMs = resolveMs,
                        detail = "Verified $durLabel stream for ID $resolvedId",
                    )
                } else {
                    val headerCheck = verifyStreamByteHeader(track.url)
                    SpotFlacProviderProbe(
                        providerId = providerId,
                        providerName = providerName,
                        resolvedId = resolvedId,
                        streamFound = headerCheck.httpStatus in 200..299,
                        container = track.container.uppercase(),
                        qualityBitDepth = "${track.quality}-bit",
                        headerSignature = headerCheck.signature,
                        contentLengthBytes = headerCheck.totalBytes,
                        httpStatus = headerCheck.httpStatus,
                        latencyMs = resolveMs + headerCheck.probeMs,
                        detail = headerCheck.detail,
                    )
                }
            }
            is Result.Cooldown -> SpotFlacProviderProbe(
                providerId = providerId,
                providerName = providerName,
                resolvedId = resolvedId,
                streamFound = false,
                container = "—",
                qualityBitDepth = "$quality-bit",
                headerSignature = "Cooldown (HTTP 503)",
                contentLengthBytes = null,
                httpStatus = 503,
                latencyMs = resolveMs,
                detail = result.message,
            )
            Result.NotFound -> SpotFlacProviderProbe(
                providerId = providerId,
                providerName = providerName,
                resolvedId = resolvedId,
                streamFound = false,
                container = "—",
                qualityBitDepth = "$quality-bit",
                headerSignature = "Not Found",
                contentLengthBytes = null,
                httpStatus = 404,
                latencyMs = resolveMs,
                detail = "Mirror returned no lossless stream for ID $resolvedId",
            )
            is Result.Error -> SpotFlacProviderProbe(
                providerId = providerId,
                providerName = providerName,
                resolvedId = resolvedId,
                streamFound = false,
                container = "—",
                qualityBitDepth = "$quality-bit",
                headerSignature = "Error",
                contentLengthBytes = null,
                httpStatus = null,
                latencyMs = resolveMs,
                detail = result.message,
            )
        }
    }

    private data class ByteHeaderProbe(
        val httpStatus: Int,
        val signature: String,
        val totalBytes: Long?,
        val probeMs: Long,
        val detail: String,
    )

    private suspend fun verifyStreamByteHeader(url: String): ByteHeaderProbe {
        val t0 = System.currentTimeMillis()
        return runCatching {
            val resp = client.get(url) {
                header("User-Agent", UA)
                header("Range", "bytes=0-63")
            }
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val status = resp.status.value
            val bytes = runCatching { resp.readRawBytes() }.getOrDefault(ByteArray(0))
            val contentRange = resp.headers["Content-Range"]
            val totalFromRange = contentRange?.substringAfter('/')?.toLongOrNull()
            val contentLen = totalFromRange ?: resp.headers["Content-Length"]?.toLongOrNull()
            val sig = inspectBinaryMagicSignature(bytes)
            val sizeStr = contentLen?.let {
                String.format(java.util.Locale.US, "%.2f MiB", it / (1024.0 * 1024.0))
            } ?: "streamed"
            ByteHeaderProbe(
                httpStatus = status,
                signature = sig,
                totalBytes = contentLen,
                probeMs = dt,
                detail = "HTTP $status · $sizeStr · $sig",
            )
        }.getOrElse { err ->
            val dt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            ByteHeaderProbe(
                httpStatus = 0,
                signature = "Network error",
                totalBytes = null,
                probeMs = dt,
                detail = err.message ?: "Header probe failed",
            )
        }
    }

    /** True if any lossless resolver path (community or public Monochrome mirrors) can be queried. */
    suspend fun anyLosslessServerUp(): Boolean =
        upLosslessProviders().isNotEmpty() || TIDAL_MONOCHROME_INSTANCES.isNotEmpty()

    /**
     * Resolve a lossless FLAC URL for a Spotify track.
     * @param isrc the track's ISRC (used for Qobuz & Deezer matching); may be null.
     * @param preferHiRes request 24-bit where available, else CD-quality 16-bit.
     * @param enabledProviders subset of enabled provider IDs ("tidal", "qobuz", "amazon", "deezer").
     * @param soloProvider if true, only queries the highest-priority enabled provider.
     */
    suspend fun resolve(
        spotifyTrackId: String,
        isrc: String?,
        preferHiRes: Boolean = true,
        providerOrder: List<String> = listOf("tidal", "qobuz", "amazon", "deezer"),
        rejectPreviewLength: Boolean = true,
        enabledProviders: Set<String> = allProviders,
        soloProvider: Boolean = false,
    ): Result = coroutineScope {
        val upProvidersDeferred = async { upLosslessProviders() }
        val idsDeferred = async { runCatching { resolveProviderIds(spotifyTrackId, isrc) }.getOrDefault(ProviderIds()) }

        val upProviders = upProvidersDeferred.await()
        val quality = if (preferHiRes) "24" else "16"
        val ids = idsDeferred.await()

        val allowed = enabledProviders.map { it.lowercase() }.toSet().ifEmpty { allProviders }
        val orderedActive = (providerOrder.map { it.lowercase() } + allProviders)
            .distinct()
            .filter { it in allowed }
            .let { if (soloProvider) it.take(1) else it }

        val candidates = mutableListOf<suspend () -> Result>()
        val customBase = customMirrorBaseUrl

        orderedActive.forEach { provider ->
            val onCooldown = (providerCooldowns[provider] ?: 0L) > System.currentTimeMillis()
            if (onCooldown) return@forEach
            when (provider) {
                "tidal" -> if (!ids.tidalId.isNullOrBlank()) {
                    // Monochrome / Squid.wtf direct mirrors work independently without login or spotbye status.
                    candidates.add { resolveTidalMonochrome(ids.tidalId, preferHiRes, rejectPreviewLength) }
                    if (customBase != null) {
                        candidates.add { communityDownload("tidal", customBase, ids.tidalId, quality, rejectPreviewLength) }
                    }
                    if ("tidal" in upProviders) {
                        candidates.add { communityDownload("tidal", TIDAL_BASE, ids.tidalId, quality, rejectPreviewLength) }
                    }
                }
                "qobuz" -> if (!ids.qobuzId.isNullOrBlank()) {
                    if (customBase != null) {
                        candidates.add { communityDownload("qobuz", customBase, ids.qobuzId, quality, rejectPreviewLength) }
                    }
                    if ("qobuz" in upProviders) {
                        candidates.add { communityDownload("qobuz", QOBUZ_BASE, ids.qobuzId, quality, rejectPreviewLength) }
                    }
                }
                "amazon" -> if (!ids.amazonId.isNullOrBlank()) {
                    if (customBase != null) {
                        candidates.add { communityDownload("amazon", customBase, ids.amazonId, quality, rejectPreviewLength) }
                    }
                    if ("amazon" in upProviders) {
                        candidates.add { communityDownload("amazon", AMAZON_BASE, ids.amazonId, quality, rejectPreviewLength) }
                    }
                }
                "deezer" -> if (!ids.deezerId.isNullOrBlank()) {
                    if (customBase != null) {
                        candidates.add { communityDownload("deezer", customBase, ids.deezerId, quality, rejectPreviewLength, arl = deezerArl) }
                    }
                    if ("deezer" in upProviders) {
                        candidates.add { communityDownload("deezer", DEEZER_BASE, ids.deezerId, quality, rejectPreviewLength, arl = deezerArl) }
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            return@coroutineScope Result.NotFound
        }

        val channel = Channel<Result>(candidates.size)
        val jobs = candidates.map { task ->
            launch {
                val r = withTimeoutOrNull(4500L) { task() } ?: Result.NotFound
                channel.send(r)
            }
        }

        var winner: Result.Success? = null
        var lastCooldown: Result.Cooldown? = null
        var receivedCount = 0

        for (r in channel) {
            receivedCount++
            when (r) {
                is Result.Success -> {
                    winner = r
                    jobs.forEach { it.cancel() }
                    break
                }
                is Result.Cooldown -> {
                    lastCooldown = r
                }
                else -> Unit
            }
            if (receivedCount >= candidates.size) break
        }

        winner ?: lastCooldown ?: Result.NotFound
    }

    private data class ProviderIds(
        val tidalId: String? = null,
        val amazonId: String? = null,
        val qobuzId: String? = null,
        val deezerId: String? = null,
    )

    private suspend fun resolveProviderIds(spotifyTrackId: String, isrc: String?): ProviderIds = coroutineScope {
        val odesliDeferred = async {
            runCatching {
                val resp = client.get("https://api.song.link/v1-alpha.1/links") {
                    parameter("url", "spotify:track:$spotifyTrackId")
                    header("User-Agent", "Mozilla/5.0")
                }
                if (resp.status.value in 200..299) {
                    json.parseToJsonElement(resp.bodyAsText()).jsonObject
                } else null
            }.getOrNull()
        }

        val qobuzDeferred = async {
            isrc?.takeIf { it.isNotBlank() }?.let { qobuzIdForIsrc(it) }
        }

        val deezerIsrcDeferred = async {
            isrc?.takeIf { it.isNotBlank() }?.let { deezerIdForIsrc(it) }
        }

        val odesli = odesliDeferred.await()
        val qobuzId = qobuzDeferred.await()
        var deezerId: String? = null
        var tidalId: String? = null
        var amazonId: String? = null

        odesli?.get("linksByPlatform")?.jsonObject?.let { platforms ->
            tidalId = entityId(platforms, "tidal", "TIDAL_SONG::")
            amazonId = entityId(platforms, "amazonMusic", "AMAZON_SONG::")
            deezerId = entityId(platforms, "deezer", "DEEZER_SONG::")
        }

        if (deezerId == null) {
            deezerId = deezerIsrcDeferred.await()
        }

        log("D", "ids tidal=$tidalId amazon=$amazonId qobuz=$qobuzId deezer=$deezerId")
        ProviderIds(tidalId = tidalId, amazonId = amazonId, qobuzId = qobuzId, deezerId = deezerId)
    }

    private suspend fun deezerIdForIsrc(isrc: String): String? = runCatching {
        val resp = client.get("https://api.deezer.com/2.0/track/isrc:${isrc.trim()}") {
            header("User-Agent", "Mozilla/5.0")
            header("Accept", "application/json")
        }
        if (resp.status.value !in 200..299) return@runCatching null
        val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        root["id"]?.jsonPrimitive?.contentOrNull
    }.getOrElse { log("W", "deezer isrc search failed: ${it.message}"); null }

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
        rejectPreviewLength: Boolean,
        arl: String? = null,
    ): Result {
        // Retry transient errors (429/502/504) with a short backoff — the proxies
        // are flaky-but-alive far more often than genuinely down. 503 is a real
        // scheduled cooldown, so we surface it instead of hammering.
        var resp: HttpResponse? = null
        val maxAttempts = 3
        for (attempt in 1..maxAttempts) {
            val r = runCatching {
                client.post("$base$DL_PATH") {
                    header("x-api-key", API_KEY)
                    header("User-Agent", UA)
                    header("Accept", "application/json")
                    if (!arl.isNullOrBlank()) {
                        header("X-Deezer-ARL", arl)
                    }
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(JsonObject.serializer(), buildJsonObject {
                        put("id", id)
                        put("quality", quality)
                        if (!arl.isNullOrBlank()) {
                            put("arl", arl)
                        }
                    }))
                }
            }.getOrElse { return Result.Error(it.message ?: "network error") }

            if (r.status.value == 503) {
                val msg = runCatching {
                    json.parseToJsonElement(runCatching { r.bodyAsText() }.getOrDefault(""))
                        .jsonObject["detail"]?.jsonPrimitive?.contentOrNull
                }.getOrNull() ?: "Lossless servers are busy. Try again shortly."
                providerCooldowns[provider] = System.currentTimeMillis() + 60_000L
                log("W", "$provider on cooldown (503) — backing off $provider for 60s")
                return Result.Cooldown(msg)
            }
            if (r.status.value in intArrayOf(429, 502, 504) && attempt < maxAttempts) {
                val retryAfter = r.headers["Retry-After"]?.toIntOrNull()?.takeIf { it in 1..3 }
                val waitMs = (retryAfter ?: attempt) * 1000L
                log("W", "$provider transient ${r.status.value}, retrying in ${waitMs}ms ($attempt/$maxAttempts)")
                kotlinx.coroutines.delay(waitMs)
                continue
            }
            resp = r
            break
        }
        val response = resp ?: return Result.Error("$provider unavailable")

        val bodyText = runCatching { response.bodyAsText() }.getOrDefault("")
        if (response.status.value !in 200..299) {
            return Result.Error("$provider HTTP ${response.status.value}")
        }

        val track = communityTrackFrom(bodyText, provider, quality, rejectPreviewLength)
            ?: return Result.NotFound.also { log("W", "$provider: no usable url in response") }
        providerCooldowns.remove(provider)
        val q = if (quality == "24") "24-bit" else "16-bit"
        log("D", "$provider FLAC resolved ($q, ${track.container})")
        return Result.Success(track)
    }

    /**
     * Resolve a TIDAL track id to a direct FLAC URL via the monochrome Hi-Fi API,
     * failing over across instances. Two-step flow (matches monochrome's client):
     *   1. GET /trackManifests/?id=&quality=LOSSLESS&adaptive=false&formats=FLAC
     *      → JSON with `data.data.attributes.uri` = a signed manifest URL.
     *   2. GET that uri → a BTS manifest (`{"urls":[...]}`) whose first url is a
     *      single-file FLAC. (Hi-res returns a segmented DASH `<MPD>` we can't use
     *      as one URL, so we request LOSSLESS for a directly-playable stream.)
     */
    private suspend fun resolveTidalMonochrome(
        tidalId: String,
        @Suppress("UNUSED_PARAMETER") preferHiRes: Boolean,
        rejectPreviewLength: Boolean,
    ): Result {
        var sawError = false
        for (base in monochromeInstances()) {
            // Instances run one of two API versions, so try both endpoint styles.
            flacViaTrackManifests(base, tidalId, rejectPreviewLength)?.let {
                log("D", "tidal FLAC via $base/trackManifests (${it.container})")
                return Result.Success(it)
            }
            flacViaTrack(base, tidalId)?.let {
                log("D", "tidal FLAC via $base/track (${it.container})")
                return Result.Success(it)
            }
            sawError = true
        }
        return if (sawError) Result.Error("Tidal backends unavailable") else Result.NotFound
    }

    /** New Hi-Fi API: /trackManifests → signed manifest uri → FLAC/DASH stream. */
    private suspend fun flacViaTrackManifests(base: String, tidalId: String, rejectPreviewLength: Boolean): LosslessTrack? {
        val lookup = runCatching {
            client.get("$base/trackManifests/") {
                parameter("id", tidalId)
                parameter("quality", "LOSSLESS")
                parameter("adaptive", "false")
                parameter("formats", "FLAC")
                header("User-Agent", UA)
                header("Accept", "application/json")
            }
        }.getOrNull() ?: return null
        if (lookup.status.value !in 200..299) return null
        val manifestUri = extractManifestUri(runCatching { lookup.bodyAsText() }.getOrDefault("")) ?: return null
        val manifestResp = runCatching { client.get(manifestUri) { header("User-Agent", UA) } }.getOrNull() ?: return null
        if (manifestResp.status.value !in 200..299) return null
        return trackFromManifest(runCatching { manifestResp.bodyAsText() }.getOrDefault(""), manifestUri, rejectPreviewLength)
    }

    /** Older hifi-api: /track/?id=&quality=LOSSLESS → OriginalTrackUrl or inline manifest. */
    private suspend fun flacViaTrack(base: String, tidalId: String): LosslessTrack? {
        val resp = runCatching {
            client.get("$base/track/") {
                parameter("id", tidalId)
                parameter("quality", "LOSSLESS")
                parameter("country", "US")
                header("User-Agent", UA)
                header("Accept", "application/json")
            }
        }.getOrNull() ?: return null
        if (resp.status.value !in 200..299) return null
        val body = runCatching { resp.bodyAsText() }.getOrDefault("")
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val data = (root["data"] as? JsonObject) ?: root
        fun JsonObject.httpUrl(key: String) =
            this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("http") }
        (data.httpUrl("OriginalTrackUrl") ?: data.httpUrl("originalTrackUrl") ?: data.httpUrl("url"))?.let {
            return LosslessTrack(url = it, provider = "tidal", quality = "16", container = "flac")
        }
        val manifest = (data["manifest"] ?: root["manifest"])?.jsonPrimitive?.contentOrNull ?: return null
        val decoded = runCatching { String(java.util.Base64.getMimeDecoder().decode(manifest)) }.getOrElse { manifest }
        return flacUrlFromManifest(decoded)?.let {
            LosslessTrack(url = it, provider = "tidal", quality = "16", container = "flac")
        }
    }

    /**
     * Turn a fetched TIDAL manifest into a playable track. A `<MPD>` is a segmented
     * DASH stream — we hand the manifest URL itself to ExoPlayer (container "dash").
     * A BTS JSON manifest yields a single-file FLAC URL (container "flac").
     */
    private fun trackFromManifest(manifestText: String, manifestUri: String, rejectPreviewLength: Boolean): LosslessTrack? {
        if (manifestText.isBlank()) return null
        if (manifestText.contains("<MPD")) {
            // Limited/no-subscription backends serve 30-second previews. Reject them
            // so a full-access backend (or full-length YouTube) is used instead.
            val durSec = mpdDurationSeconds(manifestText)
                            if (rejectPreviewLength && durSec != null && durSec < 45.0) {

                log("W", "tidal manifest is a ${durSec.toInt()}s preview — skipping")
                return null
            }
            return LosslessTrack(url = manifestUri, provider = "tidal", quality = "16", container = "dash")
        }
        return flacUrlFromManifest(manifestText)?.let {
            LosslessTrack(url = it, provider = "tidal", quality = "16", container = "flac")
        }
    }

    /** Parse a DASH `mediaPresentationDuration="PT#H#M#S"` into seconds. */
    private fun mpdDurationSeconds(mpd: String): Double? {
        val m = Regex("mediaPresentationDuration=\"PT(?:([0-9]+)H)?(?:([0-9]+)M)?([0-9.]+)S\"").find(mpd)
            ?: return null
        val h = m.groupValues[1].toDoubleOrNull() ?: 0.0
        val min = m.groupValues[2].toDoubleOrNull() ?: 0.0
        val s = m.groupValues[3].toDoubleOrNull() ?: 0.0
        return h * 3600 + min * 60 + s
    }

    /** Pull `attributes.uri` (the signed manifest URL) out of a /trackManifests response. */
    private fun extractManifestUri(body: String): String? {
        if (body.isBlank()) return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val candidates = listOf(
            root["data"]?.let { it as? JsonObject }?.get("data")?.let { it as? JsonObject },
            root["data"]?.let { it as? JsonObject },
            root,
        )
        for (node in candidates) {
            val uri = (node?.get("attributes") as? JsonObject)
                ?.get("uri")?.jsonPrimitive?.contentOrNull
            if (!uri.isNullOrBlank() && uri.startsWith("http")) return uri
        }
        return null
    }

    /**
     * Extract a single FLAC URL from a fetched TIDAL manifest. LOSSLESS uses a BTS
     * JSON manifest `{"mimeType":"audio/flac","urls":[...]}`; segmented DASH (`<MPD>`)
     * can't be a single URL so we skip it. Some instances base64-wrap the JSON.
     */
    private fun flacUrlFromManifest(manifestText: String): String? {
        if (manifestText.isBlank() || manifestText.contains("<MPD")) return null
        fun urlsFrom(text: String): List<String> = runCatching {
            (json.parseToJsonElement(text).jsonObject["urls"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
        }.getOrNull().orEmpty()

        urlsFrom(manifestText).takeIf { it.isNotEmpty() }?.let { return pickBestLosslessUrl(it) }
        val decoded = runCatching {
            String(java.util.Base64.getMimeDecoder().decode(manifestText.trim()))
        }.getOrNull()
        if (decoded != null && !decoded.contains("<MPD")) {
            urlsFrom(decoded).takeIf { it.isNotEmpty() }?.let { return pickBestLosslessUrl(it) }
        }
        return Regex("https?://[^\"\\s]+").find(manifestText)?.value
    }

    /** Prefer FLAC / lossless URLs when a manifest offers several. */
    private fun pickBestLosslessUrl(urls: List<String>): String {
        val keywords = listOf("flac", "lossless", "hi-res", "high")
        return urls.minByOrNull { url ->
            val low = url.lowercase()
            keywords.indexOfFirst { low.contains(it) }.let { if (it == -1) 999 else it }
        } ?: urls.first()
    }

    /** Pull a streamable URL out of the varied community-response shapes. */
    /**
     * Turn a community `/api/dl` response into a playable track. The `url` field is
     * either a direct http(s) FLAC URL, or `MANIFEST:<base64>` carrying a TIDAL
     * manifest — a BTS JSON (single-file FLAC) or a DASH `<MPD>` (segmented). For
     * DASH we hand ExoPlayer a `data:application/dash+xml` URI (its segment URLs are
     * absolute CDN links), skipping <45s previews.
     */
    private fun communityTrackFrom(body: String, provider: String, quality: String, rejectPreviewLength: Boolean): LosslessTrack? {
        if (body.isBlank()) return null
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        fun JsonObject.raw(key: String) = this[key]?.jsonPrimitive?.contentOrNull
        val rawUrl = obj.raw("url") ?: obj.raw("download_url")
            ?: (obj["data"] as? JsonObject)?.let { it.raw("url") ?: it.raw("download_url") }
            ?: return null

        if (rawUrl.startsWith("http")) {
            val cleanUrl = SpotifyStreamSanitizer.sanitizeUrl(rawUrl)
            return LosslessTrack(url = cleanUrl, provider = provider, quality = quality, container = "flac")
        }
        if (rawUrl.startsWith("MANIFEST:")) {
            val b64 = rawUrl.substring("MANIFEST:".length).trim()
            val decoded = runCatching {
                String(java.util.Base64.getMimeDecoder().decode(b64))
            }.getOrNull() ?: return null
            if (decoded.contains("<MPD")) {
                val durSec = mpdDurationSeconds(decoded)
                if (rejectPreviewLength && durSec != null && durSec < 45.0) {
                    log("W", "$provider manifest is a ${durSec.toInt()}s preview — skipping")
                    return null
                }
                return LosslessTrack(
                    url = "data:application/dash+xml;base64,$b64",
                    provider = provider, quality = quality, container = "dash",
                )
            }
            return flacUrlFromManifest(decoded)?.let {
                LosslessTrack(url = it, provider = provider, quality = quality, container = "flac")
            }
        }
        return null
    }

    /**
     * Download a TIDAL DASH-FLAC stream and remux it into a real single `.flac` file
     * — no ffmpeg. The DASH segments are fragmented MP4 carrying raw FLAC frames, so:
     *   1. take STREAMINFO from the init segment's `dfLa` box,
     *   2. concatenate every media segment's `mdat` payload (the FLAC frames),
     *   3. write `fLaC` + STREAMINFO metadata block + frames.
     * [streamUrl] is the `data:application/dash+xml;base64,…` URI the resolver returns
     * for TIDAL. Returns true on success.
     */
    suspend fun downloadDashFlacToFile(streamUrl: String, outFile: java.io.File): Boolean = runCatching {
        val mpd = dashMpdFromUrl(streamUrl) ?: return false
        val initUrl = htmlUnescape(Regex("initialization=\"([^\"]+)\"").find(mpd)?.groupValues?.get(1) ?: return false)
        val mediaTmpl = htmlUnescape(Regex("media=\"([^\"]+)\"").find(mpd)?.groupValues?.get(1) ?: return false)
        val startNumber = Regex("startNumber=\"(\\d+)\"").find(mpd)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val segCount = dashSegmentCount(mpd)
        if (segCount <= 0) return false

        val initBytes = httpBytes(initUrl) ?: return false
        val streamInfo = flacStreamInfoFromInit(initBytes) ?: return false

        java.io.BufferedOutputStream(java.io.FileOutputStream(outFile), 256 * 1024).use { out ->
            out.write(byteArrayOf(0x66, 0x4c, 0x61, 0x43)) // "fLaC"
            // metadata block header: last-block(0x80) | STREAMINFO(0) + 24-bit length
            out.write(0x80)
            out.write((streamInfo.size ushr 16) and 0xFF)
            out.write((streamInfo.size ushr 8) and 0xFF)
            out.write(streamInfo.size and 0xFF)
            out.write(streamInfo)

            // Fetch DASH media segments in ordered parallel batches (6 concurrent connections)
            val batchSize = 6
            var segNum = startNumber
            val endExclusive = startNumber + segCount
            coroutineScope {
                while (segNum < endExclusive) {
                    val batchEnd = minOf(segNum + batchSize, endExclusive)
                    val jobs = (segNum until batchEnd).map { idx ->
                        async { httpBytes(mediaTmpl.replace("\$Number\$", idx.toString())) }
                    }
                    for (job in jobs) {
                        val seg = job.await() ?: throw IllegalStateException("Missing DASH segment")
                        writeMdatPayloads(seg, out)
                    }
                    segNum = batchEnd
                }
            }
        }
        log("D", "tidal DASH remuxed to FLAC (${segCount} segments parallelized)")
        true
    }.getOrElse { log("W", "DASH remux failed: ${it.message}"); runCatching { outFile.delete() }; false }

    private fun dashMpdFromUrl(url: String): String? = when {
        url.startsWith("data:application/dash+xml;base64,") ->
            runCatching { String(java.util.Base64.getMimeDecoder().decode(url.substringAfter("base64,"))) }.getOrNull()
        url.contains("<MPD") -> url
        else -> null
    }

    /** Segment count from a SegmentTimeline (`<S d= r=/>`; r is a repeat count). */
    private fun dashSegmentCount(mpd: String): Int {
        var total = 0
        for (m in Regex("<S\\b[^>]*/?>").findAll(mpd)) {
            total += 1 + (Regex("\\br=\"(\\d+)\"").find(m.value)?.groupValues?.get(1)?.toIntOrNull() ?: 0)
        }
        return total
    }

    /** Pull the 34-byte STREAMINFO out of an init segment's `dfLa` box. */
    private fun flacStreamInfoFromInit(init: ByteArray): ByteArray? {
        val tag = byteArrayOf(0x64, 0x66, 0x4c, 0x61) // "dfLa"
        val k = indexOf(init, tag)
        if (k < 0 || k + 12 > init.size) return null
        // after dfLa(4) + FullBox(4): metadata block header(4) then STREAMINFO
        val len = ((init[k + 9].toInt() and 0xFF) shl 16) or
            ((init[k + 10].toInt() and 0xFF) shl 8) or (init[k + 11].toInt() and 0xFF)
        if (len <= 0 || k + 12 + len > init.size) return null
        return init.copyOfRange(k + 12, k + 12 + len)
    }

    /** Append every `mdat` box payload (the FLAC frames) from an MP4 fragment. */
    private fun writeMdatPayloads(seg: ByteArray, out: java.io.OutputStream) {
        var i = 0
        while (i + 8 <= seg.size) {
            var size = ((seg[i].toLong() and 0xFF) shl 24) or ((seg[i + 1].toLong() and 0xFF) shl 16) or
                ((seg[i + 2].toLong() and 0xFF) shl 8) or (seg[i + 3].toLong() and 0xFF)
            var hdr = 8
            if (size == 1L) {
                if (i + 16 > seg.size) break
                size = (0..7).fold(0L) { acc, b -> (acc shl 8) or (seg[i + 8 + b].toLong() and 0xFF) }
                hdr = 16
            } else if (size == 0L) {
                size = (seg.size - i).toLong()
            }
            if (size < hdr) break
            if (seg[i + 4] == 0x6D.toByte() && seg[i + 5] == 0x64.toByte() &&
                seg[i + 6] == 0x61.toByte() && seg[i + 7] == 0x74.toByte() // "mdat"
            ) {
                out.write(seg, i + hdr, (size - hdr).toInt())
            }
            i += size.toInt()
        }
    }

    private suspend fun httpBytes(url: String): ByteArray? = runCatching {
        val r = client.get(url) { header("User-Agent", UA) }
        if (r.status.value !in 200..299) null else r.readRawBytes()
    }.getOrNull()

    private fun htmlUnescape(s: String): String =
        s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    private fun md5Hex(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
