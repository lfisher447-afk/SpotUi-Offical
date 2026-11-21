package com.music.spotui.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.SpotifyAuth
import com.music.spotui.audio.UiSoundEngine
import com.music.spotui.data.api.SpotifySession
import com.music.spotui.util.DevConsoleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import timber.log.Timber

internal const val LOGIN_SPOTIFY_GREEN = 0xFF1ED760
internal const val LOGIN_BACKGROUND_DARK = 0xFF121212
internal const val LOGIN_CARD_DARK = 0xFF1E1E1E
internal const val LOGIN_TEXT_SECONDARY = 0xFFB3B3B3

internal enum class LoginTab {
    NATIVE,
    WEB,
    COOKIE,
}

internal enum class WebUaMode(val shortLabel: String, val detailLabel: String) {
    ANDROID_CHROME("Android Chrome", "Native Chromium UA (no '; wv') — matches device hardware & stops false password rejects"),
    SYSTEM_WEBVIEW("System Default", "Unmodified Android System WebView UA"),
    DESKTOP_CHROME("Desktop Chrome", "Linux Chrome 131 UA (fallback if Spotify forces desktop flow)"),
}

internal data class PendingCredentialHandoff(
    val emailOrUsername: String,
    val password: String = "",
    val preferEmailCode: Boolean = false,
)

internal fun resolveUserAgent(context: Context, mode: WebUaMode): String {
    val sysUa = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull().orEmpty()
    return when (mode) {
        WebUaMode.ANDROID_CHROME -> {
            if (sysUa.isNotBlank()) {
                // Removing "; wv" makes Spotify & Google OAuth treat WebView as authentic mobile Chrome
                // while keeping the true Android OS, device build, GPU, and touch fingerprint intact!
                sysUa
                    .replace("; wv)", ")")
                    .replace("; wv;", ";")
                    .replace("Version/4.0 ", "")
            } else {
                "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.200 Mobile Safari/537.36"
            }
        }
        WebUaMode.SYSTEM_WEBVIEW -> sysUa.ifBlank {
            "Mozilla/5.0 (Linux; Android 14; K; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0.6778.200 Mobile Safari/537.36"
        }
        WebUaMode.DESKTOP_CHROME -> {
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        }
    }
}

internal fun maskEmailForLog(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return "<empty>"
    val at = trimmed.indexOf('@')
    return if (at > 1) {
        "${trimmed.take(2)}***@${trimmed.substring(at + 1)}"
    } else if (trimmed.length > 2) {
        "${trimmed.take(2)}***"
    } else {
        "***"
    }
}

private val looseCookieJson = Json { isLenient = true; ignoreUnknownKeys = true }

/**
 * Parses `sp_dc` and optional `sp_key` out of:
 * - raw sp_dc value (`AQD...`)
 * - semicolon cookie strings (`Cookie: ... sp_dc=AQD...; sp_key=...`)
 * - Cookie-Editor JSON arrays (`[{"name":"sp_dc","value":"..."}]`) or JSON objects (`{"sp_dc":"..."}`)
 * - Netscape cookies.txt lines (`...\tsp_dc\tAQD...`)
 */
internal fun parseSpotifyCookiesFromText(raw: String): Pair<String, String> {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return "" to ""

    // 1. JSON array or object (e.g. Cookie-Editor v3 export)
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

    // 2. HTTP header / multi-cookie line (find sp_dc= anywhere inside)
    val spDcRegex = Regex("(?:^|[;\\s,])sp_dc\\s*=\\s*\"?([^;\"\\r\\n]+)\"?", RegexOption.IGNORE_CASE)
    val spKeyRegex = Regex("(?:^|[;\\s,])sp_key\\s*=\\s*\"?([^;\"\\r\\n]+)\"?", RegexOption.IGNORE_CASE)
    val dcMatch = spDcRegex.find(trimmed)?.groupValues?.getOrNull(1)?.trim()
    if (!dcMatch.isNullOrBlank()) {
        val keyMatch = spKeyRegex.find(trimmed)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        return dcMatch to keyMatch
    }

    // 3. Netscape cookies.txt line (`domain\tflag\tpath\tsecure\texp\tname\tvalue`)
    for (line in trimmed.lineSequence()) {
        val cols = line.split('\t')
        if (cols.size >= 7 && cols[5].trim().equals("sp_dc", ignoreCase = true)) {
            val valDc = cols[6].trim()
            if (valDc.isNotBlank()) return valDc to ""
        }
    }

    // 4. Plain single token string
    val cleaned = trimmed.removePrefix("sp_dc=").split(";").first().trim().trim('"')
    return cleaned to ""
}

internal data class CookieSweepResult(
    val spDc: String?,
    val spKey: String,
    val totalCookieKeysFound: Int,
)

/**
 * Checks all relevant Spotify domains in [CookieManager] for `sp_dc` & `sp_key`.
 */
internal fun sweepSpotifyCookies(currentUrl: String? = null): CookieSweepResult {
    val manager = runCatching { CookieManager.getInstance() }.getOrNull()
        ?: return CookieSweepResult(null, "", 0)

    val urls = buildList {
        currentUrl?.takeIf { it.isNotBlank() && it.contains("spotify", ignoreCase = true) }?.let { add(it) }
        add("https://open.spotify.com/")
        add("https://accounts.spotify.com/")
        add("https://www.spotify.com/")
        add("https://spotify.com/")
        add(".spotify.com")
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

internal fun resetSpotifyWebCookies(onDone: () -> Unit) {
    runCatching {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies {
            runCatching { cm.flush() }
            DevConsoleManager.logInfo("SpotifyLogin", "Cleared WebView session & challenge cookies")
            onDone()
        }
    }.onFailure { err ->
        DevConsoleManager.logWarning("SpotifyLogin", "Could not reset WebView cookies: ${err.message}")
        onDone()
    }
}

/**
 * React-safe credential auto-fill script injected after user taps Log In from the Native tab.
 * Never prints or logs the password inside JS console.
 */
internal fun buildCredentialAutofillJs(
    emailOrUsername: String,
    password: String?,
    preferEmailCode: Boolean,
): String {
    val qEmail = JSONObject.quote(emailOrUsername.trim())
    val qPass = JSONObject.quote(password.orEmpty())
    val hasPass = !password.isNullOrBlank() && !preferEmailCode
    val wantCode = preferEmailCode
    return """
        (function(){
          if (window.__spotuiAutofillStarted) return 'already-started';
          window.__spotuiAutofillStarted = true;
          var email = $qEmail;
          var pass = $qPass;
          var hasPass = $hasPass;
          var wantCode = $wantCode;

          function setReactVal(el, val){
            if (!el) return false;
            try {
              var desc = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
              if (desc && desc.set) desc.set.call(el, val);
              else el.value = val;
              el.dispatchEvent(new Event('input', {bubbles: true}));
              el.dispatchEvent(new Event('change', {bubbles: true}));
              return true;
            } catch(e){ return false; }
          }

          var attempts = 0;
          var emailFilled = false;
          var passFilled = false;
          var timer = setInterval(function(){
            attempts++;
            if (attempts > 45) { clearInterval(timer); return; }

            var userEl = document.querySelector('#login-username, input[name="username"], input[autocomplete="username"], input[data-testid="login-username"]');
            if (userEl && !emailFilled && !userEl.value) {
              setReactVal(userEl, email);
              emailFilled = true;
            }

            var passEl = document.querySelector('#login-password, input[name="password"], input[type="password"], input[data-testid="login-password"]');

            if (wantCode && emailFilled && !passEl) {
              var btns = Array.prototype.slice.call(document.querySelectorAll('button'));
              var codeBtn = btns.find(function(b){
                var t = (b.innerText || b.textContent || '').toLowerCase();
                return t.indexOf('without password') > -1 || t.indexOf('send code') > -1 || t.indexOf('continue') > -1;
              });
              if (codeBtn && !window.__spotuiClickedContinue) {
                window.__spotuiClickedContinue = true;
                codeBtn.click();
              }
              return;
            }

            if (hasPass && emailFilled && !passEl && !window.__spotuiClickedContinue) {
              var contBtn = document.querySelector('button[data-testid="login-button"], button[type="submit"]');
              if (contBtn) {
                window.__spotuiClickedContinue = true;
                contBtn.click();
              }
              return;
            }

            if (hasPass && !passEl && window.__spotuiClickedContinue && !window.__spotuiClickedPwMode) {
              var altBtns = Array.prototype.slice.call(document.querySelectorAll('button, a'));
              var usePwBtn = altBtns.find(function(b){
                var t = (b.innerText || b.textContent || '').toLowerCase();
                return t.indexOf('log in with a password') > -1 || t.indexOf('use password') > -1 || t.indexOf('password instead') > -1;
              });
              if (usePwBtn) {
                window.__spotuiClickedPwMode = true;
                usePwBtn.click();
              }
              return;
            }

            if (hasPass && passEl && !passFilled) {
              setReactVal(passEl, pass);
              passFilled = true;
              setTimeout(function(){
                var loginBtn = document.querySelector('#login-button, button[data-testid="login-button"], button[type="submit"]');
                if (loginBtn) loginBtn.click();
                clearInterval(timer);
              }, 220);
            }
          }, 350);
          return 'started';
        })();
    """.trimIndent()
}

/**
 * Inspects visible DOM alerts / CAPTCHAs / OTP steps on accounts.spotify.com so real errors
 * shown inside the Spotify page are captured cleanly by DevConsoleManager.
 */
internal fun buildDomErrorProbeJs(): String = """
    (function(){
      try {
        var sels = [
          '[data-testid="login-error"]',
          '[data-encore-id="banner"][aria-live]',
          '[role="alert"]',
          '.alert-warning',
          '.alert-danger',
          'span[class*="Message"]',
          'p[class*="Error"]'
        ];
        var texts = [];
        sels.forEach(function(s){
          document.querySelectorAll(s).forEach(function(el){
            var t = (el.innerText || '').trim().replace(/\s+/g, ' ');
            if (t && t.length > 3 && t.length < 240 && texts.indexOf(t) === -1) {
              texts.push(t);
            }
          });
        });
        var bodyTxt = (document.body && document.body.innerText || '').toLowerCase();
        var hasCaptcha = !!document.querySelector('iframe[src*="recaptcha"], iframe[src*="arkose"], iframe[src*="funcaptcha"], iframe[src*="challenge"]');
        var hasOtpPrompt = bodyTxt.indexOf('enter the 6-digit code') > -1 || bodyTxt.indexOf('verification code') > -1;
        return JSON.stringify({
          alerts: texts,
          captcha: hasCaptcha,
          otpPrompt: hasOtpPrompt
        });
      } catch(e) {
        return '{}';
      }
    })();
""".trimIndent()

/**
 * Exchanges `sp_dc` for an authenticated access token and records detailed diagnostics in [DevConsoleManager].
 */
internal fun processLoginSession(
    context: Context,
    spDc: String,
    spKey: String = "",
    source: String = "Session",
    scope: CoroutineScope,
    setProcessing: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    setError: (Boolean) -> Unit,
    onComplete: (Boolean) -> Unit,
    onSuccess: () -> Unit,
) {
    setProcessing(true)
    setError(false)
    setStatus("Verifying Spotify session…")
    DevConsoleManager.logInfo(
        "SpotifyLogin",
        "Authenticating via $source (sp_dc len=${spDc.length}, sp_key=${if (spKey.isNotBlank()) "present" else "none"})",
    )

    scope.launch(Dispatchers.IO) {
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            val result = SpotifyAuth.fetchAccessToken(spDc, spKey)
            result.onSuccess { token ->
                SpotifySession.setSpDc(context, spDc)
                Spotify.accessToken = token.accessToken
                DevConsoleManager.logInfo(
                    "SpotifyLogin",
                    "Login succeeded via $source on attempt ${attempt + 1} (anonymous=${token.isAnonymous})",
                )
                withContext(Dispatchers.Main) {
                    UiSoundEngine.playToggleSound(context, true)
                    setStatus("Signed in successfully!")
                    setProcessing(false)
                    setError(false)
                    onComplete(true)
                }
                delay(280)
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
                return@launch
            }.onFailure { e ->
                lastError = e
                val msg = e.message ?: e.javaClass.simpleName
                Timber.e(e, "Spotify token fetch failed (attempt ${attempt + 1})")
                DevConsoleManager.logWarning(
                    "SpotifyLogin",
                    "Token exchange attempt ${attempt + 1}/3 failed ($source): $msg",
                )
                if (attempt < 2) delay(750)
            }
        }
        val finalMsg = lastError?.message ?: "Unable to verify Spotify session"
        DevConsoleManager.logError("SpotifyLogin", "Login verification failed ($source): $finalMsg", lastError)
        DevConsoleManager.reportComponentFault(
            component = "SpotifyLogin",
            action = "TokenVerification_$source",
            reason = finalMsg,
            autoFixHint = "Verify network connectivity, reset WebView cookies, or use Email Code sign-in.",
            exception = lastError,
        )
        withContext(Dispatchers.Main) {
            setStatus("Login failed: $finalMsg")
            setError(true)
            setProcessing(false)
            onComplete(false)
        }
    }
}

/**
 * In-screen live login diagnostic dialog showing real-time auth, WebView, HTTP, and token logs
 * from DevConsoleManager without leaving SpotifyLoginScreen.
 */
@Composable
internal fun LoginDiagnosticsDialog(
    logs: List<DevConsoleManager.LogEntry>,
    faults: List<DevConsoleManager.ComponentFault>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val authLogs = logs.filter { entry ->
        entry.tag.contains("Spotify", ignoreCase = true) ||
            entry.tag.contains("Login", ignoreCase = true) ||
            entry.tag.contains("Auth", ignoreCase = true) ||
            entry.tag.contains("WebView", ignoreCase = true) ||
            entry.level == DevConsoleManager.LogLevel.ERROR ||
            entry.level == DevConsoleManager.LogLevel.COMPONENT_FAULT
    }.reversed().take(60)

    val authFaults = faults.filter {
        it.component.contains("Spotify", ignoreCase = true) || it.component.contains("Login", ignoreCase = true)
    }.take(10)

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
                Column {
                    Text("Live Login Diagnostics", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${authFaults.count { !it.resolved }} faults · ${authLogs.size} auth events",
                        color = Color(LOGIN_TEXT_SECONDARY),
                        fontSize = 11.sp,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (authFaults.isNotEmpty()) {
                    Text("DETECTED AUTH FAULTS", color = Color(0xFFFF8B94), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    authFaults.take(3).forEach { fault ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF28181A))
                                .border(1.dp, Color(0x44FF5252), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                        ) {
                            Text("${fault.component} · ${fault.action}", color = Color(0xFFFFB4AB), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(fault.reason, color = Color.White, fontSize = 11.sp)
                            if (fault.autoFixHint.isNotBlank()) {
                                Text("Hint: ${fault.autoFixHint}", color = Color(0xFF81C784), fontSize = 10.sp)
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 300.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0B0C10))
                        .border(1.dp, Color(0xFF2B2D3A), RoundedCornerShape(10.dp))
                        .padding(8.dp),
                ) {
                    if (authLogs.isEmpty()) {
                        Text(
                            text = "No login events recorded yet. Interact with Native/Web login to see live telemetry.",
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
                                    DevConsoleManager.LogLevel.INFO -> Color(0xFF81C784)
                                    else -> Color(0xFFB0BEC5)
                                }
                                Text(
                                    text = "${entry.timestamp} [${entry.tag}] ${entry.message}",
                                    color = tint,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val text = buildString {
                        appendLine("=== SPOTUI AUTH DIAGNOSTICS ===")
                        authFaults.forEach { appendLine("FAULT [${it.action}]: ${it.reason} | Hint: ${it.autoFixHint}") }
                        appendLine("\n=== AUTH EVENTS ===")
                        authLogs.forEach { appendLine("${it.timestamp} [${it.level}] ${it.tag}: ${it.message}") }
                    }
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("SpotUIAuthLogs", text))
                    Toast.makeText(context, "Login logs copied", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.testTag("copy_login_logs_button"),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(LOGIN_SPOTIFY_GREEN), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Copy Logs", color = Color(LOGIN_SPOTIFY_GREEN))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.White)
            }
        },
    )
}
