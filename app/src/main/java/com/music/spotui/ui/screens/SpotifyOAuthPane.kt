package com.music.spotui.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.spotui.ui.screens.SpotifyLogin.helper.LOGIN_SPOTIFY_GREEN
import com.music.spotui.ui.screens.SpotifyLogin.helper.LOGIN_TEXT_SECONDARY

/**
 * Official Spotify OAuth (Authorization Code + PKCE) sign-in. Needs the user's own Client ID from
 * developer.spotify.com; no cookie or WebView is involved.
 */
@Composable
internal fun OAuthSignInPane(
    isProcessing: Boolean,
    onSignIn: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var clientId by remember { mutableStateOf(SpotifyOAuth.clientId(context)) }
    var signedIn by remember { mutableStateOf(SpotifyOAuth.isSignedIn(context)) }
    val valid = SpotifyOAuth.isValidClientId(clientId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Official Spotify Sign-In (OAuth)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onClose) {
                Text("Back", color = Color(LOGIN_SPOTIFY_GREEN))
            }
        }

        Text(
            text = "Uses Spotify's official consent page in Chrome — no cookies, no embedded WebView. " +
                "Create an app at developer.spotify.com/dashboard, add the redirect URI below, " +
                "add your Spotify account under User Management, then paste the app's Client ID.",
            color = Color(LOGIN_TEXT_SECONDARY),
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )

        OutlinedTextField(
            value = clientId,
            onValueChange = { input ->
                clientId = input.trim()
                if (SpotifyOAuth.isValidClientId(clientId)) SpotifyOAuth.saveClientId(context, clientId)
            },
            label = { Text("Spotify Client ID", color = Color(LOGIN_TEXT_SECONDARY)) },
            isError = clientId.isNotEmpty() && !valid,
            supportingText = {
                if (clientId.isNotEmpty() && !valid) {
                    Text("A Client ID is 32 hex characters.", color = Color(0xFFFF9EA6), fontSize = 11.sp)
                }
            },
            singleLine = true,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(LOGIN_SPOTIFY_GREEN),
                unfocusedBorderColor = Color(0xFF34384A),
                focusedContainerColor = Color(0xFF10121A),
                unfocusedContainerColor = Color(0xFF10121A),
            ),
            modifier = Modifier.fillMaxWidth().testTag("oauth_client_id_input"),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Redirect URI to register", color = Color(LOGIN_TEXT_SECONDARY), fontSize = 11.sp)
                Text(
                    SpotifyOAuth.REGISTERED_REDIRECT_URI,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            }
            OutlinedButton(
                onClick = { copyToClipboard(context, "Spotify redirect URI", SpotifyOAuth.REGISTERED_REDIRECT_URI) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy redirect URI", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }

        if (isProcessing) {
            OutlinedButton(
                onClick = { SpotifyOAuth.cancelLogin() },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("oauth_cancel_button"),
            ) {
                Text("Cancel sign-in", color = Color.White)
            }
        } else {
            Button(
                enabled = valid,
                onClick = onSignIn,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(LOGIN_SPOTIFY_GREEN), contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("oauth_sign_in_button"),
            ) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (signedIn) "Sign in again & re-import" else "Sign in with Spotify", fontWeight = FontWeight.ExtraBold)
            }
        }

        if (signedIn && !isProcessing) {
            TextButton(
                onClick = {
                    SpotifyOAuth.signOut(context)
                    signedIn = false
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Sign out of official Spotify session", color = Color(0xFFFFB74D))
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
