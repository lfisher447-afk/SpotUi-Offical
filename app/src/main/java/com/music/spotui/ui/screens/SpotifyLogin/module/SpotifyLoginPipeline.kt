package com.music.spotui.ui.screens.SpotifyLogin.module

import android.content.Context
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.SpotifyAuth
import com.metrolist.spotify.models.SpotifyInternalToken
import com.music.spotui.audio.UiSoundEngine
import com.music.spotui.data.api.SpotifySession
import com.music.spotui.data.api.SpotifySync
import com.music.spotui.data.api.SpotifyTokenProvider
import com.music.spotui.util.DevConsoleManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

private const val LOGIN_TOKEN_ATTEMPTS = 3
private const val LOGIN_SYNC_TIMEOUT_MS = 120_000L
private const val LOGIN_SUCCESS_NAV_DELAY_MS = 380L
internal const val MIN_SP_DC_LENGTH = 16

/** The four visible steps of "WebView cookie in -> Spotify.kt armed -> library synced". */
internal enum class LoginStage(val label: String) {
    VERIFY("Cookie"),
    TOKEN("Session"),
    PROFILE("Profile"),
    LIBRARY("Library"),
}

internal sealed interface LoginOutcome {
    data class Success(
        val signInLabel: String,
        val accountName: String?,
        val syncDetail: String?,
        val syncWarning: String?,
    ) : LoginOutcome

    data class Failure(
        val stage: LoginStage,
        val message: String,
        val cause: Throwable?,
    ) : LoginOutcome
}

private object LoginPipelineScope {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

internal fun launchLoginTask(block: suspend CoroutineScope.() -> Unit): Job =
    LoginPipelineScope.scope.launch(block = block)

/**
 * Wires [Spotify.tokenRefresher] + [installSpotifyHashRecovery] onto the main [Spotify]
 * singleton so 401 token rotations and GraphQL persisted-query hashes heal transparently.
 */
fun installSpotifyTokenAutoRefresh(context: Context) {
    val app = context.applicationContext
    installSpotifyHashRecovery(app)
    Spotify.tokenRefresher = refresher@{
        val spDc = SpotifySession.spDc(app)
        if (spDc.isBlank()) return@refresher null
        SpotifyAuth.fetchAccessToken(spDc, SpotifySession.spKey(app)).getOrNull()?.let { fresh ->
            SpotifyTokenProvider.seedVerifiedToken(fresh.accessToken, fresh.accessTokenExpirationTimestampMs)
            fresh.accessToken
        }
    }
}

internal suspend fun runLoginPipeline(
    context: Context,
    spDc: String,
    spKey: String,
    source: String,
    preCapturedToken: SpotifyInternalToken?,
    autoSyncLibrary: Boolean,
    onStage: (LoginStage) -> Unit,
): LoginOutcome {
    val app = context.applicationContext
    DevConsoleManager.logAuth(
        "Spotify.loginWithWebViewSession via $source (sp_dc len=${spDc.length}, preToken=${preCapturedToken != null})",
    )

    onStage(LoginStage.VERIFY)
    val loginResult = Spotify.loginWithWebViewSession(
        spDc = spDc,
        spKey = spKey,
        preCapturedToken = preCapturedToken,
        retryAttempts = LOGIN_TOKEN_ATTEMPTS,
    )

    val session = loginResult.getOrElse { err ->
        Timber.e(err, "Spotify.loginWithWebViewSession failed")
        val message = Spotify.describeLoginFailure(err)
        DevConsoleManager.logError("SpotifyLogin", "Verification failed ($source): ${err.message ?: message}", err)
        return LoginOutcome.Failure(LoginStage.VERIFY, message, err)
    }

    onStage(LoginStage.TOKEN)
    SpotifySession.setSpDc(app, session.spDc, session.spKey)
    SpotifyTokenProvider.seedVerifiedToken(
        session.token.accessToken,
        session.token.accessTokenExpirationTimestampMs,
    )
    installSpotifyTokenAutoRefresh(app)

    onStage(LoginStage.PROFILE)
    val accountName = session.user?.let { user ->
        user.displayName?.takeIf { it.isNotBlank() } ?: user.id.takeIf { it.isNotBlank() }
    }
    DevConsoleManager.logAuth(
        "Spotify armed cleanly (webSession=${session.capturedFromWebSession}, profile=${accountName != null})",
    )

    var syncDetail: String? = null
    var syncWarning: String? = null
    if (autoSyncLibrary) {
        onStage(LoginStage.LIBRARY)
        val syncJob = LoginPipelineScope.scope.async {
            try {
                SpotifySync.syncAllFromSession(app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        val syncResult = withTimeoutOrNull(LOGIN_SYNC_TIMEOUT_MS) { syncJob.await() }
        when {
            syncResult == null ->
                syncWarning = "Library sync is still running — it will finish in the background."
            syncResult.isSuccess -> syncDetail = syncResult.getOrNull()?.detail
            else -> syncWarning =
                "Signed in, but the library sync failed: ${Spotify.describeLoginFailure(syncResult.exceptionOrNull())}"
        }
    }
    if (syncDetail.isNullOrBlank()) SpotifySync.notifyAuthStateChanged("Signed in via WebView!")

    return LoginOutcome.Success(
        signInLabel = if (session.capturedFromWebSession) "Signed in via WebView #session!" else "Signed in via WebView!",
        accountName = accountName,
        syncDetail = syncDetail,
        syncWarning = syncWarning,
    )
}

internal fun processLoginSession(
    context: Context,
    spDc: String,
    spKey: String = "",
    source: String = "WebViewSession",
    preCapturedToken: SpotifyInternalToken? = null,
    autoSyncLibrary: Boolean = true,
    scope: CoroutineScope,
    setProcessing: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    setError: (Boolean) -> Unit,
    onComplete: (Boolean) -> Unit,
    onSuccess: (String?) -> Unit,
    onStage: (LoginStage) -> Unit = {},
    onAccount: (String?) -> Unit = {},
) {
    val app = context.applicationContext
    setProcessing(true)
    setError(false)
    setStatus("Verifying Spotify WebView session…")

    fun onMain(block: () -> Unit) {
        scope.launch(Dispatchers.Main) { block() }
    }

    launchLoginTask {
        val outcome = runLoginPipeline(
            context = app,
            spDc = spDc,
            spKey = spKey,
            source = source,
            preCapturedToken = preCapturedToken,
            autoSyncLibrary = autoSyncLibrary,
            onStage = { stage ->
                onMain {
                    onStage(stage)
                    setStatus(
                        when (stage) {
                            LoginStage.VERIFY -> "Verifying Spotify WebView cookie…"
                            LoginStage.TOKEN -> "Arming Spotify.accessToken…"
                            LoginStage.PROFILE -> "Loading your Spotify profile…"
                            LoginStage.LIBRARY -> "Importing playlists, liked songs & artists…"
                        },
                    )
                }
            },
        )

        when (outcome) {
            is LoginOutcome.Success -> {
                onMain {
                    UiSoundEngine.playToggleSound(app, true)
                    onAccount(outcome.accountName)
                    setStatus(outcome.syncWarning ?: outcome.syncDetail ?: outcome.signInLabel)
                    setProcessing(false)
                    setError(false)
                    onComplete(true)
                }
                delay(LOGIN_SUCCESS_NAV_DELAY_MS)
                onMain { onSuccess(outcome.syncDetail) }
            }
            is LoginOutcome.Failure -> onMain {
                onStage(outcome.stage)
                setStatus("Sign-in failed: ${outcome.message}")
                setError(true)
                setProcessing(false)
                onComplete(false)
            }
        }
    }
}
