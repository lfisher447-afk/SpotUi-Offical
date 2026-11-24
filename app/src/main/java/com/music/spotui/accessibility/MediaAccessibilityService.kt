@file:Suppress("TooManyFunctions", "LongMethod", "MagicNumber")

package com.music.spotui.accessibility

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.music.spotui.MainActivity
import com.music.spotui.audio.AudioEffectController
import com.music.spotui.audio.EqPresetCatalog
import com.music.spotui.data.preferences.FLOATER_OVERLAY_PREFS_NAME
import com.music.spotui.data.preferences.SpatialProfile
import com.music.spotui.data.preferences.dspSettingsUpdates
import com.music.spotui.data.preferences.floaterSettingsUpdates
import com.music.spotui.data.preferences.getBassBoostStrength
import com.music.spotui.data.preferences.getEqBass
import com.music.spotui.data.preferences.getEqBrilliance
import com.music.spotui.data.preferences.getEqHighMid
import com.music.spotui.data.preferences.getEqLowMid
import com.music.spotui.data.preferences.getEqPreampDb
import com.music.spotui.data.preferences.getEqPreset
import com.music.spotui.data.preferences.getEqSubBass
import com.music.spotui.data.preferences.getEqTreble
import com.music.spotui.data.preferences.getEqVocal
import com.music.spotui.data.preferences.getFloaterAutoHideMs
import com.music.spotui.data.preferences.getFloaterHapticStrength
import com.music.spotui.data.preferences.getSpatialElevationDepth
import com.music.spotui.data.preferences.getSpatialOrbitSpeed
import com.music.spotui.data.preferences.getSpatialProfile
import com.music.spotui.data.preferences.getSpatialRoomDelayMs
import com.music.spotui.data.preferences.getSpatialStrength
import com.music.spotui.data.preferences.isAudioNormalizerEnabled
import com.music.spotui.data.preferences.isEqEnabled
import com.music.spotui.data.preferences.isEqSpatialEnabled
import com.music.spotui.data.preferences.isFloaterEqAnd3dEnabled
import com.music.spotui.data.preferences.isFloaterHapticsEnabled
import com.music.spotui.data.preferences.isFloaterStartExpanded
import com.music.spotui.data.preferences.isHeadphoneCrossfeedEnabled
import com.music.spotui.data.preferences.isLimiterEnabled
import com.music.spotui.data.preferences.isSkipSilenceEnabled
import com.music.spotui.data.preferences.isSpatial8dOrbitEnabled
import com.music.spotui.data.preferences.isSpatialCorrelationGuardEnabled
import com.music.spotui.data.preferences.resetFloaterGeometry
import com.music.spotui.data.preferences.setAudioNormalizerEnabled
import com.music.spotui.data.preferences.setBassBoostStrength
import com.music.spotui.data.preferences.setEqBass
import com.music.spotui.data.preferences.setEqBrilliance
import com.music.spotui.data.preferences.setEqEnabled
import com.music.spotui.data.preferences.setEqHighMid
import com.music.spotui.data.preferences.setEqLowMid
import com.music.spotui.data.preferences.setEqPreampDb
import com.music.spotui.data.preferences.setEqPreset
import com.music.spotui.data.preferences.setEqSpatialEnabled
import com.music.spotui.data.preferences.setEqSubBass
import com.music.spotui.data.preferences.setEqTreble
import com.music.spotui.data.preferences.setEqVocal
import com.music.spotui.data.preferences.setFloaterAutoHideMs
import com.music.spotui.data.preferences.setFloaterHapticsEnabled
import com.music.spotui.data.preferences.setHeadphoneCrossfeedEnabled
import com.music.spotui.data.preferences.setLimiterEnabled
import com.music.spotui.data.preferences.setPlaybackPitchPref
import com.music.spotui.data.preferences.setPlaybackSpeedPref
import com.music.spotui.data.preferences.setSkipSilenceEnabled
import com.music.spotui.data.preferences.setSpatial8dOrbitEnabled
import com.music.spotui.data.preferences.setSpatialCorrelationGuardEnabled
import com.music.spotui.data.preferences.setSpatialElevationDepth
import com.music.spotui.data.preferences.setSpatialOrbitSpeed
import com.music.spotui.data.preferences.setSpatialProfile
import com.music.spotui.data.preferences.setSpatialRoomDelayMs
import com.music.spotui.data.preferences.setSpatialStrength
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.notification.PlaybackService
import com.music.spotui.ui.theme.SpotuiTheme
import com.music.spotui.util.DevConsoleManager
import com.music.spotui.util.DevConsoleManager.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Material 3 Expressive Floating Audio Studio Console + Conditional In-Game Live Translation Bubble.
 *
 * Uses `TYPE_ACCESSIBILITY_OVERLAY` with `canRetrieveWindowContent="false"` and `isAccessibilityTool="true"`
 * so SpotUI controls (including In-App Volume, 7-band EQ, 3D Spatial, Queue, Pro Tools, corner-drag resizing,
 * and Game-only Auto-Detect -> English Translation) float cleanly over apps & games without `SYSTEM_ALERT_WINDOW`.
 */
class MediaAccessibilityService : AccessibilityService(), FloaterCallbacks {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlayScope: CoroutineScope? = null

    private var windowManager: WindowManager? = null
    private val prefs: SharedPreferences by lazy(LazyThreadSafetyMode.NONE) {
        applicationContext.getSharedPreferences(FLOATER_OVERLAY_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val uiStateFlow = MutableStateFlow(FloaterUiModel())

    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null

    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var bubbleLifecycleOwner: OverlayLifecycleOwner? = null

    private var currentOrientation: Int = Configuration.ORIENTATION_UNDEFINED
    private var isDrawerExpanded: Boolean = false
    private var activeTab: FloaterTab = FloaterTab.PLAYER
    private var isSidebarCompact: Boolean = false
    private var lastUnmutedVolumePct: Int = 100

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var accessibilityButtonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoDismissRunnable = Runnable {
        DevConsoleManager.log(LogLevel.INFO, TAG, "Floater auto-dismissed after idle timeout")
        hideOverlay()
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = refreshUiState()
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = refreshUiState()
        override fun onPlaybackStateChanged(playbackState: Int) = refreshUiState()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        currentOrientation = resources.configuration.orientation
        AudioEffectController.refreshOutputRoute(applicationContext)
        GameTranslatorEngine.hydratePreferences(applicationContext)

        activeTab = FloaterTab.entries.firstOrNull {
            it.key == prefs.getString(KEY_ACTIVE_TAB, FloaterTab.PLAYER.key)
        } ?: FloaterTab.PLAYER
        if (activeTab == FloaterTab.TRANSLATE && !GameTranslatorEngine.isGameActiveFlow.value) {
            activeTab = FloaterTab.PLAYER
        }
        isSidebarCompact = prefs.getBoolean(KEY_SIDEBAR_COMPACT, false)

        registerAccessibilityButton()
        ensureMediaControllerConnected()
        observeGameBubbleVisibility()

        DevConsoleManager.log(
            LogLevel.INFO,
            TAG,
            "MediaAccessibilityService connected (Material 3 Expressive Floater + Game Translation ready)",
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            GameTranslatorEngine.updateForegroundPackage(applicationContext, event.packageName)
            refreshUiState()
        }
    }

    override fun onInterrupt() {
        hideOverlay()
        hideTranslationBubble()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        currentOrientation = newConfig.orientation
        val view = overlayView ?: return
        val params = overlayParams ?: return
        val wm = windowManager ?: return

        applyOrientationPlacement(params, newConfig.orientation, isDrawerExpanded)
        updateWindowFocusableMode(params)
        runCatching {
            if (view.isAttachedToWindow) wm.updateViewLayout(view, params)
        }
        refreshUiState()
    }

    override fun onDestroy() {
        cancelAutoDismissTimer()
        unregisterAccessibilityButton()
        hideOverlay()
        hideTranslationBubble()
        GameTranslatorEngine.destroy()
        releaseMediaController()
        serviceScope.cancel()
        windowManager = null
        DevConsoleManager.log(LogLevel.INFO, TAG, "MediaAccessibilityService destroyed cleanly")
        super.onDestroy()
    }

    private fun observeGameBubbleVisibility() {
        serviceScope.launch {
            combine(
                GameTranslatorEngine.isGameActiveFlow,
                GameTranslatorEngine.bubbleEnabledFlow,
            ) { isGame, bubbleOn -> isGame && bubbleOn }
                .collectLatest { shouldShowBubble ->
                    if (shouldShowBubble) {
                        showTranslationBubble()
                    } else {
                        hideTranslationBubble()
                    }
                    refreshUiState()
                }
        }
    }

    private fun registerAccessibilityButton() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val controller = runCatching { accessibilityButtonController }.getOrNull() ?: return
        val callback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
            override fun onClicked(controller: AccessibilityButtonController) {
                serviceScope.launch { toggleOverlay() }
            }

            override fun onAvailabilityChanged(
                controller: AccessibilityButtonController,
                available: Boolean,
            ) {
                DevConsoleManager.log(LogLevel.INFO, TAG, "Accessibility button available=$available")
            }
        }
        accessibilityButtonCallback = callback
        runCatching { controller.registerAccessibilityButtonCallback(callback) }
    }

    private fun unregisterAccessibilityButton() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val callback = accessibilityButtonCallback ?: return
        accessibilityButtonCallback = null
        runCatching { accessibilityButtonController.unregisterAccessibilityButtonCallback(callback) }
    }

    private fun toggleOverlay() {
        if (overlayView != null) hideOverlay() else showOverlay()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        if (overlayView != null) return
        val wm = windowManager ?: (getSystemService(Context.WINDOW_SERVICE) as? WindowManager).also {
            windowManager = it
        } ?: return

        GameTranslatorEngine.refreshMicPermission(applicationContext)
        isDrawerExpanded = isFloaterEqAnd3dEnabled(this) && isFloaterStartExpanded(this)
        if (activeTab == FloaterTab.TRANSLATE && !GameTranslatorEngine.isGameActiveFlow.value) {
            activeTab = FloaterTab.PLAYER
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        applyOrientationPlacement(params, currentOrientation, isDrawerExpanded)
        updateWindowFocusableMode(params)

        val owner = OverlayLifecycleOwner()
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                SpotuiTheme(darkTheme = true, dynamicColor = true) {
                    val model by uiStateFlow.collectAsStateWithLifecycle()
                    val bubble by GameTranslatorEngine.bubbleStateFlow.collectAsStateWithLifecycle()
                    val history by GameTranslatorEngine.historyFlow.collectAsStateWithLifecycle()
                    val hasMic by GameTranslatorEngine.hasMicPermissionFlow.collectAsStateWithLifecycle()
                    val listening by GameTranslatorEngine.isListeningFlow.collectAsStateWithLifecycle()
                    val srcLang by GameTranslatorEngine.sourceLangFlow.collectAsStateWithLifecycle()
                    val bubbleOn by GameTranslatorEngine.bubbleEnabledFlow.collectAsStateWithLifecycle()
                    val bubbleW by GameTranslatorEngine.bubbleWidthDpFlow.collectAsStateWithLifecycle()
                    val bubbleSp by GameTranslatorEngine.bubbleTextSizeSpFlow.collectAsStateWithLifecycle()
                    val bubbleAlpha by GameTranslatorEngine.bubbleOpacityFlow.collectAsStateWithLifecycle()

                    FloaterExpressiveConsole(
                        state = model,
                        translationBubble = bubble,
                        translationHistory = history,
                        hasMicPermission = hasMic,
                        isListening = listening,
                        translateSourceLang = srcLang,
                        bubbleEnabled = bubbleOn,
                        bubbleWidthDp = bubbleW,
                        bubbleTextSizeSp = bubbleSp,
                        bubbleOpacity = bubbleAlpha,
                        callbacks = this@MediaAccessibilityService,
                    )
                }
            }
        }

        val container = OverlayTouchContainer(this) {
            hideOverlay()
        }.apply {
            addView(composeView)
        }
        owner.attachTo(container)
        owner.onAttach()

        val attached = runCatching {
            wm.addView(container, params)
            true
        }.getOrElse { err ->
            DevConsoleManager.log(LogLevel.ERROR, TAG, "Failed attaching Floater ComposeView: ${err.message}", err)
            owner.onDetach()
            false
        }
        if (!attached) return

        overlayView = container
        overlayParams = params
        overlayLifecycleOwner = owner

        startOverlayObservers()
        refreshUiState()
        resetAutoDismissTimer()
        performHaptic(isAccent = true)
    }

    private fun hideOverlay() {
        cancelAutoDismissTimer()
        overlayScope?.cancel()
        overlayScope = null

        val view = overlayView
        val owner = overlayLifecycleOwner
        overlayView = null
        overlayParams = null
        overlayLifecycleOwner = null

        owner?.onDetach()
        if (view != null) {
            runCatching {
                if (view.isAttachedToWindow) windowManager?.removeViewImmediate(view)
            }
        }
    }

    private fun showTranslationBubble() {
        if (bubbleView != null) return
        val wm = windowManager ?: (getSystemService(Context.WINDOW_SERVICE) as? WindowManager).also {
            windowManager = it
        } ?: return

        val metrics = resources.displayMetrics
        val savedX = prefs.getInt(KEY_BUBBLE_X, dpToPx(10)).coerceIn(dpToPx(4), (metrics.widthPixels - dpToPx(140)).coerceAtLeast(dpToPx(4)))
        val savedY = prefs.getInt(KEY_BUBBLE_Y, dpToPx(24)).coerceIn(dpToPx(8), (metrics.heightPixels - dpToPx(80)).coerceAtLeast(dpToPx(8)))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }

        val owner = OverlayLifecycleOwner()
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                SpotuiTheme(darkTheme = true, dynamicColor = true) {
                    val bubble by GameTranslatorEngine.bubbleStateFlow.collectAsStateWithLifecycle()
                    val widthDp by GameTranslatorEngine.bubbleWidthDpFlow.collectAsStateWithLifecycle()
                    val textSizeSp by GameTranslatorEngine.bubbleTextSizeSpFlow.collectAsStateWithLifecycle()
                    val opacity by GameTranslatorEngine.bubbleOpacityFlow.collectAsStateWithLifecycle()
                    val gameLabel by GameTranslatorEngine.activeGameLabelFlow.collectAsStateWithLifecycle()

                    TranslationBubbleOverlayContent(
                        bubble = bubble,
                        widthDp = widthDp,
                        textSizeSp = textSizeSp,
                        opacity = opacity,
                        gameLabel = gameLabel,
                        onBubbleDrag = { dx, dy ->
                            val v = bubbleView ?: return@TranslationBubbleOverlayContent
                            val p = bubbleParams ?: return@TranslationBubbleOverlayContent
                            p.x = (p.x + dx.roundToInt()).coerceIn(dpToPx(4), (resources.displayMetrics.widthPixels - dpToPx(120)).coerceAtLeast(dpToPx(4)))
                            p.y = (p.y + dy.roundToInt()).coerceIn(dpToPx(8), (resources.displayMetrics.heightPixels - dpToPx(64)).coerceAtLeast(dpToPx(8)))
                            runCatching { if (v.isAttachedToWindow) windowManager?.updateViewLayout(v, p) }
                        },
                        onBubbleDragEnd = {
                            val p = bubbleParams ?: return@TranslationBubbleOverlayContent
                            prefs.edit().putInt(KEY_BUBBLE_X, p.x).putInt(KEY_BUBBLE_Y, p.y).apply()
                        },
                        onBubbleResizeWidthPx = { dx ->
                            val density = resources.displayMetrics.density.coerceAtLeast(1f)
                            val currentDp = GameTranslatorEngine.bubbleWidthDpFlow.value
                            GameTranslatorEngine.setBubbleWidthDp(applicationContext, currentDp + (dx / density))
                        },
                        onDismissBubble = {
                            GameTranslatorEngine.setBubbleEnabled(applicationContext, false)
                        },
                        onOpenTranslatorTab = {
                            if (overlayView == null) {
                                activeTab = FloaterTab.TRANSLATE
                                showOverlay()
                            } else {
                                isDrawerExpanded = true
                                activeTab = FloaterTab.TRANSLATE
                                updateExpandedLayout()
                            }
                        },
                    )
                }
            }
        }

        val container = OverlayTouchContainer(this).apply { addView(composeView) }
        owner.attachTo(container)
        owner.onAttach()

        val attached = runCatching {
            wm.addView(container, params)
            true
        }.getOrDefault(false)
        if (!attached) {
            owner.onDetach()
            return
        }
        bubbleView = container
        bubbleParams = params
        bubbleLifecycleOwner = owner
    }

    private fun hideTranslationBubble() {
        val view = bubbleView
        val owner = bubbleLifecycleOwner
        bubbleView = null
        bubbleParams = null
        bubbleLifecycleOwner = null
        owner?.onDetach()
        if (view != null) {
            runCatching { if (view.isAttachedToWindow) windowManager?.removeViewImmediate(view) }
        }
    }

    private fun startOverlayObservers() {
        overlayScope?.cancel()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        overlayScope = scope

        scope.launch {
            while (isActive && overlayView != null) {
                refreshUiState()
                delay(500L)
            }
        }
        scope.launch {
            dspSettingsUpdates.collectLatest {
                if (overlayView != null) refreshUiState()
            }
        }
        scope.launch {
            floaterSettingsUpdates.collectLatest {
                if (overlayView == null) return@collectLatest
                if (!isFloaterEqAnd3dEnabled(this@MediaAccessibilityService)) {
                    isDrawerExpanded = false
                    updateExpandedLayout()
                }
                resetAutoDismissTimer()
                refreshUiState()
            }
        }
    }

    private fun refreshUiState() {
        val snap = SongPlayer.getNowPlayingSnapshot(applicationContext)
        val vol = SongPlayer.getAppVolumePercent(applicationContext)
        if (vol > 0) lastUnmutedVolumePct = vol

        val coverUri = snap.queue.getOrNull(snap.activeQueueIndex)?.coverUri
            ?: com.music.spotui.data.preferences.loadLastPlayback(applicationContext)?.first?.coverUri.orEmpty()

        val route = AudioEffectController.refreshOutputRoute(applicationContext)
        val params = overlayParams
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val wDp = ((params?.width ?: getSavedWidthPx(currentOrientation, isDrawerExpanded)) / density).roundToInt()
        val hDp = (((if ((params?.height ?: -1) > 0) params!!.height else overlayView?.height ?: dpToPx(220))) / density).roundToInt()

        val isGame = GameTranslatorEngine.isGameActiveFlow.value
        val effectiveTab = if (activeTab == FloaterTab.TRANSLATE && !isGame) FloaterTab.PLAYER else activeTab

        uiStateFlow.value = FloaterUiModel(
            songId = snap.songId,
            title = snap.title.ifBlank { mediaController?.mediaMetadata?.title?.toString().orEmpty() }.ifBlank { "SpotUI Ready" },
            artist = snap.artist.ifBlank { mediaController?.mediaMetadata?.artist?.toString().orEmpty() }.ifBlank { "Tap Play to resume" },
            album = snap.album,
            coverUri = coverUri,
            sourceLabel = snap.sourceLabel.ifBlank { "SPOTUI" },
            qualityLabel = snap.qualityLabel.ifBlank { "32-bit DSP" },
            isPlaying = snap.isPlaying || (mediaController?.isPlaying == true),
            isResolving = snap.isResolving,
            isShuffled = snap.isShuffled,
            repeatMode = snap.repeatMode,
            isLiked = snap.isLiked,
            positionMs = snap.positionMs,
            durationMs = snap.durationMs,
            activeQueueIndex = snap.activeQueueIndex,
            queue = snap.queue,
            inAppVolumePct = vol,
            isDrawerExpanded = isDrawerExpanded,
            floaterEq3dEnabled = isFloaterEqAnd3dEnabled(this),
            isSidebarCompact = isSidebarCompact,
            activeTab = effectiveTab,
            widthDp = wDp,
            heightDp = hDp,
            eqEnabled = isEqEnabled(this),
            eqPresetName = getEqPreset(this).ifBlank { "Flat" },
            bandSubBass = getEqSubBass(this),
            bandBass = getEqBass(this),
            bandLowMid = getEqLowMid(this),
            bandVocal = getEqVocal(this),
            bandHighMid = getEqHighMid(this),
            bandTreble = getEqTreble(this),
            bandBrilliance = getEqBrilliance(this),
            bassBoostStrength = getBassBoostStrength(this),
            preampDb = getEqPreampDb(this),
            normalizerEnabled = isAudioNormalizerEnabled(this),
            limiterEnabled = isLimiterEnabled(this),
            spatialEnabled = isEqSpatialEnabled(this),
            spatialProfile = getSpatialProfile(this),
            spatialStrength = getSpatialStrength(this),
            spatialRoomDelayMs = getSpatialRoomDelayMs(this),
            spatialElevationDepth = getSpatialElevationDepth(this),
            orbit8dEnabled = isSpatial8dOrbitEnabled(this),
            orbit8dSpeedHz = getSpatialOrbitSpeed(this),
            crossfeedEnabled = isHeadphoneCrossfeedEnabled(this),
            correlationGuardEnabled = isSpatialCorrelationGuardEnabled(this),
            audioRouteLabel = route.route.label,
            audioDeviceName = route.deviceName,
            speed = snap.speed,
            pitch = snap.pitch,
            skipSilence = isSkipSilenceEnabled(this),
            sleepTimerRemainingMs = snap.sleepTimerRemainingMs,
            abLoopStartMs = snap.abLoopStartMs,
            abLoopEndMs = snap.abLoopEndMs,
            autoHideMs = getFloaterAutoHideMs(this),
            hapticsEnabled = isFloaterHapticsEnabled(this),
            isGameActive = isGame,
            activeGameLabel = GameTranslatorEngine.activeGameLabelFlow.value,
        )
    }

    private fun updateExpandedLayout() {
        val v = overlayView ?: return
        val p = overlayParams ?: return
        applyOrientationPlacement(p, currentOrientation, isDrawerExpanded)
        updateWindowFocusableMode(p)
        runCatching { if (v.isAttachedToWindow) windowManager?.updateViewLayout(v, p) }
        refreshUiState()
    }

    private fun updateWindowFocusableMode(params: WindowManager.LayoutParams) {
        val allowKeyboardInput = isDrawerExpanded && activeTab == FloaterTab.TRANSLATE && GameTranslatorEngine.isGameActiveFlow.value
        if (allowKeyboardInput) {
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
    }

    // ── FloaterCallbacks Implementation ──────────────────────────────────────────────────────

    override fun onInteraction() = resetAutoDismissTimer()

    override fun onHaptic(isAccent: Boolean) = performHaptic(isAccent)

    override fun onClose() = hideOverlay()

    override fun onOpenApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        runCatching { startActivity(intent) }
        hideOverlay()
    }

    override fun onToggleLike() {
        resetAutoDismissTimer()
        SongPlayer.toggleCurrentTrackLike(applicationContext)
        refreshUiState()
    }

    override fun onToggleExpand() {
        resetAutoDismissTimer()
        isDrawerExpanded = !isDrawerExpanded
        updateExpandedLayout()
    }

    override fun onSelectTab(tab: FloaterTab) {
        resetAutoDismissTimer()
        activeTab = tab
        prefs.edit().putString(KEY_ACTIVE_TAB, tab.key).apply()
        val p = overlayParams
        val v = overlayView
        if (p != null && v != null) {
            updateWindowFocusableMode(p)
            runCatching { if (v.isAttachedToWindow) windowManager?.updateViewLayout(v, p) }
        }
        refreshUiState()
    }

    override fun onToggleSidebarCompact() {
        resetAutoDismissTimer()
        isSidebarCompact = !isSidebarCompact
        prefs.edit().putBoolean(KEY_SIDEBAR_COMPACT, isSidebarCompact).apply()
        refreshUiState()
    }

    override fun onTogglePlayPause() {
        resetAutoDismissTimer()
        val snap = SongPlayer.getNowPlayingSnapshot(applicationContext)
        val ctrl = mediaController
        if (snap.queue.isNotEmpty() || SongPlayer.isPlaybackActiveNow()) {
            SongPlayer.togglePlayOrResume(applicationContext)
        } else if (ctrl != null && ctrl.isConnected) {
            if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
        } else {
            SongPlayer.togglePlayOrResume(applicationContext)
        }
        mainHandler.postDelayed({ if (overlayView != null) refreshUiState() }, 140L)
    }

    override fun onPrevious() {
        resetAutoDismissTimer()
        SongPlayer.triggerPrevious(applicationContext)
        mainHandler.postDelayed({ if (overlayView != null) refreshUiState() }, 180L)
    }

    override fun onNext() {
        resetAutoDismissTimer()
        SongPlayer.triggerNext(applicationContext)
        mainHandler.postDelayed({ if (overlayView != null) refreshUiState() }, 180L)
    }

    override fun onToggleShuffle() {
        resetAutoDismissTimer()
        SongPlayer.toggleShuffle(applicationContext)
        refreshUiState()
    }

    override fun onCycleRepeat() {
        resetAutoDismissTimer()
        SongPlayer.cycleRepeatMode(applicationContext)
        refreshUiState()
    }

    override fun onSeekTo(positionMs: Long) {
        resetAutoDismissTimer()
        SongPlayer.seekTo(positionMs)
        refreshUiState()
    }

    override fun onSeekBy(deltaMs: Long) {
        resetAutoDismissTimer()
        val cur = SongPlayer.getCurrentPosition()
        val dur = SongPlayer.getDuration().coerceAtLeast(0L)
        val target = (cur + deltaMs).let { if (dur > 0L) it.coerceIn(0L, dur) else it.coerceAtLeast(0L) }
        SongPlayer.seekTo(target)
        refreshUiState()
    }

    override fun onPlayQueueIndex(index: Int) {
        resetAutoDismissTimer()
        SongPlayer.playQueueItemAt(applicationContext, index)
        mainHandler.postDelayed({ if (overlayView != null) refreshUiState() }, 200L)
    }

    override fun onShuffleUpcomingQueue() {
        resetAutoDismissTimer()
        SongPlayer.shuffleUpcomingQueue()
        refreshUiState()
    }

    override fun onDeduplicateQueue() {
        resetAutoDismissTimer()
        SongPlayer.deduplicateQueue()
        refreshUiState()
    }

    override fun onVolumeChange(percent: Int) {
        resetAutoDismissTimer()
        val clamped = percent.coerceIn(0, 100)
        if (clamped > 0) lastUnmutedVolumePct = clamped
        SongPlayer.setAppVolumePercent(applicationContext, clamped)
        refreshUiState()
    }

    override fun onToggleMute() {
        resetAutoDismissTimer()
        val cur = SongPlayer.getAppVolumePercent(applicationContext)
        if (cur > 0) {
            lastUnmutedVolumePct = cur
            SongPlayer.setAppVolumePercent(applicationContext, 0)
        } else {
            SongPlayer.setAppVolumePercent(applicationContext, lastUnmutedVolumePct.coerceIn(10, 100))
        }
        refreshUiState()
    }

    override fun onToggleEq(enabled: Boolean) {
        setEqEnabled(applicationContext, enabled)
        SongPlayer.refreshAudioEffects(applicationContext)
        refreshUiState()
    }

    override fun onSelectEqPreset(name: String) {
        EqPresetCatalog.findByName(name)?.let { EqPresetCatalog.applyPreset(applicationContext, it) }
        SongPlayer.refreshAudioEffects(applicationContext)
        refreshUiState()
    }

    override fun onEqBandChange(bandIndex: Int, value: Float) {
        when (bandIndex) {
            0 -> setEqSubBass(applicationContext, value)
            1 -> setEqBass(applicationContext, value)
            2 -> setEqLowMid(applicationContext, value)
            3 -> setEqVocal(applicationContext, value)
            4 -> setEqHighMid(applicationContext, value)
            5 -> setEqTreble(applicationContext, value)
            6 -> setEqBrilliance(applicationContext, value)
        }
        setEqPreset(applicationContext, "Custom")
        SongPlayer.refreshAudioEffects(applicationContext)
        refreshUiState()
    }

    override fun onBassBoostChange(strength: Int) {
        setBassBoostStrength(applicationContext, strength.coerceIn(0, 1000))
        SongPlayer.refreshAudioEffects(applicationContext)
        refreshUiState()
    }

    override fun onPreampChange(db: Float) {
        setEqPreampDb(applicationContext, db)
        SongPlayer.refreshAudioEffects(applicationContext)
        refreshUiState()
    }

    override fun onToggleNormalizer(enabled: Boolean) {
        setAudioNormalizerEnabled(applicationContext, enabled)
        SongPlayer.refreshAudioEffects(applicationContext)
        refreshUiState()
    }

    override fun onToggleLimiter(enabled: Boolean) {
        setLimiterEnabled(applicationContext, enabled)
        SongPlayer.refreshAudioEffects(applicationContext)
        refreshUiState()
    }

    override fun onResetEqFlat() {
        EqPresetCatalog.resetFlat(applicationContext)
        SongPlayer.refreshAudioEffects(applicationContext)
        refreshUiState()
    }

    override fun onToggleSpatial(enabled: Boolean) {
        setEqSpatialEnabled(applicationContext, enabled)
        SongPlayer.refreshAudioEffects(applicationContext)
        refreshUiState()
    }

    override fun onSelectSpatialProfile(profile: SpatialProfile) {
        setSpatialProfile(applicationContext, profile)
        if (!isEqSpatialEnabled(applicationContext)) setEqSpatialEnabled(applicationContext, true)
        SongPlayer.refreshAudioEffects(applicationContext)
        refreshUiState()
    }

    override fun onSpatialStrengthChange(strength: Int) {
        setSpatialStrength(applicationContext, strength)
        SongPlayer.refreshAudioEffects(applicationContext)
        refreshUiState()
    }

    override fun onSpatialDelayChange(delayMs: Float) {
        setSpatialRoomDelayMs(applicationContext, delayMs)
        SongPlayer.refreshAudioEffects(applicationContext)
        refreshUiState()
    }

    override fun onSpatialElevationChange(depth: Float) {
        setSpatialElevationDepth(applicationContext, depth)
        SongPlayer.refreshAudioEffects(applicationContext)
        refreshUiState()
    }

    override fun onToggle8dOrbit(enabled: Boolean) {
        setSpatial8dOrbitEnabled(applicationContext, enabled)
        if (enabled && !isEqSpatialEnabled(applicationContext)) setEqSpatialEnabled(applicationContext, true)
        SongPlayer.refreshAudioEffects(applicationContext)
        refreshUiState()
    }

    override fun onOrbitSpeedChange(hz: Float) {
        setSpatialOrbitSpeed(applicationContext, hz)
        SongPlayer.refreshAudioEffects(applicationContext)
        refreshUiState()
    }

    override fun onToggleCrossfeed(enabled: Boolean) {
        setHeadphoneCrossfeedEnabled(applicationContext, enabled)
        SongPlayer.refreshAudioEffects(applicationContext)
        refreshUiState()
    }

    override fun onToggleCorrelationGuard(enabled: Boolean) {
        setSpatialCorrelationGuardEnabled(applicationContext, enabled)
        SongPlayer.refreshAudioEffects(applicationContext)
        refreshUiState()
    }

    override fun onSpeedChange(speed: Float) {
        setPlaybackSpeedPref(applicationContext, speed)
        SongPlayer.refreshPlaybackTuning(applicationContext)
        refreshUiState()
    }

    override fun onPitchChange(pitch: Float) {
        setPlaybackPitchPref(applicationContext, pitch)
        SongPlayer.refreshPlaybackTuning(applicationContext)
        refreshUiState()
    }

    override fun onResetSpeedPitch() {
        setPlaybackSpeedPref(applicationContext, 1.0f)
        setPlaybackPitchPref(applicationContext, 1.0f)
        SongPlayer.refreshPlaybackTuning(applicationContext)
        refreshUiState()
    }

    override fun onToggleSkipSilence(enabled: Boolean) {
        setSkipSilenceEnabled(applicationContext, enabled)
        SongPlayer.refreshPlaybackTuning(applicationContext)
        refreshUiState()
    }

    override fun onSetSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            SongPlayer.cancelSleepTimer()
        } else {
            SongPlayer.setSleepTimer(minutes * 60_000L)
        }
        refreshUiState()
    }

    override fun onSetAbStart() {
        SongPlayer.setAbLoopStart()
        refreshUiState()
    }

    override fun onSetAbEnd() {
        SongPlayer.setAbLoopEnd()
        refreshUiState()
    }

    override fun onClearAbLoop() {
        SongPlayer.clearAbLoop()
        refreshUiState()
    }

    override fun onCycleAutoHide() {
        val next = when (getFloaterAutoHideMs(this)) {
            5_000L -> 12_000L
            12_000L -> 25_000L
            25_000L -> 0L
            else -> 5_000L
        }
        setFloaterAutoHideMs(applicationContext, next)
        resetAutoDismissTimer()
        refreshUiState()
    }

    override fun onToggleHaptics(enabled: Boolean) {
        setFloaterHapticsEnabled(applicationContext, enabled)
        if (enabled) performHaptic(true)
        refreshUiState()
    }

    override fun onResetGeometry() {
        resetFloaterGeometry(applicationContext)
        val v = overlayView
        val p = overlayParams
        if (v != null && p != null) {
            applyOrientationPlacement(p, currentOrientation, isDrawerExpanded)
            runCatching { if (v.isAttachedToWindow) windowManager?.updateViewLayout(v, p) }
        }
        refreshUiState()
    }

    override fun onHeaderDrag(dxPx: Float, dyPx: Float) {
        val v = overlayView ?: return
        val p = overlayParams ?: return
        val metrics = resources.displayMetrics
        val h = if (p.height > 0) p.height else v.height.coerceAtLeast(dpToPx(220))
        val maxX = (metrics.widthPixels - p.width - dpToPx(4)).coerceAtLeast(dpToPx(4))
        val maxY = (metrics.heightPixels - h - dpToPx(8)).coerceAtLeast(dpToPx(8))
        p.x = (p.x + dxPx.roundToInt()).coerceIn(dpToPx(4), maxX)
        p.y = (p.y + dyPx.roundToInt()).coerceIn(dpToPx(8), maxY)
        runCatching { if (v.isAttachedToWindow) windowManager?.updateViewLayout(v, p) }
    }

    override fun onHeaderDragEnd() {
        val v = overlayView ?: return
        val p = overlayParams ?: return
        val h = if (p.height > 0) p.height else v.height.coerceAtLeast(dpToPx(220))
        saveDragCoordinates(currentOrientation, p.x, p.y, h, p.width)
    }

    override fun onCornerResize(dxPx: Float, dyPx: Float, isLeftCorner: Boolean) {
        val v = overlayView ?: return
        val p = overlayParams ?: return
        val metrics = resources.displayMetrics
        val maxW = (metrics.widthPixels - dpToPx(12)).coerceAtMost(dpToPx(600))
        val minW = dpToPx(292)

        val deltaX = if (isLeftCorner) -dxPx else dxPx
        val newW = (p.width + deltaX.roundToInt()).coerceIn(minW, maxW)
        if (isLeftCorner) {
            val rightEdge = p.x + p.width
            p.x = (rightEdge - newW).coerceIn(dpToPx(4), (metrics.widthPixels - newW - dpToPx(4)).coerceAtLeast(dpToPx(4)))
        }
        p.width = newW

        if (isDrawerExpanded) {
            val baseH = if (p.height > 0) p.height else v.height.coerceAtLeast(dpToPx(320))
            val maxH = (metrics.heightPixels - p.y - dpToPx(8)).coerceAtLeast(dpToPx(280))
            p.height = (baseH + dyPx.roundToInt()).coerceIn(dpToPx(280), maxH)
        }
        runCatching { if (v.isAttachedToWindow) windowManager?.updateViewLayout(v, p) }
        refreshUiState()
    }

    override fun onCornerResizeEnd() {
        val v = overlayView ?: return
        val p = overlayParams ?: return
        val h = if (p.height > 0) p.height else v.height.coerceAtLeast(dpToPx(220))
        saveDragCoordinates(currentOrientation, p.x, p.y, h, p.width)
        saveGeometry(currentOrientation, p.width, if (isDrawerExpanded) p.height else 0)
        performHaptic(isAccent = true)
    }

    override fun onRequestMicPermission() {
        resetAutoDismissTimer()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_REQUEST_MIC_PERMISSION, true)
        }
        runCatching { startActivity(intent) }
    }

    override fun onToggleListening() {
        resetAutoDismissTimer()
        if (GameTranslatorEngine.isListeningFlow.value) {
            GameTranslatorEngine.stopListening()
        } else {
            GameTranslatorEngine.startListening(applicationContext)
        }
    }

    override fun onSelectTranslateSource(code: String) {
        resetAutoDismissTimer()
        GameTranslatorEngine.setSourceLanguage(applicationContext, code)
    }

    override fun onTranslateManualText(text: String) {
        resetAutoDismissTimer()
        GameTranslatorEngine.translateTextNow(text, recordHistory = true)
    }

    override fun onToggleBubbleVisible(enabled: Boolean) {
        resetAutoDismissTimer()
        GameTranslatorEngine.setBubbleEnabled(applicationContext, enabled)
    }

    override fun onBubbleWidthChange(widthDp: Float) {
        resetAutoDismissTimer()
        GameTranslatorEngine.setBubbleWidthDp(applicationContext, widthDp)
    }

    override fun onBubbleTextSizeChange(sizeSp: Float) {
        resetAutoDismissTimer()
        GameTranslatorEngine.setBubbleTextSizeSp(applicationContext, sizeSp)
    }

    override fun onBubbleOpacityChange(opacity: Float) {
        resetAutoDismissTimer()
        GameTranslatorEngine.setBubbleOpacity(applicationContext, opacity)
    }

    override fun onClearTranslationHistory() {
        resetAutoDismissTimer()
        GameTranslatorEngine.clearHistory()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────

    private fun performHaptic(isAccent: Boolean) {
        if (!isFloaterHapticsEnabled(this)) return
        val strength = getFloaterHapticStrength(this)
        val durationMs = if (isAccent) strength.accentDurationMs else strength.clickDurationMs
        val amplitude = strength.amplitude

        overlayView?.let { v ->
            runCatching {
                val flag = if (isAccent) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
                } else {
                    HapticFeedbackConstants.CLOCK_TICK
                }
                v.performHapticFeedback(flag)
            }
        }

        runCatching {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return@runCatching
            if (!vib.hasVibrator()) return@runCatching
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amp = if (vib.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
                vib.vibrate(VibrationEffect.createOneShot(durationMs, amp))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        }
    }

    private fun resetAutoDismissTimer() {
        if (overlayView == null) return
        mainHandler.removeCallbacks(autoDismissRunnable)
        val timeoutMs = getFloaterAutoHideMs(this)
        if (timeoutMs > 0L) {
            mainHandler.postDelayed(autoDismissRunnable, timeoutMs)
        }
    }

    private fun cancelAutoDismissTimer() {
        mainHandler.removeCallbacks(autoDismissRunnable)
    }

    private fun applyOrientationPlacement(
        params: WindowManager.LayoutParams,
        orientation: Int,
        expanded: Boolean,
    ) {
        params.gravity = Gravity.TOP or Gravity.START
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val prefix = if (isLandscape) KEY_LANDSCAPE_PREFIX else KEY_PORTRAIT_PREFIX

        params.width = getSavedWidthPx(orientation, expanded)
        params.height = if (expanded) getSavedExpandedHeightPx(orientation) else WindowManager.LayoutParams.WRAP_CONTENT

        val metrics = resources.displayMetrics
        val estHeight = if (params.height > 0) params.height else dpToPx(240)
        val defaultX = ((metrics.widthPixels - params.width) / 2).coerceAtLeast(dpToPx(8))
        val defaultY = if (isLandscape) dpToPx(20) else (metrics.heightPixels - estHeight - dpToPx(88)).coerceAtLeast(dpToPx(20))

        val hasSaved = prefs.getBoolean("${prefix}_saved", false)
        val targetX = if (hasSaved) prefs.getInt("${prefix}_x", defaultX) else defaultX
        val targetY = if (hasSaved) prefs.getInt("${prefix}_y", defaultY) else defaultY

        val maxX = (metrics.widthPixels - params.width - dpToPx(4)).coerceAtLeast(dpToPx(4))
        val maxY = (metrics.heightPixels - estHeight - dpToPx(8)).coerceAtLeast(dpToPx(8))
        params.x = targetX.coerceIn(dpToPx(4), maxX)
        params.y = targetY.coerceIn(dpToPx(8), maxY)
    }

    private fun saveDragCoordinates(orientation: Int, x: Int, y: Int, estHeight: Int, widthPx: Int) {
        val prefix = if (orientation == Configuration.ORIENTATION_LANDSCAPE) KEY_LANDSCAPE_PREFIX else KEY_PORTRAIT_PREFIX
        val metrics = resources.displayMetrics
        val maxX = (metrics.widthPixels - widthPx - dpToPx(4)).coerceAtLeast(dpToPx(4))
        val maxY = (metrics.heightPixels - estHeight - dpToPx(8)).coerceAtLeast(dpToPx(8))
        prefs.edit()
            .putBoolean("${prefix}_saved", true)
            .putInt("${prefix}_x", x.coerceIn(dpToPx(4), maxX))
            .putInt("${prefix}_y", y.coerceIn(dpToPx(8), maxY))
            .apply()
    }

    private fun saveGeometry(orientation: Int, widthPx: Int, heightPx: Int) {
        val prefix = if (orientation == Configuration.ORIENTATION_LANDSCAPE) KEY_LANDSCAPE_PREFIX else KEY_PORTRAIT_PREFIX
        val keyW = if (isDrawerExpanded) "${prefix}_w_exp" else "${prefix}_w_comp"
        val editor = prefs.edit().putInt(keyW, widthPx)
        if (isDrawerExpanded && heightPx > 0) editor.putInt("${prefix}_h_exp", heightPx)
        editor.apply()
    }

    private fun getSavedWidthPx(orientation: Int, expanded: Boolean): Int {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val prefix = if (isLandscape) KEY_LANDSCAPE_PREFIX else KEY_PORTRAIT_PREFIX
        val metrics = resources.displayMetrics
        val maxW = (metrics.widthPixels - dpToPx(12)).coerceAtMost(dpToPx(600))
        val minW = dpToPx(292)
        val defW = dpToPx(if (isLandscape) 400 else 360).coerceIn(minW, maxW)
        val keyW = if (expanded) "${prefix}_w_exp" else "${prefix}_w_comp"
        val fallback = if (expanded) prefs.getInt("${prefix}_w_comp", defW) else defW
        return prefs.getInt(keyW, fallback).coerceIn(minW, maxW)
    }

    private fun getSavedExpandedHeightPx(orientation: Int): Int {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val prefix = if (isLandscape) KEY_LANDSCAPE_PREFIX else KEY_PORTRAIT_PREFIX
        val metrics = resources.displayMetrics
        val maxH = (metrics.heightPixels - dpToPx(32)).coerceAtLeast(dpToPx(280))
        val minH = dpToPx(280)
        val defH = dpToPx(if (isLandscape) 320 else 480).coerceIn(minH, maxH)
        return prefs.getInt("${prefix}_h_exp", defH).coerceIn(minH, maxH)
    }

    private fun ensureMediaControllerConnected() {
        val existing = mediaController
        if (existing != null && existing.isConnected) {
            refreshUiState()
            return
        }
        if (controllerFuture != null) return

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                if (controllerFuture !== future) {
                    MediaController.releaseFuture(future)
                    return@addListener
                }
                val controller = runCatching { future.get() }.getOrNull() ?: run {
                    controllerFuture = null
                    return@addListener
                }
                mediaController?.removeListener(playerListener)
                mediaController = controller
                controller.addListener(playerListener)
                refreshUiState()
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun releaseMediaController() {
        mediaController?.removeListener(playerListener)
        mediaController = null
        val future = controllerFuture
        controllerFuture = null
        if (future != null) runCatching { MediaController.releaseFuture(future) }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val TAG = "MediaAccessibility"
        const val EXTRA_REQUEST_MIC_PERMISSION = "com.music.spotui.EXTRA_REQUEST_MIC_PERMISSION"
        private const val KEY_PORTRAIT_PREFIX = "portrait"
        private const val KEY_LANDSCAPE_PREFIX = "landscape"
        private const val KEY_ACTIVE_TAB = "active_tab"
        private const val KEY_SIDEBAR_COMPACT = "sidebar_compact"
        private const val KEY_BUBBLE_X = "game_trans_bubble_x"
        private const val KEY_BUBBLE_Y = "game_trans_bubble_y"
    }
}
