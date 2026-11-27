package com.music.spotui.ui.screens.SpotifyLogin.page

import com.music.spotui.ui.screens.SpotifyLogin.helper.parseSpotifyCookiesFromText
import com.music.spotui.util.DevConsoleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.Locale

data class LocalLoginServerState(
    val isRunning: Boolean = false,
    val port: Int = 0,
    val loopbackUrl: String = "",
    val lanUrl: String? = null,
    val allowLanAccess: Boolean = false,
    val csrfToken: String = "",
    val lastEvent: String = "Server stopped",
    val lastError: String? = null,
)

sealed interface LocalLoginSubmission {
    data class SpotifyCookie(val spDc: String, val spKey: String) : LocalLoginSubmission
    data class YouTubeCookie(val cookieHeader: String) : LocalLoginSubmission
    data class DeezerArl(val arl: String) : LocalLoginSubmission
}

/**
 * Embedded HTTP/1.1 Local URL Login Portal hosted on `127.0.0.1:<port>` (or optional LAN IP
 * so the user can open it from a PC browser, sign in to Spotify/YTM/Deezer, and send credentials
 * directly to SpotUI with zero third-party cloud relay).
 */
object LocalLoginWebServer {
    private const val TAG = "LocalLoginWebServer"
    private const val MAX_HEADER_BYTES = 16 * 1024
    private const val MAX_BODY_BYTES = 128 * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(LocalLoginServerState())
    val state: StateFlow<LocalLoginServerState> = _state.asStateFlow()

    private val _submissions = MutableSharedFlow<LocalLoginSubmission>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val submissions: SharedFlow<LocalLoginSubmission> = _submissions.asSharedFlow()

    @Volatile
    private var serverSocket: ServerSocket? = null

    private var acceptJob: Job? = null

    @Synchronized
    fun start(allowLan: Boolean = false, preferredPort: Int = 45890): LocalLoginServerState {
        if (serverSocket != null && _state.value.isRunning && _state.value.allowLanAccess == allowLan) {
            return _state.value
        }
        stop()

        val csrf = generateCsrfNonce()
        val bindAddr = if (allowLan) InetAddress.getByName("0.0.0.0") else InetAddress.getByName("127.0.0.1")
        val socket = runCatching {
            ServerSocket(preferredPort, 16, bindAddr)
        }.recoverCatching {
            ServerSocket(0, 16, bindAddr)
        }.getOrElse { err ->
            val failed = LocalLoginServerState(
                isRunning = false,
                allowLanAccess = allowLan,
                lastError = err.message ?: "Unable to bind local server socket",
                lastEvent = "Failed to start local portal",
            )
            _state.value = failed
            return failed
        }

        socket.soTimeout = 1000
        serverSocket = socket
        val boundPort = socket.localPort
        val loopback = "http://127.0.0.1:$boundPort/"
        val lan = if (allowLan) findLanIpv4Address()?.let { "http://$it:$boundPort/" } else null

        val activeState = LocalLoginServerState(
            isRunning = true,
            port = boundPort,
            loopbackUrl = loopback,
            lanUrl = lan,
            allowLanAccess = allowLan,
            csrfToken = csrf,
            lastEvent = "Listening on $loopback",
            lastError = null,
        )
        _state.value = activeState
        DevConsoleManager.logAuth("Local URL Login Portal listening at $loopback" + (lan?.let { " (LAN: $it)" }.orEmpty()))

        acceptJob = scope.launch {
            while (isActive && serverSocket === socket && !socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: Throwable) {
                    break
                }
                launch(Dispatchers.IO) {
                    handleClientSocket(client)
                }
            }
        }
        return activeState
    }

    @Synchronized
    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        _state.value = _state.value.copy(
            isRunning = false,
            lastEvent = "Server stopped",
        )
    }

    fun emitSubmissionForTest(submission: LocalLoginSubmission) {
        _submissions.tryEmit(submission)
    }

    private fun handleClientSocket(socket: Socket) {
        socket.use { sock ->
            sock.soTimeout = 5000
            val input = BufferedInputStream(sock.getInputStream())
            val rawHeaderBytes = ByteArrayOutputStream()
            var prev4 = 0
            while (rawHeaderBytes.size() < MAX_HEADER_BYTES) {
                val b = input.read()
                if (b == -1) break
                rawHeaderBytes.write(b)
                prev4 = (prev4 shl 8) or (b and 0xFF)
                if (prev4 == 0x0D0A0D0A) break
            }
            val headerText = rawHeaderBytes.toString(Charsets.UTF_8.name())
            val lines = headerText.split("\r\n").filter { it.isNotEmpty() }
            if (lines.isEmpty()) return

            val requestParts = lines.first().split(" ")
            val method = requestParts.getOrElse(0) { "GET" }.uppercase(Locale.US)
            val path = requestParts.getOrElse(1) { "/" }.substringBefore("?")

            val headers = mutableMapOf<String, String>()
            for (i in 1 until lines.size) {
                val idx = lines[i].indexOf(':')
                if (idx > 0) {
                    headers[lines[i].substring(0, idx).trim().lowercase(Locale.US)] =
                        lines[i].substring(idx + 1).trim()
                }
            }

            val contentLength = (headers["content-length"]?.toIntOrNull() ?: 0).coerceIn(0, MAX_BODY_BYTES)
            val bodyBytes = if (contentLength > 0) {
                val buf = ByteArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val n = input.read(buf, readTotal, contentLength - readTotal)
                    if (n <= 0) break
                    readTotal += n
                }
                buf.copyOf(readTotal)
            } else {
                ByteArray(0)
            }
            val bodyString = String(bodyBytes, Charsets.UTF_8)
            val response = routeRequest(method, path, headers, bodyString)

            val payload = response.body.toByteArray(Charsets.UTF_8)
            val head = "HTTP/1.1 ${response.statusCode} ${response.reason}\r\n" +
                "Content-Type: ${response.contentType}; charset=utf-8\r\n" +
                "Content-Length: ${payload.size}\r\n" +
                "Cache-Control: no-store\r\n" +
                "X-Content-Type-Options: nosniff\r\n" +
                "Connection: close\r\n\r\n"

            sock.getOutputStream().apply {
                write(head.toByteArray(Charsets.US_ASCII))
                write(payload)
                flush()
            }
        }
    }

    internal data class HttpResponse(
        val statusCode: Int,
        val reason: String,
        val contentType: String,
        val body: String,
    )

    internal fun routeRequest(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: String,
        expectedCsrf: String = _state.value.csrfToken,
        onSubmission: (LocalLoginSubmission) -> Unit = { _submissions.tryEmit(it) },
    ): HttpResponse {
        if (method == "OPTIONS") {
            return HttpResponse(204, "No Content", "text/plain", "")
        }
        return when {
            method == "GET" && (path == "/" || path == "/index.html") -> {
                val snap = _state.value
                val token = expectedCsrf.ifBlank { snap.csrfToken }
                HttpResponse(
                    200,
                    "OK",
                    "text/html",
                    buildPortalHtml(
                        loopbackUrl = snap.loopbackUrl.ifBlank { "http://127.0.0.1:${snap.port}/" },
                        lanUrl = snap.lanUrl,
                        csrfToken = token,
                    ),
                )
            }
            method == "GET" && path == "/api/status" -> {
                val snap = _state.value
                val json = JSONObject().apply {
                    put("ok", true)
                    put("app", "SpotUI")
                    put("port", snap.port)
                    put("lastEvent", snap.lastEvent)
                }
                HttpResponse(200, "OK", "application/json", json.toString())
            }
            method == "POST" && path.startsWith("/api/") -> {
                val parsedJson = runCatching { JSONObject(body) }.getOrNull()
                val headerCsrf = headers["x-spotui-csrf"].orEmpty()
                val bodyCsrf = parsedJson?.optString("csrfToken").orEmpty()
                val tokenValid = expectedCsrf.isNotBlank() &&
                    (headerCsrf == expectedCsrf || bodyCsrf == expectedCsrf)
                if (!tokenValid) {
                    return HttpResponse(
                        403,
                        "Forbidden",
                        "application/json",
                        """{"ok":false,"error":"Invalid or missing CSRF token. Reload this local portal page."}""",
                    )
                }

                when (path) {
                    "/api/spotify/cookie" -> {
                        val raw = parsedJson?.optString("payload").orEmpty().ifBlank { body }
                        val (spDc, spKey) = parseSpotifyCookiesFromText(raw)
                        if (spDc.isBlank()) {
                            HttpResponse(
                                400,
                                "Bad Request",
                                "application/json",
                                """{"ok":false,"error":"Could not find a valid sp_dc cookie value."}""",
                            )
                        } else {
                            onSubmission(LocalLoginSubmission.SpotifyCookie(spDc, spKey))
                            _state.value = _state.value.copy(
                                lastEvent = "Spotify session received from local portal",
                                lastError = null,
                            )
                            HttpResponse(
                                200,
                                "OK",
                                "application/json",
                                """{"ok":true,"message":"Spotify cookie sent to SpotUI! Check the app."}""",
                            )
                        }
                    }
                    "/api/ytmusic/cookie" -> {
                        val cookie = parsedJson?.optString("cookie").orEmpty().trim()
                        if (cookie.length < 16) {
                            HttpResponse(
                                400,
                                "Bad Request",
                                "application/json",
                                """{"ok":false,"error":"Paste the full YouTube Music Cookie header."}""",
                            )
                        } else {
                            onSubmission(LocalLoginSubmission.YouTubeCookie(cookie))
                            _state.value = _state.value.copy(
                                lastEvent = "YouTube Music cookie received",
                                lastError = null,
                            )
                            HttpResponse(
                                200,
                                "OK",
                                "application/json",
                                """{"ok":true,"message":"YouTube Music cookie saved in SpotUI!"}""",
                            )
                        }
                    }
                    "/api/deezer/arl" -> {
                        val arl = parsedJson?.optString("arl").orEmpty().trim()
                        if (arl.length < 32) {
                            HttpResponse(
                                400,
                                "Bad Request",
                                "application/json",
                                """{"ok":false,"error":"Deezer ARL cookie appears too short."}""",
                            )
                        } else {
                            onSubmission(LocalLoginSubmission.DeezerArl(arl))
                            _state.value = _state.value.copy(
                                lastEvent = "Deezer ARL received",
                                lastError = null,
                            )
                            HttpResponse(
                                200,
                                "OK",
                                "application/json",
                                """{"ok":true,"message":"Deezer ARL updated in SpotUI!"}""",
                            )
                        }
                    }
                    else -> HttpResponse(404, "Not Found", "application/json", """{"ok":false,"error":"Unknown API route"}""")
                }
            }
            else -> HttpResponse(404, "Not Found", "text/plain", "Not Found")
        }
    }

    internal fun buildPortalHtml(
        loopbackUrl: String,
        lanUrl: String?,
        csrfToken: String,
    ): String {
        val safeCsrf = htmlEscape(csrfToken)
        val lanNote = lanUrl?.let { "<div>LAN URL (same Wi-Fi): <code>${htmlEscape(it)}</code></div>" }.orEmpty()
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8"/>
              <meta name="viewport" content="width=device-width,initial-scale=1"/>
              <title>SpotUI Local Login Portal</title>
              <style>
                :root { --bg:#090c12; --card:#131824; --border:#252d40; --accent:#1ed760; --muted:#9aa5bc; --text:#f4f7ff; }
                *{box-sizing:border-box}
                body{margin:0;padding:20px;background:radial-gradient(circle at top,#172235,#090c12 60%);color:var(--text);font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
                .wrap{max-width:720px;margin:0 auto;display:grid;gap:16px}
                .hero{background:linear-gradient(135deg,#152033,#101521);border:1px solid var(--border);border-radius:18px;padding:20px}
                h1{margin:0 0 6px;font-size:22px}
                .sub{color:var(--muted);font-size:13px;line-height:1.45}
                .card{background:var(--card);border:1px solid var(--border);border-radius:16px;padding:18px}
                .card h2{margin:0 0 6px;font-size:16px}
                textarea,input{width:100%;background:#0b0f17;color:var(--text);border:1px solid #2b354b;border-radius:12px;padding:12px;font-family:ui-monospace,monospace;font-size:12px;margin:10px 0}
                .row{display:flex;gap:10px;flex-wrap:wrap;align-items:center}
                button,a.btn{display:inline-flex;align-items:center;justify-content:center;border:none;border-radius:999px;padding:11px 18px;font-weight:700;font-size:13px;cursor:pointer;text-decoration:none}
                .primary{background:var(--accent);color:#041309}
                .secondary{background:#21293b;color:var(--text)}
                .status{margin-top:10px;font-size:13px;color:var(--accent);min-height:18px}
                .err{color:#ff6b7a}
                code{background:#0b0f17;padding:2px 6px;border-radius:6px;font-size:12px}
              </style>
            </head>
            <body>
              <div class="wrap">
                <div class="hero">
                  <h1>SpotUI Local Auth Bridge</h1>
                  <div class="sub">
                    Running strictly on your device (<code>${htmlEscape(loopbackUrl)}</code>).
                    $lanNote
                    Sign in to Spotify in a real browser tab, copy your <code>sp_dc</code> cookie (or JSON export from Cookie-Editor), and send it directly into SpotUI.
                  </div>
                  <div class="row" style="margin-top:14px">
                    <a class="btn primary" href="https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F" target="_blank" rel="noopener">Open Spotify Login</a>
                    <a class="btn secondary" href="https://music.youtube.com/" target="_blank" rel="noopener">Open YT Music</a>
                    <a class="btn secondary" href="https://www.deezer.com/login" target="_blank" rel="noopener">Open Deezer</a>
                  </div>
                </div>

                <div class="card">
                  <h2>1. Spotify Session (`sp_dc` or Cookie JSON)</h2>
                  <div class="sub">Paste raw <code>sp_dc</code> value, header string (<code>sp_dc=...; sp_key=...</code>), or Cookie-Editor JSON array.</div>
                  <textarea id="spPayload" rows="3" placeholder="Paste sp_dc value or cookie JSON here..."></textarea>
                  <button class="primary" onclick="sendJson('/api/spotify/cookie',{payload:document.getElementById('spPayload').value},'spStatus')">Connect Spotify Session</button>
                  <div id="spStatus" class="status"></div>
                </div>

                <div class="card">
                  <h2>2. YouTube Music Cookie (Optional)</h2>
                  <div class="sub">Enables private YouTube Music library sync & age-restricted playback.</div>
                  <textarea id="ytPayload" rows="2" placeholder="Paste Cookie header from music.youtube.com..."></textarea>
                  <button class="secondary" onclick="sendJson('/api/ytmusic/cookie',{cookie:document.getElementById('ytPayload').value},'ytStatus')">Save YT Music Cookie</button>
                  <div id="ytStatus" class="status"></div>
                </div>

                <div class="card">
                  <h2>3. Deezer ARL (Optional Lossless FLAC)</h2>
                  <div class="sub">Enables Deezer CD-quality streaming in SpotUI's lossless fallback router.</div>
                  <input id="dzPayload" type="text" placeholder="Paste 192-char Deezer arl token..."/>
                  <button class="secondary" onclick="sendJson('/api/deezer/arl',{arl:document.getElementById('dzPayload').value},'dzStatus')">Save Deezer ARL</button>
                  <div id="dzStatus" class="status"></div>
                </div>
              </div>

              <script>
                const CSRF = "$safeCsrf";
                async function sendJson(path, body, statusId) {
                  const el = document.getElementById(statusId);
                  el.className = "status";
                  el.textContent = "Sending to SpotUI...";
                  try {
                    const res = await fetch(path, {
                      method: "POST",
                      headers: { "Content-Type": "application/json", "X-SpotUI-CSRF": CSRF },
                      body: JSON.stringify(Object.assign({}, body, { csrfToken: CSRF }))
                    });
                    const data = await res.json();
                    if (!res.ok || !data.ok) {
                      el.className = "status err";
                      el.textContent = data.error || ("HTTP " + res.status);
                    } else {
                      el.className = "status";
                      el.textContent = data.message || "Connected!";
                    }
                  } catch (e) {
                    el.className = "status err";
                    el.textContent = "Connection error: " + e.message;
                  }
                }
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun htmlEscape(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private fun generateCsrfNonce(): String {
        val bytes = ByteArray(18)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun findLanIpv4Address(): String? = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching null
        for (iface in interfaces) {
            if (!iface.isUp || iface.isLoopback) continue
            val addrs = iface.inetAddresses
            while (addrs.hasMoreElements()) {
                val addr = addrs.nextElement()
                if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                    return@runCatching addr.hostAddress
                }
            }
        }
        null
    }.getOrNull()
}
