package com.music.spotui.ui.screens.SpotifyLogin.helper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.webkit.WebViewCompat
import com.music.spotui.util.DevConsoleManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal const val LOGIN_SPOTIFY_GREEN = 0xFF1ED760
internal const val LOGIN_BACKGROUND_DARK = 0xFF0B0E14
internal const val LOGIN_CARD_DARK = 0xFF151924
internal const val LOGIN_TEXT_SECONDARY = 0xFFAAB2C4

private const val MAX_CLIPBOARD_PAYLOAD_CHARS = 20_000
private val RAW_SP_DC_VALUE = Regex("^AQ[A-Za-z0-9_-]{50,}$")

internal enum class LoginTab(val label: String) {
    WEBVIEW("WebView Sign-In"),
    PASTE_COOKIE("Backup Cookie"),
}

internal enum class LoginUaProfile(val label: String) {
    SYSTEM_DEFAULT("Native WebView"),
    MOBILE_CHROME("Chrome Mobile"),
    DESKTOP_CHROME("Desktop Chrome"),
}

private val looseCookieJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Resolves a User-Agent aligned with the device's actual installed Chromium WebView package
 * so `User-Agent` and `Sec-CH-UA` Client Hints match without triggering Spotify anti-bot errors.
 *
 * - [LoginUaProfile.SYSTEM_DEFAULT]: Uses exact system WebView UA (100% Client Hints alignment).
 * - [LoginUaProfile.MOBILE_CHROME]: Strips `; wv` / `Version/x.x` while keeping the exact installed Chromium version.
 * - [LoginUaProfile.DESKTOP_CHROME]: Uses Desktop header with the exact installed Chromium major version.
 */
internal fun resolveLoginUserAgent(context: Context, profile: LoginUaProfile): String {
    val defaultUa = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull().orEmpty()
    val installedVersion = runCatching {
        WebViewCompat.getCurrentWebViewPackage(context)?.versionName
    }.getOrNull().orEmpty()
    val chromeMajor = installedVersion.substringBefore('.').toIntOrNull()?.takeIf { it >= 90 }
        ?: Regex("""Chrome/(\d+)""").find(defaultUa)?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: 136

    return when (profile) {
        LoginUaProfile.SYSTEM_DEFAULT -> defaultUa
        LoginUaProfile.MOBILE_CHROME -> {
            if (defaultUa.isBlank()) {
                "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeMajor.0.0.0 Mobile Safari/537.36"
            } else {
                defaultUa
                    .replace(Regex(";\\s*wv\\b", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("\\bwv;\\s*", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("\\bVersion/\\d+(?:\\.\\d+)*\\s*", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("\\s{2,}"), " ")
                    .trim()
            }
        }
        LoginUaProfile.DESKTOP_CHROME -> {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeMajor.0.0.0 Safari/537.36"
        }
    }
}

/**
 * Accepts:
 * - Raw `sp_dc` string
 * - HTTP Cookie header (`sp_dc=...; sp_key=...`)
 * - cURL commands (`-H 'cookie: ...'` or `-b '...'`)
 * - Cookie-Editor JSON array or object
 * - Netscape `cookies.txt` tab-separated lines
 */
internal fun parseSpotifyCookiesFromText(raw: String): Pair<String, String> {
    val normalized = raw.trim().let {
        if (it.contains("sp_dc%3D", ignoreCase = true)) {
            runCatching { Uri.decode(it) }.getOrDefault(it)
        } else {
            it
        }
    }
    val trimmed = normalized.trim()
    if (trimmed.isBlank() || trimmed.equals("Not found", ignoreCase = true)) return "" to ""

    if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
        runCatching {
            val elem = looseCookieJson.parseToJsonElement(trimmed)
            var foundDc = ""
            var foundKey = ""
            if (trimmed.startsWith("[")) {
                for (item in elem.jsonArray) {
                    val obj = item.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.content.orEmpty()
                    val value = obj["value"]?.jsonPrimitive?.content.orEmpty()
                    if (name.equals("sp_dc", ignoreCase = true) && value.isNotBlank()) foundDc = value.trim()
                    if (name.equals("sp_key", ignoreCase = true) && value.isNotBlank()) foundKey = value.trim()
                }
            } else {
                val obj = elem.jsonObject
                foundDc = obj["sp_dc"]?.jsonPrimitive?.content.orEmpty().trim()
                foundKey = obj["sp_key"]?.jsonPrimitive?.content.orEmpty().trim()
            }
            if (foundDc.isNotBlank()) return foundDc to foundKey
        }
    }

    val spDcRegex = Regex("(?:^|[;\\s,'\"])sp_dc\\s*=\\s*\"?([^;\"'\\r\\n\\s]+)\"?", RegexOption.IGNORE_CASE)
    val spKeyRegex = Regex("(?:^|[;\\s,'\"])sp_key\\s*=\\s*\"?([^;\"'\\r\\n\\s]+)\"?", RegexOption.IGNORE_CASE)
    val dcMatch = spDcRegex.find(trimmed)?.groupValues?.getOrNull(1)?.trim()
    if (!dcMatch.isNullOrBlank()) {
        val keyMatch = spKeyRegex.find(trimmed)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        return dcMatch to keyMatch
    }

    for (line in trimmed.lineSequence()) {
        val cols = line.split('\t')
        if (cols.size >= 7 && cols[5].trim().equals("sp_dc", ignoreCase = true)) {
            val valDc = cols[6].trim()
            if (valDc.isNotBlank()) return valDc to ""
        }
    }

    val cleaned = trimmed
        .removePrefix("sp_dc=")
        .split(";")
        .first()
        .trim()
        .trim('"', '\'')
        .filterNot { it.isWhitespace() || it.isISOControl() }
    return cleaned to ""
}

internal data class CookieSweepResult(
    val spDc: String?,
    val spKey: String,
    val totalCookieKeysFound: Int,
)

internal fun sweepSpotifyCookies(currentUrl: String? = null): CookieSweepResult {
    val manager = runCatching { CookieManager.getInstance() }.getOrNull()
        ?: return CookieSweepResult(null, "", 0)

    val urls = buildList {
        currentUrl?.takeIf { it.isNotBlank() && it.contains("spotify", ignoreCase = true) }?.let { add(it) }
        add("https://open.spotify.com/")
        add("https://accounts.spotify.com/")
        add("https://www.spotify.com/")
        add("https://spotify.com/")
    }.distinct()

    var foundDc: String? = null
    var foundKey = ""
    val seenNames = HashSet<String>()

    for (url in urls) {
        val allCookies = runCatching { manager.getCookie(url) }.getOrNull() ?: continue
        for (chunk in allCookies.split(";")) {
            val parts = chunk.trim().split("=", limit = 2)
            if (parts.size != 2) continue
            val name = parts[0].trim()
            val value = parts[1].trim()
            if (name.isNotEmpty()) seenNames.add(name)
            if (name.equals("sp_dc", ignoreCase = true) && value.isNotBlank() && foundDc == null) {
                foundDc = value
            } else if (name.equals("sp_key", ignoreCase = true) && value.isNotBlank() && foundKey.isEmpty()) {
                foundKey = value
            }
        }
    }

    return CookieSweepResult(foundDc, foundKey, seenNames.size)
}

internal fun resetSpotifyWebSession(webView: WebView?, onDone: () -> Unit) {
    runCatching {
        runCatching { WebStorage.getInstance().deleteAllData() }
        runCatching {
            webView?.clearCache(true)
            webView?.clearFormData()
            webView?.clearHistory()
        }
        val cm = CookieManager.getInstance()
        cm.removeAllCookies {
            runCatching {
                cm.flush()
                cm.setAcceptCookie(true)
                webView?.let { cm.setAcceptThirdPartyCookies(it, true) }
            }
            DevConsoleManager.logAuth("Cleared WebView cookies, WebStorage & cache cleanly")
            onDone()
        }
    }.onFailure {
        onDone()
    }
}

internal fun looksLikeSpotifyCookiePayload(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.length > MAX_CLIPBOARD_PAYLOAD_CHARS) return false
    return trimmed.contains("sp_dc", ignoreCase = true) || RAW_SP_DC_VALUE.matches(trimmed)
}

internal fun readClipboardText(context: Context): String {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return ""
    val clip = manager.primaryClip ?: return ""
    if (clip.itemCount == 0) return ""
    return runCatching { clip.getItemAt(0).coerceToText(context)?.toString().orEmpty() }.getOrDefault("")
}

internal fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied $label", Toast.LENGTH_SHORT).show()
}

private val SECRET_REDACTIONS: List<Regex> = listOf(
    Regex("""(sp_dc\s*[=:]\s*"?)[^;"'\s]+""", RegexOption.IGNORE_CASE),
    Regex("""(sp_key\s*[=:]\s*"?)[^;"'\s]+""", RegexOption.IGNORE_CASE),
    Regex("""(Bearer\s+)[A-Za-z0-9._~+/=-]+""", RegexOption.IGNORE_CASE),
    Regex("""(accessToken"?\s*[=:]\s*"?)[A-Za-z0-9._~+/=-]{16,}""", RegexOption.IGNORE_CASE),
)
private val RAW_SP_DC_IN_TEXT = Regex("""\bAQ[A-Za-z0-9_-]{50,}""")

/** Masks cookie / bearer values so logs shown on screen or copied to the clipboard never leak a session. */
internal fun redactSecrets(text: String): String {
    var out = text
    for (pattern in SECRET_REDACTIONS) out = pattern.replace(out, "\$1<redacted>")
    return RAW_SP_DC_IN_TEXT.replace(out, "<redacted>")
}

@Composable
internal fun LoginDiagnosticsDialog(
    logs: List<DevConsoleManager.LogEntry>,
    faults: List<DevConsoleManager.ComponentFault>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val authLogs = logs.filter { entry ->
        entry.category == DevConsoleManager.LogCategory.AUTH ||
            entry.tag.contains("Spotify", ignoreCase = true) ||
            entry.tag.contains("Login", ignoreCase = true) ||
            entry.level == DevConsoleManager.LogLevel.ERROR
    }.reversed().take(60)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(LOGIN_CARD_DARK),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = "Auth Diagnostics",
                    tint = Color(LOGIN_SPOTIFY_GREEN),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Login Event Logs (${authLogs.size})", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 300.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0A0C10))
                    .border(1.dp, Color(0xFF2B2D3A), RoundedCornerShape(12.dp))
                    .padding(10.dp),
            ) {
                if (authLogs.isEmpty()) {
                    Text(
                        "No login events yet.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(authLogs, key = { idx, item -> "${item.timestamp}_${item.tag}_$idx" }) { _, entry ->
                            val tint = when (entry.level) {
                                DevConsoleManager.LogLevel.ERROR,
                                DevConsoleManager.LogLevel.COMPONENT_FAULT -> Color(0xFFFF6B6B)
                                DevConsoleManager.LogLevel.WARNING -> Color(0xFFFFB74D)
                                else -> Color(0xFF81C784)
                            }
                            Text(
                                text = redactSecrets("${entry.timestamp} [${entry.tag}] ${entry.message}"),
                                color = tint,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val text = buildString {
                        if (faults.isNotEmpty()) {
                            appendLine("=== FAULTS ===")
                            faults.takeLast(8).forEach { f ->
                                appendLine(redactSecrets("[${f.component}] ${f.reason} | ${f.autoFixHint}"))
                            }
                            appendLine()
                        }
                        appendLine("=== AUTH EVENTS ===")
                        authLogs.forEach { entry ->
                            appendLine(redactSecrets("${entry.timestamp} [${entry.level}] ${entry.tag}: ${entry.message}"))
                        }
                    }
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("SpotUIAuthLogs", text))
                    Toast.makeText(context, "Copied auth logs", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.testTag("copy_login_logs_button"),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(LOGIN_SPOTIFY_GREEN), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Copy", color = Color(LOGIN_SPOTIFY_GREEN))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.White)
            }
        },
    )
}
