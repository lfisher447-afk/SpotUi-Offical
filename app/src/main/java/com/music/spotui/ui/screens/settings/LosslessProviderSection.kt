package com.music.spotui.ui.screens.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.spotify.SpotiFlac
import com.music.spotui.data.preferences.LosslessProviderRoute
import com.music.spotui.data.preferences.ProviderRoutingPreferences
import com.music.spotui.data.preferences.getCustomLosslessMirrorUrl
import com.music.spotui.data.preferences.getDeezerArl
import com.music.spotui.data.preferences.getDeezerTier
import com.music.spotui.data.preferences.setCustomLosslessMirrorUrl
import com.music.spotui.data.preferences.setDeezerArl
import com.music.spotui.data.preferences.setDeezerTier
import com.music.spotui.deezer.DeezerSession
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.screens.DeezerLoginActivity
import com.music.spotui.ui.theme.AppPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Collections
import java.util.concurrent.TimeUnit

@Composable
fun LosslessProviderSection(
    onOpenProviderStatusDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var providerOrder by remember { mutableStateOf(ProviderRoutingPreferences.losslessOrder(context)) }
    var enabledProviders by remember { mutableStateOf(ProviderRoutingPreferences.enabledProviders(context)) }
    var soloPrimary by remember { mutableStateOf(ProviderRoutingPreferences.soloProvider(context)) }
    var strictFullLength by remember { mutableStateOf(ProviderRoutingPreferences.preferFullLength(context)) }

    var deezerArlInput by remember { mutableStateOf(getDeezerArl(context)) }
    var deezerTier by remember { mutableStateOf(getDeezerTier(context)) }
    var deezerStatus by remember { mutableStateOf("") }
    var deezerBusy by remember { mutableStateOf(false) }

    var customMirrorInput by remember { mutableStateOf(getCustomLosslessMirrorUrl(context)) }
    var mirrorPingStatus by remember { mutableStateOf("") }
    var mirrorPingBusy by remember { mutableStateOf(false) }

    var mirrorHealthSummary by remember { mutableStateOf("Probing mirrors…") }

    val deezerWebLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val freshArl = getDeezerArl(context)
        deezerArlInput = freshArl
        if (freshArl.isNotBlank()) {
            deezerBusy = true
            scope.launch {
                val session = withContext(Dispatchers.IO) {
                    DeezerSession.setArl(freshArl)
                    DeezerSession.authorize(forceRefresh = true)
                }
                deezerBusy = false
                if (session.authorized) {
                    val label = when (session.entitledQuality) {
                        DeezerSession.QUALITY_FLAC -> "HiFi FLAC"
                        DeezerSession.QUALITY_MP3_320 -> "Premium 320"
                        else -> "Free 128"
                    }
                    setDeezerTier(context, label)
                    deezerTier = label
                    deezerStatus = "Linked ${session.userName} ($label)"
                } else {
                    deezerStatus = session.detail
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val statuses = withContext(Dispatchers.IO) { SpotiFlac.getProviderStatuses() }
        val up = statuses.count { it.isUp && !it.isCooldown }
        mirrorHealthSummary = "$up/${statuses.size} public mirrors online · Tap to inspect"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Mirror Health Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onOpenProviderStatusDialog)
                .testTag("lossless_status_inspect_button")
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Lossless Mirror Status", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(mirrorHealthSummary, color = Color(0xFFB3B3B3), fontSize = 12.sp)
            }
            IconButton(
                onClick = {
                    scope.launch {
                        SpotiFlac.clearCooldowns()
                        SpotiFlac.clearStatusCache()
                        val statuses = withContext(Dispatchers.IO) { SpotiFlac.getProviderStatuses() }
                        val up = statuses.count { it.isUp && !it.isCooldown }
                        mirrorHealthSummary = "$up/${statuses.size} online (cooldowns reset)"
                        Toast.makeText(context, "Cleared provider cooldowns", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.testTag("clear_provider_cooldowns_button"),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Clear Cooldowns", tint = AppPalette)
            }
        }

        // Priority Controller Container — iterates saved `providerOrder` directly!
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1A1A20))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Lossless Provider Priority Controller", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "Resolver strictly executes enabled providers top-to-bottom. Tap ★ to promote a source to #1 Primary.",
                color = Color(0xFFB3B3B3),
                fontSize = 11.sp,
            )

            val routesById = remember { LosslessProviderRoute.entries.associateBy { it.id } }
            providerOrder.forEachIndexed { index, id ->
                val route = routesById[id] ?: return@forEachIndexed
                val isEnabled = id in enabledProviders
                val isPrimary = index == 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isPrimary) AppPalette.copy(alpha = 0.14f)
                            else Color(0xFF23232B),
                        )
                        .border(
                            width = 1.dp,
                            color = if (isPrimary) AppPalette.copy(alpha = 0.6f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (isPrimary) AppPalette else Color(0xFF32323E)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = if (isPrimary) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(route.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(route.detail, color = Color(0xFFA0A7B8), fontSize = 10.sp, maxLines = 2)
                    }
                    IconButton(
                        onClick = {
                            val updated = ProviderRoutingPreferences.setPrimaryProvider(context, id)
                            providerOrder = updated
                            enabledProviders = ProviderRoutingPreferences.enabledProviders(context)
                        },
                        enabled = !isPrimary,
                        modifier = Modifier.testTag("provider_primary_${id}_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Make ${route.label} primary",
                            tint = if (isPrimary) AppPalette else Color(0xFF888C98),
                        )
                    }
                    IconButton(
                        onClick = {
                            val reordered = providerOrder.toMutableList().also {
                                if (index > 0) Collections.swap(it, index, index - 1)
                            }
                            providerOrder = reordered
                            ProviderRoutingPreferences.setLosslessOrder(context, reordered)
                        },
                        enabled = index > 0,
                        modifier = Modifier.testTag("provider_up_${id}_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Move ${route.label} up",
                            tint = if (index > 0) Color.White else Color(0xFF4C4F59),
                        )
                    }
                    IconButton(
                        onClick = {
                            val reordered = providerOrder.toMutableList().also {
                                if (index < it.lastIndex) Collections.swap(it, index, index + 1)
                            }
                            providerOrder = reordered
                            ProviderRoutingPreferences.setLosslessOrder(context, reordered)
                        },
                        enabled = index < providerOrder.lastIndex,
                        modifier = Modifier.testTag("provider_down_${id}_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Move ${route.label} down",
                            tint = if (index < providerOrder.lastIndex) Color.White else Color(0xFF4C4F59),
                        )
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { checked ->
                            val updated = ProviderRoutingPreferences.setProviderEnabled(context, id, checked)
                            enabledProviders = updated
                        },
                        modifier = Modifier.testTag("provider_enable_${id}_switch"),
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppPalette),
                    )
                }
            }

            // Solo Primary Only & Full-Length Gate
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Strict Solo Primary Mode", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Only use the #1 ranked provider above (never fall through to lower-ranked mirrors)", color = Color(0xFF9FA9BC), fontSize = 11.sp)
                }
                Switch(
                    checked = soloPrimary,
                    onCheckedChange = {
                        soloPrimary = it
                        ProviderRoutingPreferences.setSoloProvider(context, it)
                    },
                    modifier = Modifier.testTag("solo_primary_provider_switch"),
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppPalette),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Reject preview-length lossless (<45s)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Discards 30-second sample snippets automatically before fallback", color = Color(0xFF9FA9BC), fontSize = 11.sp)
                }
                Switch(
                    checked = strictFullLength,
                    onCheckedChange = {
                        strictFullLength = it
                        ProviderRoutingPreferences.setPreferFullLength(context, it)
                    },
                    modifier = Modifier.testTag("reject_preview_lossless_switch"),
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppPalette),
                )
            }
        }

        // Deezer Account & Direct HiFi ARL Decryption Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1A1A20))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Deezer HiFi Account (Direct ARL)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (deezerArlInput.isNotBlank()) {
                            "Linked • Tier: ${deezerTier.ifBlank { "Configured (tap Validate)" }}"
                        } else {
                            "Sign in or paste an ARL cookie for encrypted direct CDN FLAC/320"
                        },
                        color = if (deezerArlInput.isNotBlank()) Color(0xFF81C784) else Color(0xFFB3B3B3),
                        fontSize = 11.sp,
                    )
                }
                if (deezerArlInput.isNotBlank()) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Deezer Linked", tint = Color(0xFF81C784))
                }
            }

            OutlinedTextField(
                value = deezerArlInput,
                onValueChange = { deezerArlInput = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                placeholder = { Text("Paste 192-char Deezer ARL token…", fontSize = 12.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("deezer_arl_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppPalette,
                    unfocusedBorderColor = Color(0xFF34343F),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        deezerWebLauncher.launch(Intent(context, DeezerLoginActivity::class.java))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppPalette, contentColor = Color.Black),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("deezer_webview_login_button"),
                ) {
                    Text("Web Sign In", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    enabled = !deezerBusy && deezerArlInput.isNotBlank(),
                    onClick = {
                        deezerBusy = true
                        setDeezerArl(context, deezerArlInput)
                        scope.launch {
                            val status = withContext(Dispatchers.IO) {
                                DeezerSession.setArl(deezerArlInput)
                                val direct = DeezerSession.authorize(forceRefresh = true)
                                val api = SpotiFlac.validateDeezerArl(deezerArlInput)
                                direct to api
                            }
                            deezerBusy = false
                            val (direct, api) = status
                            if (direct.authorized || api.valid) {
                                val label = when (direct.entitledQuality) {
                                    DeezerSession.QUALITY_FLAC -> "HiFi FLAC"
                                    DeezerSession.QUALITY_MP3_320 -> "Premium 320"
                                    else -> if (api.losslessCapable) "HiFi FLAC" else "Free 128"
                                }
                                setDeezerTier(context, label)
                                deezerTier = label
                                val name = direct.userName.ifBlank { api.userName }.ifBlank { "Linked" }
                                deezerStatus = "Verified: $name ($label)"
                            } else {
                                deezerStatus = direct.detail.ifBlank { api.detail }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF282A36), contentColor = Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("deezer_validate_button"),
                ) {
                    Text(if (deezerBusy) "Checking…" else "Save & Verify", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                if (deezerArlInput.isNotBlank()) {
                    TextButton(
                        onClick = {
                            deezerArlInput = ""
                            deezerTier = ""
                            setDeezerArl(context, "")
                            deezerStatus = "Deezer ARL cleared."
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("deezer_signout_button"),
                    ) {
                        Text("Clear", color = Color(0xFFFF8B94), fontSize = 12.sp)
                    }
                }
            }
            if (deezerStatus.isNotBlank()) {
                Text(deezerStatus, color = AppPalette, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Custom Self-Hosted Mirror Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1A1A20))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Custom Lossless Mirror (Optional)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("Tried first before public Tidal/Qobuz/Amazon community mirrors.", color = Color(0xFFB3B3B3), fontSize = 11.sp)
            OutlinedTextField(
                value = customMirrorInput,
                onValueChange = { customMirrorInput = it },
                singleLine = true,
                placeholder = { Text("https://mirror.example.org", fontSize = 12.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_mirror_url_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppPalette,
                    unfocusedBorderColor = Color(0xFF34343F),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !mirrorPingBusy,
                    onClick = {
                        setCustomLosslessMirrorUrl(context, customMirrorInput)
                        val clean = getCustomLosslessMirrorUrl(context)
                        if (clean.isBlank()) {
                            mirrorPingStatus = "Custom mirror cleared."
                            return@TextButton
                        }
                        mirrorPingBusy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                val start = System.currentTimeMillis()
                                runCatching {
                                    val c = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).build()
                                    c.newCall(Request.Builder().url(clean).get().build()).execute().use { r ->
                                        "HTTP ${r.code} in ${System.currentTimeMillis() - start}ms"
                                    }
                                }.getOrElse { "Ping failed: ${it.message}" }
                            }
                            mirrorPingBusy = false
                            mirrorPingStatus = "Saved • $result"
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("custom_mirror_save_ping_button"),
                ) {
                    Text(if (mirrorPingBusy) "Pinging…" else "Save & Ping Mirror", color = AppPalette, fontWeight = FontWeight.Bold)
                }
                if (SongPlayer.isPrepared()) {
                    TextButton(
                        onClick = { SongPlayer.restartCurrentTrackForQualityChange(context) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("re_resolve_current_track_button"),
                    ) {
                        Text("Re-resolve Now", color = Color.White)
                    }
                }
            }
            if (mirrorPingStatus.isNotBlank()) {
                Text(mirrorPingStatus, color = Color(0xFFD0D6E0), fontSize = 11.sp)
            }
        }
    }
}
