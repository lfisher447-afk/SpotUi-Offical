package com.metrolist.innertube.network

import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YouTubeLocale
import com.metrolist.innertube.proxy.RotatingProxyClient
import com.metrolist.innertube.proxy.RotatingProxySelector
import com.metrolist.innertube.utils.parseCookieString
import io.ktor.http.URLBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.Proxy
import java.util.Base64
import java.util.Locale

enum class DnsOverHttpsPreset(
    val displayName: String,
    val url: String?,
    val bootstrapHosts: List<String> = emptyList(),
) {
    SYSTEM("System DNS", null),
    CLOUDFLARE("Cloudflare (1.1.1.1)", "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    GOOGLE("Google (8.8.8.8)", "https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4")),
    QUAD9("Quad9 (9.9.9.9)", "https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112")),
    ADGUARD("AdGuard DNS", "https://dns.adguard-dns.com/dns-query", listOf("94.140.14.14", "94.140.15.15")),
    ;

    companion object {
        fun fromName(raw: String?): DnsOverHttpsPreset =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SYSTEM
    }
}

object AuthSessionStore {
    private val lock = Any()

    private val _authState = MutableStateFlow(PlaybackAuthState.EMPTY)
    val authStateFlow: StateFlow<PlaybackAuthState> = _authState.asStateFlow()

    @Volatile
    var locale: YouTubeLocale = YouTubeLocale(
        gl = Locale.getDefault().country.ifBlank { "US" },
        hl = Locale.getDefault().toLanguageTag().ifBlank { "en" },
    )

    @Volatile
    var useLoginForBrowse: Boolean = false

    @Volatile
    var proxy: Proxy? = null
        set(value) {
            field = value
            bumpNetworkGeneration()
        }

    @Volatile
    var proxyAuth: String? = null
        set(value) {
            field = value
            bumpNetworkGeneration()
        }

    @Volatile
    var proxyUrl: String? = null
        private set

    @Volatile
    var ipRotationEnabled: Boolean = false
        set(value) {
            field = value
            bumpNetworkGeneration()
        }

    @Volatile
    var streamBypassProxy: Boolean = false
        set(value) {
            field = value
            bumpNetworkGeneration()
        }

    @Volatile
    var dnsPreset: DnsOverHttpsPreset = DnsOverHttpsPreset.SYSTEM
        set(value) {
            if (field != value) {
                field = value
                cachedDohDns = null
                bumpNetworkGeneration()
            }
        }

    @Volatile
    private var networkGeneration: Long = 0L

    @Volatile
    private var cachedDohPreset: DnsOverHttpsPreset? = null

    @Volatile
    private var cachedDohDns: Dns? = null

    var visitorData: String?
        get() = _authState.value.visitorData
        set(value) {
            updateAuthState { it.copy(visitorData = value) }
        }

    var dataSyncId: String?
        get() = _authState.value.dataSyncId
        set(value) {
            updateAuthState { it.copy(dataSyncId = value) }
        }

    var cookie: String?
        get() = _authState.value.cookie
        set(value) {
            updateAuthState { it.copy(cookie = value) }
        }

    var poToken: String?
        get() = _authState.value.poToken
        set(value) {
            updateAuthState { it.copy(poToken = value) }
        }

    var poTokenGvs: String?
        get() = _authState.value.poTokenGvs
        set(value) {
            updateAuthState { it.copy(poTokenGvs = value) }
        }

    var poTokenPlayer: String?
        get() = _authState.value.poTokenPlayer
        set(value) {
            updateAuthState { it.copy(poTokenPlayer = value) }
        }

    var webClientPoTokenEnabled: Boolean
        get() = _authState.value.webClientPoTokenEnabled
        set(value) {
            updateAuthState { it.copy(webClientPoTokenEnabled = value) }
        }

    fun currentAuthState(): PlaybackAuthState = _authState.value
    fun current(): PlaybackAuthState = currentAuthState()

    fun applyAuthState(state: PlaybackAuthState): PlaybackAuthState {
        val normalized = state.normalized()
        synchronized(lock) {
            _authState.value = normalized
        }
        return normalized
    }

    inline fun updateAuthState(transform: (PlaybackAuthState) -> PlaybackAuthState): PlaybackAuthState =
        applyAuthState(transform(currentAuthState()))

    fun updateFromWebViewCookies(
        cookieString: String?,
        visitorData: String? = null,
        dataSyncId: String? = null,
    ): PlaybackAuthState {
        val parsed = if (cookieString.isNullOrBlank()) emptyMap() else parseCookieString(cookieString)
        return updateAuthState { current ->
            current.copy(
                cookie = cookieString ?: current.cookie,
                visitorData = visitorData
                    ?: parsed["VISITOR_INFO1_LIVE"]?.takeIf { current.visitorData.isNullOrBlank() }
                    ?: current.visitorData,
                dataSyncId = dataSyncId ?: current.dataSyncId,
            )
        }
    }

    fun applyProxyConfiguration(rawProxyUrls: String?) {
        val cleaned = rawProxyUrls?.trim()?.takeIf(String::isNotEmpty)
        proxyUrl = cleaned
        if (cleaned == null) {
            RotatingProxyClient.reset()
            proxy = null
            proxyAuth = null
            bumpNetworkGeneration()
            return
        }
        RotatingProxyClient.setProxyUrls(cleaned)
        val first = RotatingProxyClient.current()
        if (first != null) {
            proxy = first.proxy
            proxyAuth = first.basicAuthHeader
            RotatingProxyClient.applySocksAuthenticatorIfNeeded(first.username, first.password)
        } else {
            proxy = null
            proxyAuth = null
        }
        bumpNetworkGeneration()
    }

    fun setBasicProxyAuth(username: String?, password: String?) {
        val user = username?.trim().orEmpty()
        proxyAuth = if (user.isNotEmpty()) {
            val encoded = Base64.getEncoder().encodeToString("$user:${password.orEmpty()}".toByteArray())
            "Basic $encoded"
        } else {
            null
        }
        RotatingProxyClient.applySocksAuthenticatorIfNeeded(user.ifBlank { null }, password)
    }

    fun rotateIp(): Boolean {
        val rotated = RotatingProxyClient.rotate() ?: return false
        proxy = rotated.proxy
        if (rotated.basicAuthHeader != null) {
            proxyAuth = rotated.basicAuthHeader
        }
        bumpNetworkGeneration()
        return true
    }

    fun onProxyRequestFailed() {
        if (!ipRotationEnabled && RotatingProxyClient.activeCount() <= 1) return
        val rotated = RotatingProxyClient.markLastSelectedFailed() ?: return
        proxy = rotated.proxy
        if (rotated.basicAuthHeader != null) {
            proxyAuth = rotated.basicAuthHeader
        }
        bumpNetworkGeneration()
    }

    fun activeProxyCount(): Int = RotatingProxyClient.activeCount().coerceAtLeast(if (proxy != null) 1 else 0)

    fun okHttpProxy(): Proxy? =
        if (ipRotationEnabled && RotatingProxyClient.activeCount() > 0) {
            RotatingProxyClient.current()?.proxy ?: proxy
        } else {
            proxy
        }

    fun streamOkHttpProxy(): Proxy? = if (streamBypassProxy) Proxy.NO_PROXY else okHttpProxy()

    fun currentNetworkFingerprint(): String =
        listOf(
            networkGeneration.toString(),
            dnsPreset.name,
            ipRotationEnabled.toString(),
            streamBypassProxy.toString(),
            proxy?.toString().orEmpty(),
            proxyAuth.orEmpty(),
        ).joinToString("|")

    fun resolveDns(): Dns {
        val preset = dnsPreset
        if (preset == DnsOverHttpsPreset.SYSTEM || preset.url == null) {
            return Dns.SYSTEM
        }
        val existing = cachedDohDns
        if (existing != null && cachedDohPreset == preset) {
            return existing
        }
        return synchronized(lock) {
            val recheck = cachedDohDns
            if (recheck != null && cachedDohPreset == preset) {
                recheck
            } else {
                val bootstrapClient = OkHttpClient.Builder().build()
                val bootstrapAddresses = preset.bootstrapHosts.mapNotNull { host ->
                    runCatching { InetAddress.getByName(host) }.getOrNull()
                }
                val doh = DnsOverHttps.Builder()
                    .client(bootstrapClient)
                    .url(preset.url.toHttpUrl())
                    .apply {
                        if (bootstrapAddresses.isNotEmpty()) {
                            bootstrapDnsHosts(bootstrapAddresses)
                        }
                    }
                    .build()
                cachedDohPreset = preset
                cachedDohDns = doh
                doh
            }
        }
    }

    fun applyOkHttpSettings(
        builder: OkHttpClient.Builder,
        forStream: Boolean = false,
    ): OkHttpClient.Builder {
        builder.dns(resolveDns())
        if (forStream && streamBypassProxy) {
            builder.proxy(Proxy.NO_PROXY)
            return builder
        }
        if (ipRotationEnabled && RotatingProxyClient.activeCount() > 0) {
            builder.proxySelector(RotatingProxySelector { okHttpProxy() })
        } else {
            okHttpProxy()?.let { configuredProxy ->
                builder.proxy(configuredProxy)
            }
        }
        val activeAuth = RotatingProxyClient.current()?.basicAuthHeader ?: proxyAuth
        if (!activeAuth.isNullOrBlank()) {
            builder.proxyAuthenticator { _, response ->
                if (response.request.header("Proxy-Authorization") != null) {
                    null
                } else {
                    response.request.newBuilder()
                        .header("Proxy-Authorization", activeAuth)
                        .build()
                }
            }
        }
        return builder
    }

    fun appendGvsPoToken(
        url: String,
        client: YouTubeClient? = null,
    ): String {
        val gvsToken = currentAuthState().resolveGvsPoToken(client) ?: return url
        return runCatching {
            val builder = URLBuilder(url)
            if (builder.parameters["pot"].isNullOrBlank()) {
                builder.parameters.append("pot", gvsToken)
            }
            builder.buildString()
        }.getOrDefault(url)
    }

    private fun bumpNetworkGeneration() {
        networkGeneration += 1L
    }
}
