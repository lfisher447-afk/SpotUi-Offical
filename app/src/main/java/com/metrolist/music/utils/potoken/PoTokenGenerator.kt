package com.metrolist.music.utils.potoken

import android.content.Context
import android.os.Looper
import android.webkit.CookieManager
import com.metrolist.music.utils.cipher.CipherDeobfuscator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * High-performance, thread-safe Proof of Origin (PoToken) Generator.
 * Manages YouTube player and streaming PoToken generation through a hidden background WebView.
 */
class PoTokenGenerator(
    private val contextProvider: () -> Context = { CipherDeobfuscator.appContext }
) {
    companion object {
        private const val TAG = "PoTokenGenerator"
        
        /** Default timeout for token generation (in milliseconds) */
        const val DEFAULT_TIMEOUT_MS = 8_000L

        /** Maximum consecutive failures before triggering an automatic reset */
        private const val MAX_CONSECUTIVE_FAILURES = 3
    }

    // Lock to prevent simultaneous recreation/access race conditions
    private val lock = Mutex()

    // Session State
    private var webPoTokenSessionId: String? = null
    private var webPoTokenStreamingPot: String? = null
    private var webPoTokenGenerator: PoTokenWebView? = null

    // Health Tracking
    private val isWebViewBadImpl = AtomicBoolean(false)
    private val consecutiveFailures = AtomicInteger(0)

    /**
     * Checks if the Android System WebView is supported and functioning.
     */
    val isSupported: Boolean
        get() = !isWebViewBadImpl.get() && runCatching { CookieManager.getInstance() }.isSuccess

    /**
     * Synchronous entry-point for generating a web client PoToken.
     * Note: Prefer calling the suspend version [getWebClientPoToken] if you are inside a Coroutine.
     */
    fun getWebClientPoToken(
        videoId: String,
        sessionId: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): PoTokenResult? {
        if (!isSupported) {
            Timber.tag(TAG).w("PoToken generation skipped: WebView not available or bad implementation.")
            return null
        }

        // Prevent thread deadlocks if invoked on the main UI thread with runBlocking
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Timber.tag(TAG).e("getWebClientPoToken called on UI Thread! This can block JS evaluation.")
        }

        return try {
            runBlocking {
                getWebClientPoToken(videoId, sessionId, timeoutMs)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Sync bridge PoToken generation error: ${e.javaClass.simpleName} - ${e.message}")
            null
        }
    }

    /**
     * Coroutine-native suspend method to generate player and streaming PoTokens.
     */
    suspend fun getWebClientPoToken(
        videoId: String,
        sessionId: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): PoTokenResult? {
        if (!isSupported) return null

        Timber.tag(TAG).d("Requesting web client PoToken: videoId=$videoId, sessionId=${sessionId.take(10)}...")

        return try {
            withTimeout(timeoutMs) {
                generateInternal(videoId = videoId, sessionId = sessionId, forceRecreate = false)
            }
        } catch (e: TimeoutCancellationException) {
            Timber.tag(TAG).w("PoToken generation timed out ($timeoutMs ms) for videoId=$videoId")
            handleFailure()
            null
        } catch (e: BadWebViewException) {
            Timber.tag(TAG).e(e, "System WebView implementation is corrupted/broken.")
            isWebViewBadImpl.set(true)
            invalidate()
            null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to generate PoToken (${e.javaClass.simpleName}: ${e.message})")
            handleFailure()
            null
        }
    }

    /**
     * Internal recursive generation engine with automatic recovery & retry mechanisms.
     */
    private suspend fun generateInternal(
        videoId: String,
        sessionId: String,
        forceRecreate: Boolean
    ): PoTokenResult {
        val (generator, streamingPot, wasRecreated) = lock.withLock {
            val shouldRecreate = forceRecreate ||
                    webPoTokenGenerator == null ||
                    webPoTokenGenerator?.isExpired == true ||
                    webPoTokenSessionId != sessionId

            if (shouldRecreate) {
                Timber.tag(TAG).d("Initializing new PoTokenWebView instance (forceRecreate=$forceRecreate)...")
                
                // Cleanup existing instance on Main Thread
                closeGeneratorInternal()

                webPoTokenSessionId = sessionId

                // WebView creation MUST occur on the Main Thread
                val newGenerator = withContext(Dispatchers.Main.immediate) {
                    PoTokenWebView.getNewPoTokenGenerator(contextProvider())
                }
                webPoTokenGenerator = newGenerator

                // Streaming token must be generated FIRST before any individual video token
                val streamingToken = withContext(Dispatchers.Main.immediate) {
                    newGenerator.generatePoToken(sessionId)
                }
                webPoTokenStreamingPot = streamingToken

                Timber.tag(TAG).d("Streaming PoToken initialized successfully.")
            }

            Triple(
                webPoTokenGenerator!!,
                webPoTokenStreamingPot!!,
                shouldRecreate
            )
        }

        // Generate the player token for the specific video
        val playerPot = try {
            withContext(Dispatchers.Main.immediate) {
                generator.generatePoToken(videoId)
            }
        } catch (throwable: Throwable) {
            if (wasRecreated) {
                // If it already failed on a fresh WebView, throw it up the chain
                throw throwable
            } else {
                // Otherwise retry once by destroying and recreating the WebView
                Timber.tag(TAG).w(throwable, "Player token generation failed. Retrying with fresh WebView...")
                return generateInternal(videoId = videoId, sessionId = sessionId, forceRecreate = true)
            }
        }

        // Reset failure counter on success
        consecutiveFailures.set(0)
        Timber.tag(TAG).d("PoTokens generated successfully: player=${playerPot.take(10)}..., streaming=${streamingPot.take(10)}...")

        return PoTokenResult(playerPot = playerPot, streamingPot = streamingPot)
    }

    /**
     * Resets internal state and releases the current WebView resources.
     */
    fun reset() {
        runBlocking {
            invalidate()
        }
    }

    /**
     * Coroutine-friendly invalidate method.
     */
    suspend fun invalidate() {
        lock.withLock {
            closeGeneratorInternal()
        }
    }

    /**
     * Safely closes the existing PoTokenWebView instance on the UI Thread.
     */
    private suspend fun closeGeneratorInternal() {
        webPoTokenSessionId = null
        webPoTokenStreamingPot = null
        val currentGenerator = webPoTokenGenerator
        webPoTokenGenerator = null

        if (currentGenerator != null) {
            withContext(Dispatchers.Main.immediate) {
                runCatching {
                    currentGenerator.close()
                }.onFailure { e ->
                    Timber.tag(TAG).e(e, "Error occurred while closing PoTokenWebView")
                }
            }
        }
    }

    /**
     * Registers a failure event and resets state if threshold is exceeded.
     */
    private suspend fun handleFailure() {
        val failures = consecutiveFailures.incrementAndGet()
        Timber.tag(TAG).w("PoToken failure recorded ($failures/$MAX_CONSECUTIVE_FAILURES)")
        
        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            Timber.tag(TAG).e("Max failure threshold reached. Resetting PoTokenGenerator state.")
            invalidate()
            consecutiveFailures.set(0)
        }
    }

    /**
     * Resets system bad-state flags, allowing manual retry after system updates or WebView recovery.
     */
    fun resetBadWebViewState() {
        isWebViewBadImpl.set(false)
        consecutiveFailures.set(0)
    }
}
