package com.metrolist.innertube

import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.network.AuthSessionStore
import com.metrolist.innertube.network.DnsOverHttpsPreset
import com.metrolist.innertube.network.PlaybackAuthState
import kotlinx.coroutines.flow.StateFlow

object PlayerClient {
    val authStateFlow: StateFlow<PlaybackAuthState>
        get() = AuthSessionStore.authStateFlow

    fun currentPlaybackAuthState(): PlaybackAuthState = AuthSessionStore.currentAuthState()

    fun setPoTokens(
        poToken: String? = null,
        poTokenGvs: String? = null,
        poTokenPlayer: String? = null,
        webClientPoTokenEnabled: Boolean? = null,
    ): PlaybackAuthState =
        AuthSessionStore.updateAuthState { current ->
            current.copy(
                poToken = poToken ?: current.poToken,
                poTokenGvs = poTokenGvs ?: current.poTokenGvs,
                poTokenPlayer = poTokenPlayer ?: current.poTokenPlayer,
                webClientPoTokenEnabled = webClientPoTokenEnabled ?: current.webClientPoTokenEnabled,
            )
        }

    fun setWebClientPoTokenEnabled(enabled: Boolean): PlaybackAuthState =
        AuthSessionStore.updateAuthState { it.copy(webClientPoTokenEnabled = enabled) }

    fun setDnsPreset(preset: DnsOverHttpsPreset) {
        AuthSessionStore.dnsPreset = preset
    }

    fun appendGvsPoToken(
        url: String,
        client: YouTubeClient? = null,
    ): String = AuthSessionStore.appendGvsPoToken(url, client)
}
