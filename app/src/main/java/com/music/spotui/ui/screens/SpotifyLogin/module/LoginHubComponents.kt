package com.music.spotui.ui.screens.SpotifyLogin.module

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.spotui.ui.screens.SpotifyLogin.helper.AuthEcosystemStatus
import com.music.spotui.ui.screens.SpotifyLogin.helper.CookieValidationResult
import com.music.spotui.ui.screens.SpotifyLogin.helper.LOGIN_CARD_DARK
import com.music.spotui.ui.screens.SpotifyLogin.helper.LOGIN_SPOTIFY_GREEN
import com.music.spotui.ui.screens.SpotifyLogin.helper.LOGIN_TEXT_SECONDARY
import com.music.spotui.ui.screens.SpotifyLogin.helper.LoginSessionInspector
import com.music.spotui.ui.screens.SpotifyLogin.helper.looksLikeSpotifyCookiePayload
import com.music.spotui.ui.screens.SpotifyLogin.helper.openSpotifyInChromeCustomTab
import com.music.spotui.ui.screens.SpotifyLogin.page.DeezerLoginActivity
import com.music.spotui.ui.screens.SpotifyLogin.page.LocalLoginServerState
import com.music.spotui.ui.screens.SpotifyLogin.page.YouTubeMusicWebActivity

@Composable
internal fun AuthEcosystemStatusBanner(
    status: AuthEcosystemStatus,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(LOGIN_CARD_DARK)),
        border = BorderStroke(1.dp, Color(0xFF283146)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Connected Accounts",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusPill(
                    label = if (status.spotifyCookiePresent) "Spotify Cookie" else "Spotify Off",
                    active = status.spotifyCookiePresent,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(
                    label = if (status.oauthSignedIn) "OAuth PKCE" else "PKCE Ready",
                    active = status.oauthSignedIn,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusPill(
                    label = if (status.youtubeLoggedIn) "YT Music" else "YT Guest",
                    active = status.youtubeLoggedIn,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(
                    label = if (status.deezerArlSet) "Deezer HiFi" else "Deezer Off",
                    active = status.deezerArlSet,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (active) Color(0xFF143521) else Color(0xFF1C2230),
        border = BorderStroke(1.dp, if (active) Color(LOGIN_SPOTIFY_GREEN) else Color(0xFF2E384D)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (active) Color(LOGIN_SPOTIFY_GREEN) else Color(0xFF6B748B),
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                color = if (active) Color(0xFFE7FFF0) else Color(LOGIN_TEXT_SECONDARY),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
internal fun LocalUrlLoginCard(
    context: Context,
    serverState: LocalLoginServerState,
    onStartServer: (allowLan: Boolean) -> Unit,
    onStopServer: () -> Unit,
    onOpenLocalUrl: (String) -> Unit,
) {
    var allowLan by remember(serverState.allowLanAccess) { mutableStateOf(serverState.allowLanAccess) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(LOGIN_CARD_DARK)),
        border = BorderStroke(1.dp, Color(0xFF2A364E)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Http,
                    contentDescription = "Local URL Login Server",
                    tint = Color(LOGIN_SPOTIFY_GREEN),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Local URL Login Portal",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    Text(
                        text = "Starts an encrypted-local HTTP portal on your phone so you can log in from Chrome or a desktop browser on Wi-Fi.",
                        color = Color(LOGIN_TEXT_SECONDARY),
                        fontSize = 12.sp,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = Color(0xFF7FA6FF),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Expose on local Wi-Fi (for PC login)",
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                }
                Switch(
                    checked = allowLan,
                    onCheckedChange = { checked ->
                        allowLan = checked
                        if (serverState.isRunning) onStartServer(checked)
                    },
                    modifier = Modifier.testTag("local_server_lan_switch"),
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(LOGIN_SPOTIFY_GREEN)),
                )
            }

            if (serverState.isRunning) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0B0F17),
                    border = BorderStroke(1.dp, Color(0xFF263147)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "LOCAL URL",
                            color = Color(LOGIN_SPOTIFY_GREEN),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = serverState.loopbackUrl,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        )
                        serverState.lanUrl?.let { lan ->
                            Text(
                                text = "LAN URL: $lan",
                                color = Color(0xFF93B4FF),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                            )
                        }
                        Text(
                            text = serverState.lastEvent,
                            color = Color(LOGIN_TEXT_SECONDARY),
                            fontSize = 11.sp,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onOpenLocalUrl(serverState.loopbackUrl) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("local_server_open_browser"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(LOGIN_SPOTIFY_GREEN),
                            contentColor = Color.Black,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open Page", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { copyTextToClipboard(context, "SpotUI Login URL", serverState.lanUrl ?: serverState.loopbackUrl) },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("local_server_copy_url"),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Local URL", modifier = Modifier.size(18.dp))
                    }
                    OutlinedButton(
                        onClick = onStopServer,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("local_server_stop_button"),
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop Server", tint = Color(0xFFFF7A85))
                    }
                }
            } else {
                Button(
                    onClick = { onStartServer(allowLan) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("local_server_start_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(LOGIN_SPOTIFY_GREEN),
                        contentColor = Color.Black,
                    ),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Start Local Login Page", fontWeight = FontWeight.Bold)
                }
            }

            serverState.lastError?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
internal fun ChromeCustomTabAuthCard(
    context: Context,
    onPasteFromClipboard: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(LOGIN_CARD_DARK)),
        border = BorderStroke(1.dp, Color(0xFF283146)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Chrome Custom Tab Login",
                    tint = Color(LOGIN_SPOTIFY_GREEN),
                )
                Column {
                    Text(
                        text = "Browser Login (Passes Google/Apple SSO)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                    Text(
                        text = "Opens Chrome Custom Tabs where passkeys, Google Sign-In, and reCAPTCHA succeed reliably.",
                        color = Color(LOGIN_TEXT_SECONDARY),
                        fontSize = 12.sp,
                    )
                }
            }

            Button(
                onClick = { openSpotifyInChromeCustomTab(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_chrome_custom_tab_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(LOGIN_SPOTIFY_GREEN),
                    contentColor = Color.Black,
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open Spotify in Chrome Tab", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = {
                    val clipText = readClipboardText(context)
                    if (!clipText.isNullOrBlank()) {
                        onPasteFromClipboard(clipText)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("smart_paste_cookie_button"),
            ) {
                Icon(Icons.Default.Key, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                val hasClipCookie = readClipboardText(context)?.let(::looksLikeSpotifyCookiePayload) == true
                Text(
                    if (hasClipCookie) "Detect & Import Cookie from Clipboard" else "Paste Cookie from Clipboard",
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
internal fun MultiPlatformTokensCard(
    context: Context,
    onSubmitSpotifyCookie: (String) -> Unit,
    onSaveYoutubeCookie: (String) -> Unit,
    onSaveDeezerArl: (String) -> Unit,
) {
    var spotifyInput by remember { mutableStateOf("") }
    var ytInput by remember { mutableStateOf("") }
    var deezerInput by remember { mutableStateOf("") }
    val validation = remember(spotifyInput) { LoginSessionInspector.validateSpotifySpDc(spotifyInput) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(LOGIN_CARD_DARK)),
            border = BorderStroke(1.dp, Color(0xFF283146)),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Spotify Cookie / JSON Importer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                OutlinedTextField(
                    value = spotifyInput,
                    onValueChange = { spotifyInput = it },
                    label = { Text("sp_dc value or Cookie-Editor JSON") },
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(LOGIN_SPOTIFY_GREEN),
                        unfocusedBorderColor = Color(0xFF39435B),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("spotify_cookie_text_field"),
                )
                Text(
                    text = LoginSessionInspector.validationHint(validation),
                    color = if (validation == CookieValidationResult.VALID) Color(LOGIN_SPOTIFY_GREEN) else Color(LOGIN_TEXT_SECONDARY),
                    fontSize = 11.sp,
                )
                Button(
                    onClick = { onSubmitSpotifyCookie(spotifyInput) },
                    enabled = validation == CookieValidationResult.VALID,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("submit_spotify_cookie_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(LOGIN_SPOTIFY_GREEN),
                        contentColor = Color.Black,
                    ),
                ) {
                    Text("Sign In with Cookie", fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(LOGIN_CARD_DARK)),
            border = BorderStroke(1.dp, Color(0xFF283146)),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("YouTube Music Session", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(context, YouTubeMusicWebActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text("Open YTM Sign-In", fontSize = 12.sp)
                    }
                }
                OutlinedTextField(
                    value = ytInput,
                    onValueChange = { ytInput = it },
                    label = { Text("Paste YTM Cookie Header (Optional)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF4E45),
                        unfocusedBorderColor = Color(0xFF39435B),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onSaveYoutubeCookie(ytInput); ytInput = "" },
                    enabled = ytInput.trim().length >= 16,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Save YTM Cookie")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(LOGIN_CARD_DARK)),
            border = BorderStroke(1.dp, Color(0xFF283146)),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Deezer Lossless ARL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(context, DeezerLoginActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text("Open Deezer Login", fontSize = 12.sp)
                    }
                }
                OutlinedTextField(
                    value = deezerInput,
                    onValueChange = { deezerInput = it },
                    label = { Text("Deezer ARL Token") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFA238FF),
                        unfocusedBorderColor = Color(0xFF39435B),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onSaveDeezerArl(deezerInput); deezerInput = "" },
                    enabled = deezerInput.trim().length >= 32,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text("Save Deezer ARL")
                }
            }
        }
    }
}

private fun readClipboardText(context: Context): String? = runCatching {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
}.getOrNull()

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
