package com.metrolist.innertube.proxy

import java.io.IOException
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

data class ProxyEndpoint(
    val proxy: Proxy,
    val rawUrl: String,
    val basicAuthHeader: String? = null,
    val username: String? = null,
    val password: String? = null,
)

object RotatingProxyClient {
    private val endpoints = CopyOnWriteArrayList<ProxyEndpoint>()
    private val cursor = AtomicInteger(0)

    @Volatile
    private var lastSelected: ProxyEndpoint? = null

    fun setProxies(urls: List<String>) {
        endpoints.clear()
        cursor.set(0)
        lastSelected = null
        urls.mapNotNull(::parseProxyUrl).forEach { endpoints.add(it) }
    }

    fun setProxyUrls(commaOrNewlineSeparated: String?) {
        if (commaOrNewlineSeparated.isNullOrBlank()) {
            reset()
            return
        }
        val list = commaOrNewlineSeparated
            .split(',', '\n', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        setProxies(list)
    }

    fun activeCount(): Int = endpoints.size

    fun current(): ProxyEndpoint? {
        if (endpoints.isEmpty()) return null
        val index = Math.floorMod(cursor.get(), endpoints.size)
        val selected = endpoints.getOrNull(index)
        lastSelected = selected
        return selected
    }

    fun rotate(): ProxyEndpoint? {
        if (endpoints.isEmpty()) return null
        val index = Math.floorMod(cursor.incrementAndGet(), endpoints.size)
        val selected = endpoints.getOrNull(index)
        lastSelected = selected
        return selected
    }

    fun markLastSelectedFailed(): ProxyEndpoint? {
        val failed = lastSelected
        if (endpoints.size <= 1) return endpoints.firstOrNull()
        val next = rotate()
        if (failed != null && endpoints.size > 1 && next == failed) {
            return rotate()
        }
        return next
    }

    fun reset() {
        endpoints.clear()
        cursor.set(0)
        lastSelected = null
    }

    fun parseProxyUrl(raw: String): ProxyEndpoint? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val normalized = if ("://" in trimmed) trimmed else "http://$trimmed"
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        val host = uri.host?.takeIf(String::isNotBlank) ?: return null
        val scheme = uri.scheme?.lowercase().orEmpty()
        val proxyType = when {
            scheme.startsWith("socks") -> Proxy.Type.SOCKS
            else -> Proxy.Type.HTTP
        }
        val port = when {
            uri.port > 0 -> uri.port
            proxyType == Proxy.Type.SOCKS -> 1080
            scheme == "https" -> 443
            else -> 8080
        }
        val userInfo = uri.userInfo
        val (user, pass) = if (!userInfo.isNullOrBlank() && ':' in userInfo) {
            userInfo.substringBefore(':') to userInfo.substringAfter(':')
        } else if (!userInfo.isNullOrBlank()) {
            userInfo to ""
        } else {
            null to null
        }
        val authHeader = if (!user.isNullOrEmpty()) {
            val encoded = Base64.getEncoder().encodeToString("$user:${pass.orEmpty()}".toByteArray())
            "Basic $encoded"
        } else {
            null
        }
        return ProxyEndpoint(
            proxy = Proxy(proxyType, InetSocketAddress(host, port)),
            rawUrl = trimmed,
            basicAuthHeader = authHeader,
            username = user,
            password = pass,
        )
    }

    fun applySocksAuthenticatorIfNeeded(username: String?, password: String?) {
        if (username.isNullOrBlank()) return
        Authenticator.setDefault(
            object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication? {
                    if (requestorType == RequestorType.PROXY) {
                        return PasswordAuthentication(username, (password ?: "").toCharArray())
                    }
                    return null
                }
            },
        )
    }
}

class RotatingProxySelector(
    private val fallbackProxyProvider: () -> Proxy?,
) : ProxySelector() {
    override fun select(uri: URI?): MutableList<Proxy> {
        val rotating = RotatingProxyClient.current()?.proxy
        if (rotating != null) {
            return mutableListOf(rotating)
        }
        val fallback = fallbackProxyProvider()
        return if (fallback != null) mutableListOf(fallback) else mutableListOf(Proxy.NO_PROXY)
    }

    override fun connectFailed(
        uri: URI?,
        sa: SocketAddress?,
        ioe: IOException?,
    ) {
        RotatingProxyClient.markLastSelectedFailed()
    }
}
