package com.music.spotui.accessibility

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.TextUtils
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.music.spotui.MainActivity
import com.music.spotui.R
import com.music.spotui.audio.AudioEffectController
import com.music.spotui.audio.EqPresetCatalog
import com.music.spotui.data.preferences.FLOATER_OVERLAY_PREFS_NAME
import com.music.spotui.data.preferences.FloaterHapticStrength
import com.music.spotui.data.preferences.SpatialProfile
import com.music.spotui.data.preferences.dspSettingsUpdates
import com.music.spotui.data.preferences.floaterSettingsUpdates
import com.music.spotui.data.preferences.getBassBoostStrength
import com.music.spotui.data.preferences.getEqBass
import com.music.spotui.data.preferences.getEqPreampDb
import com.music.spotui.data.preferences.getEqPreset
import com.music.spotui.data.preferences.getEqTreble
import com.music.spotui.data.preferences.getEqVocal
import com.music.spotui.data.preferences.getFloaterAutoHideMs
import com.music.spotui.data.preferences.getFloaterHapticStrength
import com.music.spotui.data.preferences.getPlaybackPitchPref
import com.music.spotui.data.preferences.getPlaybackSpeedPref
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
import com.music.spotui.data.preferences.setAudioNormalizerEnabled
import com.music.spotui.data.preferences.setBassBoostStrength
import com.music.spotui.data.preferences.setEqBass
import com.music.spotui.data.preferences.setEqEnabled
import com.music.spotui.data.preferences.setEqPreampDb
import com.music.spotui.data.preferences.setEqSpatialEnabled
import com.music.spotui.data.preferences.setEqTreble
import com.music.spotui.data.preferences.setEqVocal
import com.music.spotui.data.preferences.setFloaterAutoHideMs
import com.music.spotui.data.preferences.setFloaterHapticStrength
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
import com.music.spotui.di.RepeatMode
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.notification.PlaybackService
import com.music.spotui.util.DevConsoleManager
import com.music.spotui.util.DevConsoleManager.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Samsung One UI Game Booster Edition Floating Audio Studio Console.
 *
 * Renders an obsidian glass overlay (`TYPE_ACCESSIBILITY_OVERLAY`) with
 * `canRetrieveWindowContent="false"` and `isAccessibilityTool="true"` so users can control
 * SpotUI seamlessly over full-screen apps and landscape games without `SYSTEM_ALERT_WINDOW`.
 *
 * Key capabilities:
 * - Always renders the real active playing/paused/last song directly via [SongPlayer.getNowPlayingSnapshot]
 *   with [MediaController] backup, live progress bar, quality badge, Favorite heart, Shuffle & Loop.
 * - Direct Play/Pause/Prev/Next routed through [SongPlayer] + [PlaybackService] for zero-fail response.
 * - Dedicated In-App Volume booster (0–100%, OS media volume untouched) + mute/-5%/+5% steps.
 * - Docked Left Sidebar Navigation Rail with organized sections (`MEDIA`, `SOUND`, `PRO`) and 5
 *   interactive tabs (`Player`, `Queue`, `EQ`, `3D`, `Tools`) plus compact width toggle.
 * - Bottom-left & bottom-right corner-drag resizing + tactile [VibratorManager]/[Vibrator] haptics.
 */
class MediaAccessibilityService : AccessibilityService() {

    private enum class FloaterTab(val key: String) {
        PLAYER("player"),
        QUEUE("queue"),
        EQ("eq"),
        SPATIAL_3D("3d"),
        TOOLS("tools"),
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlayScope: CoroutineScope? = null

    private var windowManager: WindowManager? = null
    private val prefs: SharedPreferences by lazy(LazyThreadSafetyMode.NONE) {
        applicationContext.getSharedPreferences(FLOATER_OVERLAY_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var currentOrientation: Int = Configuration.ORIENTATION_UNDEFINED
    private var isDrawerExpanded: Boolean = false
    private var activeTab: FloaterTab = FloaterTab.PLAYER
    private var isSidebarCompact: Boolean = false

    // Header & Now Playing Card
    private var statusBadgeTextView: TextView? = null
    private var coverDiscImageView: ImageView? = null
    private var titleTextView: TextView? = null
    private var artistTextView: TextView? = null
    private var sourceBadgeTextView: TextView? = null
    private var compactProgressBar: ProgressBar? = null
    private var likeButton: ImageButton? = null
    private var openAppButton: ImageButton? = null
    private var fxToggleButton: ImageButton? = null
    private var closeButton: ImageButton? = null

    // Transport Controls
    private var shuffleButton: ImageButton? = null
    private var prevButton: ImageButton? = null
    private var playPauseButton: ImageButton? = null
    private var nextButton: ImageButton? = null
    private var repeatButton: ImageButton? = null

    // In-App Volume
    private var volumeMuteButton: ImageButton? = null
    private var volumeSeekBar: SeekBar? = null
    private var volumePercentTextView: TextView? = null
    private var volumeStepDownButton: TextView? = null
    private var volumeStepUpButton: TextView? = null
    private var isUserSeekingVolume = false
    private var lastUnmutedVolumePct = 100

    // Drawer + Left Sidebar Rail
    private var studioDrawerLayout: LinearLayout? = null
    private var sidebarScrollView: View? = null
    private var btnSidebarCompactToggle: ImageButton? = null
    private var tvSectionMedia: TextView? = null
    private var tvSectionSound: TextView? = null
    private var tvSectionTools: TextView? = null

    private var navTabPlayer: LinearLayout? = null
    private var navTabQueue: LinearLayout? = null
    private var navTabEq: LinearLayout? = null
    private var navTab3d: LinearLayout? = null
    private var navTabTools: LinearLayout? = null

    private var ivNavPlayer: ImageView? = null
    private var ivNavQueue: ImageView? = null
    private var ivNavEq: ImageView? = null
    private var ivNav3d: ImageView? = null
    private var ivNavTools: ImageView? = null

    private var tvNavPlayer: TextView? = null
    private var tvNavQueue: TextView? = null
    private var tvNavEq: TextView? = null
    private var tvNav3d: TextView? = null
    private var tvNavTools: TextView? = null

    private var panelPlayerLayout: LinearLayout? = null
    private var panelQueueLayout: LinearLayout? = null
    private var panelEqLayout: LinearLayout? = null
    private var panel3dLayout: LinearLayout? = null
    private var panelToolsLayout: LinearLayout? = null

    // Tab 1: Player
    private var tvPlayerTabTitle: TextView? = null
    private var tvPlayerTabArtist: TextView? = null
    private var tvPlayerTabSource: TextView? = null
    private var seekPlayerTrack: SeekBar? = null
    private var tvPlayerPosTime: TextView? = null
    private var tvPlayerDurTime: TextView? = null
    private var btnPlayerRew10: TextView? = null
    private var btnPlayerFwd10: TextView? = null
    private var chipPlayerShuffle: TextView? = null
    private var chipPlayerRepeat: TextView? = null
    private var tvPlayerRouteChip: TextView? = null
    private var isUserSeekingTrack = false

    // Tab 2: Queue
    private var tvQueueCount: TextView? = null
    private var btnQueueShuffleUpcoming: TextView? = null
    private var btnQueueDedupe: TextView? = null
    private var containerQueueItems: LinearLayout? = null
    private var lastQueueRenderHash: Int = 0

    // Tab 3: EQ Controls
    private var eqPresetsContainer: LinearLayout? = null
    private var toggleEqView: TextView? = null
    private var seekBassBoost: SeekBar? = null
    private var tvBassBoostVal: TextView? = null
    private var seekEqBass: SeekBar? = null
    private var tvEqBassVal: TextView? = null
    private var seekEqVocal: SeekBar? = null
    private var tvEqVocalVal: TextView? = null
    private var seekEqTreble: SeekBar? = null
    private var tvEqTrebleVal: TextView? = null
    private var seekEqPreamp: SeekBar? = null
    private var tvEqPreampVal: TextView? = null
    private var toggleNormalizer: TextView? = null
    private var toggleLimiter: TextView? = null
    private var btnEqReset: TextView? = null

    // Tab 4: 3D Controls
    private var spatialProfilesContainer: LinearLayout? = null
    private var toggle3dView: TextView? = null
    private var tvRouteInfo: TextView? = null
    private var seekSpatialStrength: SeekBar? = null
    private var tvSpatialStrengthVal: TextView? = null
    private var seekRoomDelay: SeekBar? = null
    private var tvRoomDelayVal: TextView? = null
    private var seekElevation: SeekBar? = null
    private var tvElevationVal: TextView? = null
    private var row8dSpeed: LinearLayout? = null
    private var seek8dSpeed: SeekBar? = null
    private var tv8dSpeedVal: TextView? = null
    private var toggle8d: TextView? = null
    private var toggleCrossfeed: TextView? = null
    private var toggleCorrGuard: TextView? = null

    // Tab 5: Pro Tools
    private var seekToolsSpeed: SeekBar? = null
    private var tvToolsSpeedVal: TextView? = null
    private var seekToolsPitch: SeekBar? = null
    private var tvToolsPitchVal: TextView? = null
    private var btnToolsSpeedReset: TextView? = null
    private var toggleToolsSkipSilence: TextView? = null
    private var tvToolsSleepStatus: TextView? = null
    private var btnSleepOff: TextView? = null
    private var btnSleep10m: TextView? = null
    private var btnSleep15m: TextView? = null
    private var btnSleep30m: TextView? = null
    private var btnSleep60m: TextView? = null
    private var tvToolsAbStatus: TextView? = null
    private var btnAbSetA: TextView? = null
    private var btnAbSetB: TextView? = null
    private var btnAbClear: TextView? = null
    private var btnToolsAutohide: TextView? = null
    private var btnToolsHaptics: TextView? = null

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var accessibilityButtonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoDismissRunnable = Runnable {
        DevConsoleManager.log(LogLevel.INFO, TAG, "One UI Booster Floater auto-dismissed after idle timeout")
        hideOverlay()
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncPlaybackSnapshotToUi()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            syncPlaybackSnapshotToUi()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncPlaybackSnapshotToUi()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        currentOrientation = resources.configuration.orientation
        AudioEffectController.refreshOutputRoute(applicationContext)

        registerAccessibilityButton()
        ensureMediaControllerConnected()

        DevConsoleManager.log(
            LogLevel.INFO,
            TAG,
            "MediaAccessibilityService connected (One UI Game Booster Studio Floater ready)",
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally unused: canRetrieveWindowContent="false" keeps accessibility privacy-safe.
    }

    override fun onInterrupt() {
        hideOverlay()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        currentOrientation = newConfig.orientation
        val view = overlayView ?: return
        val params = overlayParams ?: return
        val wm = windowManager ?: return

        applyOrientationPlacement(params, newConfig.orientation, isDrawerExpanded)
        updateGeometryBadge(params.width, params.height, view)
        runCatching {
            if (view.isAttachedToWindow) {
                wm.updateViewLayout(view, params)
            }
        }.onFailure { err ->
            DevConsoleManager.log(
                LogLevel.WARNING,
                TAG,
                "Failed updating Booster Floater layout on orientation change: ${err.message}",
                err,
            )
        }
    }

    override fun onDestroy() {
        cancelAutoDismissTimer()
        unregisterAccessibilityButton()
        hideOverlay()
        releaseMediaController()
        serviceScope.cancel()
        windowManager = null
        DevConsoleManager.log(LogLevel.INFO, TAG, "MediaAccessibilityService destroyed cleanly")
        super.onDestroy()
    }

    private fun registerAccessibilityButton() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val controller = runCatching { accessibilityButtonController }.getOrNull() ?: return
        val callback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
            override fun onClicked(controller: AccessibilityButtonController) {
                serviceScope.launch {
                    toggleOverlay()
                }
            }

            override fun onAvailabilityChanged(
                controller: AccessibilityButtonController,
                available: Boolean,
            ) {
                DevConsoleManager.log(
                    LogLevel.INFO,
                    TAG,
                    "One UI Accessibility button availability changed: available=$available",
                )
            }
        }
        accessibilityButtonCallback = callback
        runCatching {
            controller.registerAccessibilityButtonCallback(callback)
        }.onFailure { err ->
            DevConsoleManager.log(
                LogLevel.WARNING,
                TAG,
                "Failed registering AccessibilityButtonCallback: ${err.message}",
                err,
            )
        }
    }

    private fun unregisterAccessibilityButton() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val callback = accessibilityButtonCallback ?: return
        accessibilityButtonCallback = null
        runCatching {
            accessibilityButtonController.unregisterAccessibilityButtonCallback(callback)
        }
    }

    private fun toggleOverlay() {
        if (overlayView != null) {
            hideOverlay()
        } else {
            showOverlay()
        }
    }

    @SuppressLint("InflateParams")
    private fun showOverlay() {
        if (overlayView != null) return
        val wm = windowManager ?: (getSystemService(Context.WINDOW_SERVICE) as? WindowManager).also {
            windowManager = it
        } ?: return

        try {
            currentOrientation = resources.configuration.orientation
            val themedContext = ContextThemeWrapper(this, R.style.Theme_Spotui_MiniPlayerOverlay)
            val rootView = LayoutInflater.from(themedContext).inflate(R.layout.layout_mini_player, null)

            val fxAllowed = isFloaterEqAnd3dEnabled(this)
            isDrawerExpanded = isFloaterStartExpanded(this)
            isSidebarCompact = prefs.getBoolean(KEY_SIDEBAR_COMPACT, false)
            activeTab = FloaterTab.entries.firstOrNull {
                it.key == prefs.getString(KEY_ACTIVE_TAB, FloaterTab.PLAYER.key)
            } ?: FloaterTab.PLAYER
            if (!fxAllowed && (activeTab == FloaterTab.EQ || activeTab == FloaterTab.SPATIAL_3D)) {
                activeTab = FloaterTab.PLAYER
            }
            lastQueueRenderHash = 0

            val params = WindowManager.LayoutParams(
                getSavedWidthPx(currentOrientation, isDrawerExpanded),
                if (isDrawerExpanded) getSavedExpandedHeightPx(currentOrientation) else WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            applyOrientationPlacement(params, currentOrientation, isDrawerExpanded)

            bindOverlayViews(rootView, themedContext, params, fxAllowed)
            wm.addView(rootView, params)

            overlayView = rootView
            overlayParams = params
            rootView.post { updateGeometryBadge(params.width, params.height, rootView) }

            performHaptic(rootView, isAccent = false)
            ensureMediaControllerConnected()
            syncPlaybackSnapshotToUi()
            syncVolumeFlowAndUi()
            syncAudioFxUiFromPreferences()
            syncToolsUiFromState(SongPlayer.getNowPlayingSnapshot(this))
            startReactiveCollectors(themedContext, rootView, params)
            resetAutoDismissTimer()

            DevConsoleManager.log(
                LogLevel.INFO,
                TAG,
                "Booster Floater opened (expanded=$isDrawerExpanded, tab=${activeTab.key}, inAppVol=${SongPlayer.getAppVolumePercent(this)}%)",
            )
        } catch (t: Throwable) {
            DevConsoleManager.log(
                LogLevel.ERROR,
                TAG,
                "Failed to inflate/attach Booster Floater overlay: ${t.message}",
                t,
            )
            hideOverlay()
        }
    }

    private fun startReactiveCollectors(
        themedContext: Context,
        rootView: View,
        params: WindowManager.LayoutParams,
    ) {
        overlayScope?.cancel()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        overlayScope = scope

        // Live 400ms ticker ensures Now Playing title, progress bar, Play/Pause, Shuffle/Loop,
        // Queue list, Sleep Timer countdown, and A-B loop stay 100% synced at all times.
        scope.launch {
            while (isActive && overlayView === rootView) {
                syncPlaybackSnapshotToUi()
                delay(400L)
            }
        }

        scope.launch {
            SongPlayer.appVolumePercentFlow.collectLatest { pct ->
                if (!isUserSeekingVolume) {
                    val sb = volumeSeekBar ?: return@collectLatest
                    if (sb.progress != pct) sb.progress = pct
                    volumePercentTextView?.text = "$pct%"
                    if (pct > 0) lastUnmutedVolumePct = pct
                    updateVolumeMuteIcon(pct == 0)
                }
            }
        }
        scope.launch {
            dspSettingsUpdates.collectLatest {
                syncAudioFxUiFromPreferences()
                buildEqPresetChips(themedContext)
                buildSpatialProfileChips(themedContext)
            }
        }
        scope.launch {
            AudioEffectController.routeState.collectLatest { routeInfo ->
                val routeStr = "Output: ${routeInfo.route.label} (${routeInfo.deviceName})"
                tvRouteInfo?.text = routeStr
                tvPlayerRouteChip?.text = routeStr
            }
        }
        scope.launch {
            floaterSettingsUpdates.collectLatest {
                val fxAllowed = isFloaterEqAnd3dEnabled(themedContext)
                navTabEq?.visibility = if (fxAllowed) View.VISIBLE else View.GONE
                navTab3d?.visibility = if (fxAllowed) View.VISIBLE else View.GONE
                tvSectionSound?.visibility = if (fxAllowed && !isSidebarCompact) View.VISIBLE else View.GONE
                if (!fxAllowed && (activeTab == FloaterTab.EQ || activeTab == FloaterTab.SPATIAL_3D)) {
                    switchFloaterTab(FloaterTab.PLAYER)
                }
                val prefix = if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                    KEY_LANDSCAPE_PREFIX
                } else {
                    KEY_PORTRAIT_PREFIX
                }
                if (!prefs.getBoolean("${prefix}_saved", false) && !prefs.contains("${prefix}_w_comp")) {
                    applyOrientationPlacement(params, currentOrientation, isDrawerExpanded)
                    updateGeometryBadge(params.width, params.height, rootView)
                    runCatching {
                        if (rootView.isAttachedToWindow) windowManager?.updateViewLayout(rootView, params)
                    }
                }
                syncToolsUiFromState(SongPlayer.getNowPlayingSnapshot(themedContext))
                resetAutoDismissTimer()
            }
        }
    }

    private fun bindOverlayViews(
        rootView: View,
        themedContext: Context,
        params: WindowManager.LayoutParams,
        fxAllowed: Boolean,
    ) {
        statusBadgeTextView = rootView.findViewById(R.id.tv_mini_status_badge)
        coverDiscImageView = rootView.findViewById(R.id.iv_mini_cover_disc)
        titleTextView = rootView.findViewById<TextView>(R.id.tv_mini_title)?.apply {
            isSelected = true
        }
        artistTextView = rootView.findViewById(R.id.tv_mini_artist)
        sourceBadgeTextView = rootView.findViewById(R.id.tv_mini_source_badge)
        compactProgressBar = rootView.findViewById(R.id.progress_mini_compact)

        likeButton = rootView.findViewById<ImageButton>(R.id.btn_mini_like)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                val nowLiked = SongPlayer.toggleCurrentTrackLike(applicationContext)
                performHaptic(view, isAccent = nowLiked)
                updateLikeButtonUi(nowLiked)
                DevConsoleManager.log(LogLevel.INFO, TAG, "Floater favorite -> liked=$nowLiked")
            }
        }
        openAppButton = rootView.findViewById<ImageButton>(R.id.btn_mini_open_app)?.apply {
            setOnClickListener { view ->
                performHaptic(view, isAccent = true)
                openMainAppAndDismiss()
            }
        }
        fxToggleButton = rootView.findViewById<ImageButton>(R.id.btn_mini_fx_toggle)?.apply {
            setOnClickListener { view ->
                performHaptic(view, isAccent = true)
                setDrawerExpanded(!isDrawerExpanded, rootView, params)
                resetAutoDismissTimer()
            }
        }
        closeButton = rootView.findViewById<ImageButton>(R.id.btn_mini_close)?.apply {
            setOnClickListener { view ->
                performHaptic(view, isAccent = false)
                hideOverlay()
            }
        }

        // Transport Buttons
        shuffleButton = rootView.findViewById<ImageButton>(R.id.btn_mini_shuffle)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                val enabled = SongPlayer.toggleShuffle(applicationContext)
                performHaptic(view, isAccent = enabled)
                syncPlaybackSnapshotToUi()
                DevConsoleManager.log(LogLevel.INFO, TAG, "Floater Shuffle -> $enabled")
            }
        }
        prevButton = rootView.findViewById<ImageButton>(R.id.btn_mini_prev)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                performHaptic(view, isAccent = false)
                handlePreviousClick()
            }
        }
        playPauseButton = rootView.findViewById<ImageButton>(R.id.btn_mini_play_pause)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                performHaptic(view, isAccent = true)
                handlePlayPauseClick()
            }
        }
        nextButton = rootView.findViewById<ImageButton>(R.id.btn_mini_next)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                performHaptic(view, isAccent = false)
                handleNextClick()
            }
        }
        repeatButton = rootView.findViewById<ImageButton>(R.id.btn_mini_repeat)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                val mode = SongPlayer.cycleRepeatMode(applicationContext)
                performHaptic(view, isAccent = mode != RepeatMode.OFF)
                syncPlaybackSnapshotToUi()
                DevConsoleManager.log(LogLevel.INFO, TAG, "Floater Loop mode -> $mode")
            }
        }

        // Dedicated In-App Volume Controls
        volumeMuteButton = rootView.findViewById<ImageButton>(R.id.btn_mini_vol_mute)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                performHaptic(view, isAccent = true)
                toggleMute()
            }
        }
        volumePercentTextView = rootView.findViewById(R.id.tv_mini_volume_pct)
        volumeStepDownButton = rootView.findViewById<TextView>(R.id.btn_mini_vol_down)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                performHaptic(view, isAccent = false)
                adjustVolumeStep(-5)
            }
        }
        volumeStepUpButton = rootView.findViewById<TextView>(R.id.btn_mini_vol_up)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                performHaptic(view, isAccent = false)
                adjustVolumeStep(5)
            }
        }
        volumeSeekBar = rootView.findViewById<SeekBar>(R.id.seek_mini_volume)?.apply {
            max = 100
            var lastTickBucket = progress / 10
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    volumePercentTextView?.text = "$progress%"
                    updateVolumeMuteIcon(progress == 0)
                    if (fromUser) {
                        resetAutoDismissTimer()
                        SongPlayer.setAppVolumePercent(themedContext, progress)
                        val bucket = progress / 10
                        if (bucket != lastTickBucket) {
                            lastTickBucket = bucket
                            performHaptic(seekBar, isAccent = progress == 0 || progress == 100)
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isUserSeekingVolume = true
                    lastTickBucket = (seekBar?.progress ?: 0) / 10
                    cancelAutoDismissTimer()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    isUserSeekingVolume = false
                    performHaptic(seekBar, isAccent = false)
                    DevConsoleManager.log(
                        LogLevel.INFO,
                        TAG,
                        "Floater In-App Volume -> ${seekBar?.progress ?: 0}% (OS untouched)",
                    )
                    resetAutoDismissTimer()
                }
            })
        }

        // Studio Drawer + Left Navigation Rail
        studioDrawerLayout = rootView.findViewById(R.id.layout_studio_drawer)
        sidebarScrollView = rootView.findViewById(R.id.scroll_floater_sidebar)
        tvSectionMedia = rootView.findViewById(R.id.tv_sidebar_section_media)
        tvSectionSound = rootView.findViewById(R.id.tv_sidebar_section_sound)
        tvSectionTools = rootView.findViewById(R.id.tv_sidebar_section_tools)

        btnSidebarCompactToggle = rootView.findViewById<ImageButton>(R.id.btn_sidebar_compact_toggle)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                isSidebarCompact = !isSidebarCompact
                prefs.edit().putBoolean(KEY_SIDEBAR_COMPACT, isSidebarCompact).apply()
                applySidebarCompactState(fxAllowed)
                performHaptic(view, isAccent = true)
            }
        }

        navTabPlayer = rootView.findViewById(R.id.nav_tab_player)
        navTabQueue = rootView.findViewById(R.id.nav_tab_queue)
        navTabEq = rootView.findViewById(R.id.nav_tab_eq)
        navTab3d = rootView.findViewById(R.id.nav_tab_3d)
        navTabTools = rootView.findViewById(R.id.nav_tab_tools)

        ivNavPlayer = rootView.findViewById(R.id.iv_nav_player)
        ivNavQueue = rootView.findViewById(R.id.iv_nav_queue)
        ivNavEq = rootView.findViewById(R.id.iv_nav_eq)
        ivNav3d = rootView.findViewById(R.id.iv_nav_3d)
        ivNavTools = rootView.findViewById(R.id.iv_nav_tools)

        tvNavPlayer = rootView.findViewById(R.id.tv_nav_player)
        tvNavQueue = rootView.findViewById(R.id.tv_nav_queue)
        tvNavEq = rootView.findViewById(R.id.tv_nav_eq)
        tvNav3d = rootView.findViewById(R.id.tv_nav_3d)
        tvNavTools = rootView.findViewById(R.id.tv_nav_tools)

        navTabEq?.visibility = if (fxAllowed) View.VISIBLE else View.GONE
        navTab3d?.visibility = if (fxAllowed) View.VISIBLE else View.GONE

        navTabPlayer?.setOnClickListener { v ->
            resetAutoDismissTimer()
            performHaptic(v, isAccent = false)
            switchFloaterTab(FloaterTab.PLAYER)
        }
        navTabQueue?.setOnClickListener { v ->
            resetAutoDismissTimer()
            performHaptic(v, isAccent = false)
            switchFloaterTab(FloaterTab.QUEUE)
        }
        navTabEq?.setOnClickListener { v ->
            if (!isFloaterEqAnd3dEnabled(themedContext)) return@setOnClickListener
            resetAutoDismissTimer()
            performHaptic(v, isAccent = false)
            switchFloaterTab(FloaterTab.EQ)
        }
        navTab3d?.setOnClickListener { v ->
            if (!isFloaterEqAnd3dEnabled(themedContext)) return@setOnClickListener
            resetAutoDismissTimer()
            performHaptic(v, isAccent = false)
            switchFloaterTab(FloaterTab.SPATIAL_3D)
        }
        navTabTools?.setOnClickListener { v ->
            resetAutoDismissTimer()
            performHaptic(v, isAccent = false)
            switchFloaterTab(FloaterTab.TOOLS)
        }

        panelPlayerLayout = rootView.findViewById(R.id.panel_floater_player)
        panelQueueLayout = rootView.findViewById(R.id.panel_floater_queue)
        panelEqLayout = rootView.findViewById(R.id.panel_floater_eq)
        panel3dLayout = rootView.findViewById(R.id.panel_floater_3d)
        panelToolsLayout = rootView.findViewById(R.id.panel_floater_tools)

        eqPresetsContainer = rootView.findViewById(R.id.container_eq_presets)
        spatialProfilesContainer = rootView.findViewById(R.id.container_spatial_profiles)

        bindPlayerTabControls(rootView)
        bindQueueTabControls(rootView)
        buildEqPresetChips(themedContext)
        buildSpatialProfileChips(themedContext)
        bindEqAnd3dControls(rootView, themedContext)
        bindToolsTabControls(rootView, themedContext)

        applySidebarCompactState(fxAllowed)
        switchFloaterTab(activeTab)

        // Dismiss on outside touch
        rootView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                hideOverlay()
                true
            } else {
                false
            }
        }

        // Panning gesture on header pill bar and top track strip
        val touchSlop = ViewConfiguration.get(themedContext).scaledTouchSlop
        val dragPillArea = rootView.findViewById<View>(R.id.layout_drag_pill_area)
        val topBarView = rootView.findViewById<View>(R.id.layout_top_bar)
        val trackInfoView = rootView.findViewById<View>(R.id.layout_track_info)
        val dragListener = createDragTouchListener(params, rootView, touchSlop)
        dragPillArea?.setOnTouchListener(dragListener)
        topBarView?.setOnTouchListener(dragListener)
        trackInfoView?.setOnTouchListener(dragListener)

        // Bottom-Right & Bottom-Left Corner Resize Grips
        val resizeBr = rootView.findViewById<View>(R.id.resize_corner_br)
        val resizeBl = rootView.findViewById<View>(R.id.resize_corner_bl)
        val resizeListener = createResizeTouchListener(params, rootView, themedContext)
        resizeBr?.setOnTouchListener(resizeListener)
        resizeBl?.setOnTouchListener(resizeListener)

        studioDrawerLayout?.visibility = if (isDrawerExpanded) View.VISIBLE else View.GONE
        updateFxToggleButtonTint()
        updateGeometryBadge(params.width, params.height, rootView)
    }

    private fun bindPlayerTabControls(rootView: View) {
        tvPlayerTabTitle = rootView.findViewById(R.id.tv_player_tab_title)
        tvPlayerTabArtist = rootView.findViewById(R.id.tv_player_tab_artist)
        tvPlayerTabSource = rootView.findViewById(R.id.tv_player_tab_source)
        tvPlayerPosTime = rootView.findViewById(R.id.tv_player_pos_time)
        tvPlayerDurTime = rootView.findViewById(R.id.tv_player_dur_time)
        tvPlayerRouteChip = rootView.findViewById(R.id.tv_player_route_chip)

        seekPlayerTrack = rootView.findViewById<SeekBar>(R.id.seek_floater_track)?.apply {
            max = 1000
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        resetAutoDismissTimer()
                        val dur = SongPlayer.getNowPlayingSnapshot(applicationContext).durationMs
                        if (dur > 0L) {
                            tvPlayerPosTime?.text = formatDurationMs((dur * progress) / 1000L)
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isUserSeekingTrack = true
                    cancelAutoDismissTimer()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    isUserSeekingTrack = false
                    val progress = seekBar?.progress ?: 0
                    val dur = SongPlayer.getNowPlayingSnapshot(applicationContext).durationMs
                    if (dur > 0L) {
                        val targetMs = (dur * progress) / 1000L
                        SongPlayer.seekTo(targetMs)
                        mediaController?.takeIf { it.isConnected }?.seekTo(targetMs)
                        performHaptic(seekBar, isAccent = false)
                    }
                    resetAutoDismissTimer()
                }
            })
        }

        btnPlayerRew10 = rootView.findViewById<TextView>(R.id.btn_player_rew10)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                performHaptic(view, isAccent = false)
                val cur = SongPlayer.getNowPlayingSnapshot(applicationContext).positionMs
                val target = (cur - 10_000L).coerceAtLeast(0L)
                SongPlayer.seekTo(target)
                syncPlaybackSnapshotToUi()
            }
        }
        btnPlayerFwd10 = rootView.findViewById<TextView>(R.id.btn_player_fwd10)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                performHaptic(view, isAccent = false)
                val snap = SongPlayer.getNowPlayingSnapshot(applicationContext)
                val target = if (snap.durationMs > 0L) {
                    (snap.positionMs + 10_000L).coerceAtMost(snap.durationMs)
                } else {
                    snap.positionMs + 10_000L
                }
                SongPlayer.seekTo(target)
                syncPlaybackSnapshotToUi()
            }
        }
        chipPlayerShuffle = rootView.findViewById<TextView>(R.id.chip_player_shuffle)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                val enabled = SongPlayer.toggleShuffle(applicationContext)
                performHaptic(view, isAccent = enabled)
                syncPlaybackSnapshotToUi()
            }
        }
        chipPlayerRepeat = rootView.findViewById<TextView>(R.id.chip_player_repeat)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                val mode = SongPlayer.cycleRepeatMode(applicationContext)
                performHaptic(view, isAccent = mode != RepeatMode.OFF)
                syncPlaybackSnapshotToUi()
            }
        }
    }

    private fun bindQueueTabControls(rootView: View) {
        tvQueueCount = rootView.findViewById(R.id.tv_queue_count)
        containerQueueItems = rootView.findViewById(R.id.container_queue_items)

        btnQueueShuffleUpcoming = rootView.findViewById<TextView>(R.id.btn_queue_shuffle_upcoming)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                val count = SongPlayer.shuffleUpcomingQueue()
                performHaptic(view, isAccent = count > 0)
                lastQueueRenderHash = 0
                syncPlaybackSnapshotToUi()
                DevConsoleManager.log(LogLevel.INFO, TAG, "Floater shuffled $count upcoming tracks")
            }
        }
        btnQueueDedupe = rootView.findViewById<TextView>(R.id.btn_queue_dedupe)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                val removed = SongPlayer.deduplicateQueue()
                performHaptic(view, isAccent = removed > 0)
                lastQueueRenderHash = 0
                syncPlaybackSnapshotToUi()
                DevConsoleManager.log(LogLevel.INFO, TAG, "Floater deduped $removed duplicate tracks")
            }
        }
    }

    private fun bindToolsTabControls(rootView: View, ctx: Context) {
        seekToolsSpeed = rootView.findViewById<SeekBar>(R.id.seek_tools_speed)?.apply {
            max = 15 // 0.5x to 2.0x in 0.1x steps
            setOnSeekBarChangeListener(createSimpleSeekBarListener(
                onChanged = { p ->
                    val speed = 0.5f + (p * 0.1f)
                    setPlaybackSpeedPref(ctx, speed)
                    SongPlayer.refreshPlaybackTuning(ctx)
                    tvToolsSpeedVal?.text = String.format(Locale.US, "%.1fx", speed)
                },
            ))
        }
        tvToolsSpeedVal = rootView.findViewById(R.id.tv_tools_speed_val)

        seekToolsPitch = rootView.findViewById<SeekBar>(R.id.seek_tools_pitch)?.apply {
            max = 10 // 0.50x to 1.50x in 0.10x steps
            setOnSeekBarChangeListener(createSimpleSeekBarListener(
                onChanged = { p ->
                    val pitch = 0.5f + (p * 0.1f)
                    setPlaybackPitchPref(ctx, pitch)
                    SongPlayer.refreshPlaybackTuning(ctx)
                    tvToolsPitchVal?.text = String.format(Locale.US, "%.2fx", pitch)
                },
            ))
        }
        tvToolsPitchVal = rootView.findViewById(R.id.tv_tools_pitch_val)

        btnToolsSpeedReset = rootView.findViewById<TextView>(R.id.btn_tools_speed_reset)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                performHaptic(view, isAccent = true)
                setPlaybackSpeedPref(ctx, 1.0f)
                setPlaybackPitchPref(ctx, 1.0f)
                setSkipSilenceEnabled(ctx, false)
                SongPlayer.refreshPlaybackTuning(ctx)
                syncToolsUiFromState(SongPlayer.getNowPlayingSnapshot(ctx))
            }
        }

        toggleToolsSkipSilence = rootView.findViewById<TextView>(R.id.toggle_tools_skip_silence)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                val next = !isSkipSilenceEnabled(ctx)
                setSkipSilenceEnabled(ctx, next)
                SongPlayer.refreshPlaybackTuning(ctx)
                performHaptic(view, isAccent = next)
                syncToolsUiFromState(SongPlayer.getNowPlayingSnapshot(ctx))
            }
        }

        tvToolsSleepStatus = rootView.findViewById(R.id.tv_tools_sleep_status)
        btnSleepOff = rootView.findViewById<TextView>(R.id.btn_sleep_off)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                SongPlayer.cancelSleepTimer()
                performHaptic(view, isAccent = false)
                syncToolsUiFromState(SongPlayer.getNowPlayingSnapshot(ctx))
            }
        }
        btnSleep10m = rootView.findViewById<TextView>(R.id.btn_sleep_10m)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                SongPlayer.setSleepTimer(10 * 60_000L)
                performHaptic(view, isAccent = true)
                syncToolsUiFromState(SongPlayer.getNowPlayingSnapshot(ctx))
            }
        }
        btnSleep15m = rootView.findViewById<TextView>(R.id.btn_sleep_15m)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                SongPlayer.setSleepTimer(15 * 60_000L)
                performHaptic(view, isAccent = true)
                syncToolsUiFromState(SongPlayer.getNowPlayingSnapshot(ctx))
            }
        }
        btnSleep30m = rootView.findViewById<TextView>(R.id.btn_sleep_30m)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                SongPlayer.setSleepTimer(30 * 60_000L)
                performHaptic(view, isAccent = true)
                syncToolsUiFromState(SongPlayer.getNowPlayingSnapshot(ctx))
            }
        }
        btnSleep60m = rootView.findViewById<TextView>(R.id.btn_sleep_60m)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                SongPlayer.setSleepTimer(60 * 60_000L)
                performHaptic(view, isAccent = true)
                syncToolsUiFromState(SongPlayer.getNowPlayingSnapshot(ctx))
            }
        }

        tvToolsAbStatus = rootView.findViewById(R.id.tv_tools_ab_status)
        btnAbSetA = rootView.findViewById<TextView>(R.id.btn_ab_set_a)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                SongPlayer.setAbLoopStart()
                performHaptic(view, isAccent = true)
                syncToolsUiFromState(SongPlayer.getNowPlayingSnapshot(ctx))
            }
        }
        btnAbSetB = rootView.findViewById<TextView>(R.id.btn_ab_set_b)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                SongPlayer.setAbLoopEnd()
                performHaptic(view, isAccent = true)
                syncToolsUiFromState(SongPlayer.getNowPlayingSnapshot(ctx))
            }
        }
        btnAbClear = rootView.findViewById<TextView>(R.id.btn_ab_clear)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                SongPlayer.clearAbLoop()
                performHaptic(view, isAccent = false)
                syncToolsUiFromState(SongPlayer.getNowPlayingSnapshot(ctx))
            }
        }

        btnToolsAutohide = rootView.findViewById<TextView>(R.id.btn_tools_autohide)?.apply {
            setOnClickListener { view ->
                val options = longArrayOf(0L, 8_000L, 12_000L, 20_000L, 45_000L)
                val cur = getFloaterAutoHideMs(ctx)
                val idx = options.indexOfFirst { it == cur }
                val next = options[(idx + 1) % options.size]
                setFloaterAutoHideMs(ctx, next)
                performHaptic(view, isAccent = true)
                resetAutoDismissTimer()
                syncToolsUiFromState(SongPlayer.getNowPlayingSnapshot(ctx))
            }
        }
        btnToolsHaptics = rootView.findViewById<TextView>(R.id.btn_tools_haptics)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                val enabled = isFloaterHapticsEnabled(ctx)
                val strength = getFloaterHapticStrength(ctx)
                when {
                    !enabled -> {
                        setFloaterHapticsEnabled(ctx, true)
                        setFloaterHapticStrength(ctx, FloaterHapticStrength.LIGHT)
                    }
                    strength == FloaterHapticStrength.LIGHT -> setFloaterHapticStrength(ctx, FloaterHapticStrength.MEDIUM)
                    strength == FloaterHapticStrength.MEDIUM -> setFloaterHapticStrength(ctx, FloaterHapticStrength.STRONG)
                    else -> setFloaterHapticsEnabled(ctx, false)
                }
                performHaptic(view, isAccent = true)
                syncToolsUiFromState(SongPlayer.getNowPlayingSnapshot(ctx))
            }
        }
    }

    private fun switchFloaterTab(tab: FloaterTab) {
        activeTab = tab
        prefs.edit().putString(KEY_ACTIVE_TAB, tab.key).apply()

        panelPlayerLayout?.visibility = if (tab == FloaterTab.PLAYER) View.VISIBLE else View.GONE
        panelQueueLayout?.visibility = if (tab == FloaterTab.QUEUE) View.VISIBLE else View.GONE
        panelEqLayout?.visibility = if (tab == FloaterTab.EQ) View.VISIBLE else View.GONE
        panel3dLayout?.visibility = if (tab == FloaterTab.SPATIAL_3D) View.VISIBLE else View.GONE
        panelToolsLayout?.visibility = if (tab == FloaterTab.TOOLS) View.VISIBLE else View.GONE

        styleNavItem(navTabPlayer, ivNavPlayer, tvNavPlayer, tab == FloaterTab.PLAYER)
        styleNavItem(navTabQueue, ivNavQueue, tvNavQueue, tab == FloaterTab.QUEUE)
        styleNavItem(navTabEq, ivNavEq, tvNavEq, tab == FloaterTab.EQ)
        styleNavItem(navTab3d, ivNav3d, tvNav3d, tab == FloaterTab.SPATIAL_3D)
        styleNavItem(navTabTools, ivNavTools, tvNavTools, tab == FloaterTab.TOOLS)

        if (tab == FloaterTab.QUEUE) {
            lastQueueRenderHash = 0
            syncPlaybackSnapshotToUi()
        }
    }

    private fun styleNavItem(
        container: LinearLayout?,
        icon: ImageView?,
        label: TextView?,
        selected: Boolean,
    ) {
        container?.background = if (selected) {
            ContextCompat.getDrawable(this, R.drawable.bg_mini_nav_item_active)
        } else {
            null
        }
        val tintColor = if (selected) Color.parseColor("#FF1ED760") else Color.parseColor("#FFB8C2D4")
        icon?.imageTintList = ColorStateList.valueOf(tintColor)
        label?.setTextColor(tintColor)
    }

    private fun applySidebarCompactState(fxAllowed: Boolean) {
        val sidebar = sidebarScrollView ?: return
        val lp = sidebar.layoutParams
        lp.width = dpToPx(if (isSidebarCompact) 48 else 72)
        sidebar.layoutParams = lp

        val labelVis = if (isSidebarCompact) View.GONE else View.VISIBLE
        tvNavPlayer?.visibility = labelVis
        tvNavQueue?.visibility = labelVis
        tvNavEq?.visibility = labelVis
        tvNav3d?.visibility = labelVis
        tvNavTools?.visibility = labelVis

        tvSectionMedia?.visibility = labelVis
        tvSectionSound?.visibility = if (!isSidebarCompact && fxAllowed) View.VISIBLE else View.GONE
        tvSectionTools?.visibility = labelVis
        btnSidebarCompactToggle?.imageTintList = ColorStateList.valueOf(
            if (isSidebarCompact) Color.parseColor("#FF1ED760") else Color.parseColor("#FF9CA6B8"),
        )
    }

    private fun setDrawerExpanded(
        expanded: Boolean,
        rootView: View,
        params: WindowManager.LayoutParams,
    ) {
        if (isDrawerExpanded == expanded) return

        isDrawerExpanded = expanded
        studioDrawerLayout?.visibility = if (expanded) View.VISIBLE else View.GONE
        updateFxToggleButtonTint()

        params.width = getSavedWidthPx(currentOrientation, expanded)
        params.height = if (expanded) {
            getSavedExpandedHeightPx(currentOrientation)
        } else {
            WindowManager.LayoutParams.WRAP_CONTENT
        }

        val estHeight = if (params.height > 0) params.height else dpToPx(236)
        val (clampedX, clampedY) = clampCoordinatesToDisplay(params.x, params.y, params.width, estHeight)
        params.x = clampedX
        params.y = clampedY

        runCatching {
            if (rootView.isAttachedToWindow) {
                windowManager?.updateViewLayout(rootView, params)
            }
        }
        rootView.post { updateGeometryBadge(params.width, params.height, rootView) }
    }

    private fun updateFxToggleButtonTint() {
        val btn = fxToggleButton ?: return
        val tintColor = if (isDrawerExpanded) Color.parseColor("#FF1ED760") else Color.WHITE
        btn.imageTintList = ColorStateList.valueOf(tintColor)
    }

    private fun syncPlaybackSnapshotToUi() {
        if (overlayView == null) return

        val snap = SongPlayer.getNowPlayingSnapshot(applicationContext)
        val ctrl = mediaController?.takeIf { it.isConnected }
        val ctrlMeta = ctrl?.mediaMetadata?.takeIf { !it.title.isNullOrBlank() }
            ?: ctrl?.currentMediaItem?.mediaMetadata

        val displayTitle = snap.title.takeIf { it.isNotBlank() }
            ?: ctrlMeta?.title?.toString()?.takeIf { it.isNotBlank() }
            ?: getString(R.string.mini_player_idle_title)
        val displayArtist = snap.artist.takeIf { it.isNotBlank() }
            ?: ctrlMeta?.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: ctrlMeta?.albumArtist?.toString()?.takeIf { it.isNotBlank() }
            ?: getString(R.string.mini_player_idle_artist)

        val playingNow = snap.isPlaying || (ctrl?.isPlaying == true)

        if (titleTextView?.text?.toString() != displayTitle) {
            titleTextView?.text = displayTitle
        }
        titleTextView?.isSelected = true
        if (artistTextView?.text?.toString() != displayArtist) {
            artistTextView?.text = displayArtist
        }

        // Source/Status pill badge on main card
        val srcBadgeText = when {
            snap.isResolving -> "SYNCING"
            snap.qualityLabel.isNotBlank() && snap.qualityLabel != "—" ->
                "${snap.sourceLabel.uppercase(Locale.US)} · ${snap.qualityLabel.uppercase(Locale.US)}"
            playingNow -> "PLAYING · ${snap.sourceLabel.uppercase(Locale.US)}"
            snap.title.isNotBlank() -> "PAUSED"
            else -> "READY"
        }
        sourceBadgeTextView?.apply {
            text = srcBadgeText
            setTextColor(
                when {
                    playingNow || snap.isResolving -> Color.parseColor("#FF1ED760")
                    snap.title.isNotBlank() -> Color.parseColor("#FF8BE9FD")
                    else -> Color.parseColor("#FFB8C0CE")
                },
            )
        }

        coverDiscImageView?.imageTintList = ColorStateList.valueOf(
            if (playingNow) Color.parseColor("#FF1ED760") else Color.parseColor("#FF9AA5BA"),
        )

        updatePlayPauseIcon(playingNow)
        updateLikeButtonUi(snap.isLiked)
        updateShuffleButtonUi(snap.isShuffled)
        updateRepeatButtonUi(snap.repeatMode)

        // Compact + Expanded progress bars
        val posMs = snap.positionMs.coerceAtLeast(0L)
        val durMs = snap.durationMs.coerceAtLeast(0L)
        val progressRatio = if (durMs > 0L) ((posMs * 1000L) / durMs).toInt().coerceIn(0, 1000) else 0
        compactProgressBar?.progress = progressRatio

        if (!isUserSeekingTrack) {
            seekPlayerTrack?.progress = progressRatio
            tvPlayerPosTime?.text = formatDurationMs(posMs)
        }
        tvPlayerDurTime?.text = formatDurationMs(durMs)
        tvPlayerTabTitle?.text = displayTitle
        tvPlayerTabArtist?.text = if (snap.album.isNotBlank()) "$displayArtist · ${snap.album}" else displayArtist
        tvPlayerTabSource?.text = "Engine: ${snap.sourceLabel} · ${snap.qualityLabel}"

        chipPlayerShuffle?.apply {
            text = if (snap.isShuffled) "Shuffle ON" else "Shuffle OFF"
            setBackgroundResource(if (snap.isShuffled) R.drawable.bg_mini_chip_active else R.drawable.bg_mini_chip_inactive)
            setTextColor(if (snap.isShuffled) Color.parseColor("#FF0B0E11") else Color.WHITE)
        }
        chipPlayerRepeat?.apply {
            text = when (snap.repeatMode) {
                RepeatMode.OFF -> "Loop OFF"
                RepeatMode.ALL -> "Loop ALL"
                RepeatMode.ONE -> "Loop ONE"
            }
            val active = snap.repeatMode != RepeatMode.OFF
            setBackgroundResource(if (active) R.drawable.bg_mini_chip_active else R.drawable.bg_mini_chip_inactive)
            setTextColor(if (active) Color.parseColor("#FF0B0E11") else Color.WHITE)
        }

        val routeInfo = AudioEffectController.routeState.value
        val routeText = "Output: ${routeInfo.route.label} (${routeInfo.deviceName})"
        if (tvPlayerRouteChip?.text?.toString() != routeText) {
            tvPlayerRouteChip?.text = routeText
        }

        if (activeTab == FloaterTab.QUEUE) {
            renderQueueIfNeeded(snap.queue, snap.activeQueueIndex)
        }
        if (activeTab == FloaterTab.TOOLS) {
            syncToolsUiFromState(snap)
        }
    }

    private fun renderQueueIfNeeded(
        queue: List<com.music.spotui.data.entity.SongsModel>,
        activeIndex: Int,
    ) {
        val container = containerQueueItems ?: return
        val hash = queue.hashCode() * 31 + activeIndex
        if (hash == lastQueueRenderHash) return
        lastQueueRenderHash = hash

        tvQueueCount?.text = "Up Next (${queue.size})"
        container.removeAllViews()

        if (queue.isEmpty()) {
            val emptyTv = TextView(this).apply {
                setPadding(dpToPx(12), dpToPx(14), dpToPx(12), dpToPx(14))
                setBackgroundResource(R.drawable.bg_mini_card_inner)
                text = "Queue is empty. Play a song or playlist in SpotUI."
                textSize = 11f
                setTextColor(Color.parseColor("#FFB8C2D4"))
            }
            container.addView(emptyTv)
            return
        }

        val startIdx = (activeIndex - 1).coerceAtLeast(0)
        val endIdx = (startIdx + 24).coerceAtMost(queue.lastIndex)
        for (i in startIdx..endIdx) {
            val song = queue[i]
            val isCurrent = i == activeIndex
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = dpToPx(5)
                }
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(
                    if (isCurrent) R.drawable.bg_gb_now_playing_card else R.drawable.bg_mini_card_inner,
                )
                setPadding(dpToPx(10), dpToPx(7), dpToPx(10), dpToPx(7))
                setOnClickListener { view ->
                    resetAutoDismissTimer()
                    performHaptic(view, isAccent = true)
                    SongPlayer.playQueueItemAt(applicationContext, i)
                    lastQueueRenderHash = 0
                    mainHandler.postDelayed({ syncPlaybackSnapshotToUi() }, 180L)
                }
            }

            val indexTv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(24), LinearLayout.LayoutParams.WRAP_CONTENT)
                text = if (isCurrent) "▶" else "${i + 1}"
                textSize = 10.5f
                setTextColor(if (isCurrent) Color.parseColor("#FF1ED760") else Color.parseColor("#FF8892A6"))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            val textCol = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, dpToPx(6), 0)
            }
            val songTitleTv = TextView(this).apply {
                text = song.title.ifBlank { "Track ${i + 1}" }
                textSize = 11.5f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(if (isCurrent) Color.parseColor("#FF1ED760") else Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            val songArtistTv = TextView(this).apply {
                text = song.singer.ifBlank { song.album }
                textSize = 9.5f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(Color.parseColor("#FFB8C2D4"))
            }
            textCol.addView(songTitleTv)
            textCol.addView(songArtistTv)

            val durTv = TextView(this).apply {
                text = if (song.durationMs > 0) formatDurationMs(song.durationMs.toLong()) else ""
                textSize = 9.5f
                setTextColor(Color.parseColor("#FF8892A6"))
            }

            row.addView(indexTv)
            row.addView(textCol)
            if (song.durationMs > 0) row.addView(durTv)
            container.addView(row)
        }
    }

    private fun syncToolsUiFromState(snap: SongPlayer.FloaterPlaybackSnapshot) {
        val ctx = this
        val speed = getPlaybackSpeedPref(ctx)
        seekToolsSpeed?.progress = ((speed - 0.5f) / 0.1f).roundToInt().coerceIn(0, 15)
        tvToolsSpeedVal?.text = String.format(Locale.US, "%.1fx", speed)

        val pitch = getPlaybackPitchPref(ctx)
        seekToolsPitch?.progress = ((pitch - 0.5f) / 0.1f).roundToInt().coerceIn(0, 10)
        tvToolsPitchVal?.text = String.format(Locale.US, "%.2fx", pitch)

        val skipSil = isSkipSilenceEnabled(ctx)
        toggleToolsSkipSilence?.apply {
            text = if (skipSil) "Skip Silence: ON" else "Skip Silence: OFF"
            setBackgroundResource(if (skipSil) R.drawable.bg_mini_chip_active else R.drawable.bg_mini_chip_inactive)
            setTextColor(if (skipSil) Color.parseColor("#FF0B0E11") else Color.WHITE)
        }

        val rem = snap.sleepTimerRemainingMs
        tvToolsSleepStatus?.text = if (rem > 0L) {
            "Sleep Timer: ${formatDurationMs(rem)} remaining"
        } else {
            "Sleep Timer: Off"
        }

        val aMs = snap.abLoopStartMs
        val bMs = snap.abLoopEndMs
        tvToolsAbStatus?.text = when {
            aMs >= 0L && bMs > aMs -> "A-B Loop Active: ${formatDurationMs(aMs)} → ${formatDurationMs(bMs)}"
            aMs >= 0L -> "A-B Loop: A=${formatDurationMs(aMs)} (tap Set B)"
            else -> "A-B Loop: Not set"
        }

        val autoMs = getFloaterAutoHideMs(ctx)
        btnToolsAutohide?.text = if (autoMs == 0L) "Auto-Hide: Off" else "Auto-Hide: ${autoMs / 1000L}s"

        val hapticsLabel = if (!isFloaterHapticsEnabled(ctx)) {
            "Haptics: Off"
        } else {
            "Haptics: ${getFloaterHapticStrength(ctx).label}"
        }
        btnToolsHaptics?.text = hapticsLabel
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        val button = playPauseButton ?: return
        button.setImageResource(
            if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )
        button.contentDescription = if (isPlaying) "Pause" else "Play"
    }

    private fun updateLikeButtonUi(liked: Boolean) {
        val btn = likeButton ?: return
        btn.setImageResource(if (liked) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border)
        btn.imageTintList = ColorStateList.valueOf(
            if (liked) Color.parseColor("#FF1ED760") else Color.parseColor("#E6FFFFFF"),
        )
    }

    private fun updateShuffleButtonUi(shuffled: Boolean) {
        val btn = shuffleButton ?: return
        btn.setImageResource(if (shuffled) R.drawable.ic_player_shuffle else R.drawable.ic_shuffle_off)
        btn.imageTintList = ColorStateList.valueOf(
            if (shuffled) Color.parseColor("#FF1ED760") else Color.parseColor("#CCFFFFFF"),
        )
    }

    private fun updateRepeatButtonUi(mode: RepeatMode) {
        val btn = repeatButton ?: return
        val resId = when (mode) {
            RepeatMode.OFF -> R.drawable.ic_repeat_off
            RepeatMode.ALL -> R.drawable.ic_repeat
            RepeatMode.ONE -> R.drawable.ic_repeat_one
        }
        btn.setImageResource(resId)
        btn.imageTintList = ColorStateList.valueOf(
            if (mode == RepeatMode.OFF) Color.parseColor("#CCFFFFFF") else Color.parseColor("#FF1ED760"),
        )
    }

    private fun handlePlayPauseClick() {
        val wasPlaying = SongPlayer.isPlaybackActiveNow() || (mediaController?.isPlaying == true)
        SongPlayer.togglePlayOrResume(applicationContext)
        // Optimistic immediate flip + follow-up real state reconciliation
        updatePlayPauseIcon(!wasPlaying)
        mainHandler.postDelayed({ syncPlaybackSnapshotToUi() }, 180L)
        mainHandler.postDelayed({ syncPlaybackSnapshotToUi() }, 650L)
        DevConsoleManager.log(LogLevel.INFO, TAG, "Floater Play/Pause clicked (wasPlaying=$wasPlaying)")
    }

    private fun handleNextClick() {
        SongPlayer.triggerNext(applicationContext)
        mainHandler.postDelayed({ syncPlaybackSnapshotToUi() }, 200L)
        mainHandler.postDelayed({ syncPlaybackSnapshotToUi() }, 700L)
    }

    private fun handlePreviousClick() {
        SongPlayer.triggerPrevious(applicationContext)
        mainHandler.postDelayed({ syncPlaybackSnapshotToUi() }, 200L)
        mainHandler.postDelayed({ syncPlaybackSnapshotToUi() }, 700L)
    }

    private fun syncVolumeFlowAndUi() {
        val pct = SongPlayer.getAppVolumePercent(this)
        volumeSeekBar?.progress = pct
        volumePercentTextView?.text = "$pct%"
        if (pct > 0) lastUnmutedVolumePct = pct
        updateVolumeMuteIcon(pct == 0)
    }

    private fun toggleMute() {
        val cur = SongPlayer.getAppVolumePercent(this)
        if (cur > 0) {
            lastUnmutedVolumePct = cur
            SongPlayer.setAppVolumePercent(this, 0)
            updateVolumeMuteIcon(muted = true)
            DevConsoleManager.log(LogLevel.INFO, TAG, "Floater muted In-App audio (prev=$cur%)")
        } else {
            val target = lastUnmutedVolumePct.coerceIn(10, 100)
            SongPlayer.setAppVolumePercent(this, target)
            updateVolumeMuteIcon(muted = false)
            DevConsoleManager.log(LogLevel.INFO, TAG, "Floater unmuted In-App audio -> $target%")
        }
    }

    private fun adjustVolumeStep(delta: Int) {
        val cur = SongPlayer.getAppVolumePercent(this)
        val next = (cur + delta).coerceIn(0, 100)
        SongPlayer.setAppVolumePercent(this, next)
        DevConsoleManager.log(LogLevel.INFO, TAG, "Floater stepped In-App Volume -> $next%")
    }

    private fun updateVolumeMuteIcon(muted: Boolean) {
        val btn = volumeMuteButton ?: return
        btn.setImageResource(
            if (muted) R.drawable.ic_mini_vol_off else R.drawable.ic_mini_vol_up,
        )
        btn.imageTintList = ColorStateList.valueOf(
            if (muted) Color.parseColor("#FFFF6B6B") else Color.parseColor("#FF1ED760"),
        )
    }

    private fun buildEqPresetChips(context: Context) {
        val container = eqPresetsContainer ?: return
        container.removeAllViews()
        val currentPreset = getEqPreset(context)

        EqPresetCatalog.presets.forEach { preset ->
            val chip = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(28),
                ).apply {
                    marginEnd = dpToPx(6)
                }
                setPadding(dpToPx(12), 0, dpToPx(12), 0)
                gravity = Gravity.CENTER
                textSize = 10.5f
                text = preset.name
                val isSelected = preset.name.equals(currentPreset, ignoreCase = true)
                setBackgroundResource(
                    if (isSelected) R.drawable.bg_mini_chip_active else R.drawable.bg_mini_chip_inactive,
                )
                setTextColor(if (isSelected) Color.parseColor("#FF0B0E11") else Color.WHITE)
                setOnClickListener { view ->
                    resetAutoDismissTimer()
                    performHaptic(view, isAccent = true)
                    EqPresetCatalog.applyPreset(context, preset)
                    SongPlayer.refreshAudioEffects(context)
                    buildEqPresetChips(context)
                    syncAudioFxUiFromPreferences()
                    DevConsoleManager.log(LogLevel.INFO, TAG, "Floater applied EQ preset '${preset.name}'")
                }
            }
            container.addView(chip)
        }
    }

    private fun buildSpatialProfileChips(context: Context) {
        val container = spatialProfilesContainer ?: return
        container.removeAllViews()
        val currentProfile = getSpatialProfile(context)

        SpatialProfile.entries.forEach { profile ->
            val chip = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(28),
                ).apply {
                    marginEnd = dpToPx(6)
                }
                setPadding(dpToPx(10), 0, dpToPx(10), 0)
                gravity = Gravity.CENTER
                textSize = 10f
                text = profile.label
                val isSelected = profile == currentProfile
                setBackgroundResource(
                    if (isSelected) R.drawable.bg_mini_chip_active else R.drawable.bg_mini_chip_inactive,
                )
                setTextColor(if (isSelected) Color.parseColor("#FF0B0E11") else Color.WHITE)
                setOnClickListener { view ->
                    resetAutoDismissTimer()
                    performHaptic(view, isAccent = true)
                    setSpatialProfile(context, profile)
                    SongPlayer.refreshAudioEffects(context)
                    buildSpatialProfileChips(context)
                    syncAudioFxUiFromPreferences()
                    DevConsoleManager.log(LogLevel.INFO, TAG, "Floater switched 3D profile -> ${profile.label}")
                }
            }
            container.addView(chip)
        }
    }

    private fun bindEqAnd3dControls(rootView: View, ctx: Context) {
        toggleEqView = rootView.findViewById<TextView>(R.id.toggle_floater_eq)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                val next = !isEqEnabled(ctx)
                setEqEnabled(ctx, next)
                SongPlayer.refreshAudioEffects(ctx)
                syncAudioFxUiFromPreferences()
                performHaptic(view, isAccent = next)
                DevConsoleManager.log(LogLevel.INFO, TAG, "Floater EQ ${if (next) "ENABLED" else "DISABLED"}")
            }
        }
        seekBassBoost = rootView.findViewById<SeekBar>(R.id.seek_floater_bass_boost)?.apply {
            max = 1000
            setOnSeekBarChangeListener(createSimpleSeekBarListener(
                onChanged = { p ->
                    setBassBoostStrength(ctx, p)
                    SongPlayer.refreshAudioEffects(ctx)
                    tvBassBoostVal?.text = "${(p * 100f / 1000f).roundToInt()}%"
                },
                onCommit = {
                    DevConsoleManager.log(LogLevel.INFO, TAG, "Floater Bass Boost -> ${getBassBoostStrength(ctx)}")
                },
            ))
        }
        tvBassBoostVal = rootView.findViewById(R.id.tv_floater_bass_boost_val)

        seekEqBass = rootView.findViewById<SeekBar>(R.id.seek_floater_eq_bass)?.apply {
            max = 100
            setOnSeekBarChangeListener(createSimpleSeekBarListener(
                onChanged = { p ->
                    setEqBass(ctx, p.toFloat())
                    SongPlayer.refreshAudioEffects(ctx)
                    tvEqBassVal?.text = String.format(Locale.US, "%+.1f dB", ((p - 50f) * 12f / 50f))
                },
            ))
        }
        tvEqBassVal = rootView.findViewById(R.id.tv_floater_eq_bass_val)

        seekEqVocal = rootView.findViewById<SeekBar>(R.id.seek_floater_eq_vocal)?.apply {
            max = 100
            setOnSeekBarChangeListener(createSimpleSeekBarListener(
                onChanged = { p ->
                    setEqVocal(ctx, p.toFloat())
                    SongPlayer.refreshAudioEffects(ctx)
                    tvEqVocalVal?.text = String.format(Locale.US, "%+.1f dB", ((p - 50f) * 12f / 50f))
                },
            ))
        }
        tvEqVocalVal = rootView.findViewById(R.id.tv_floater_eq_vocal_val)

        seekEqTreble = rootView.findViewById<SeekBar>(R.id.seek_floater_eq_treble)?.apply {
            max = 100
            setOnSeekBarChangeListener(createSimpleSeekBarListener(
                onChanged = { p ->
                    setEqTreble(ctx, p.toFloat())
                    SongPlayer.refreshAudioEffects(ctx)
                    tvEqTrebleVal?.text = String.format(Locale.US, "%+.1f dB", ((p - 50f) * 12f / 50f))
                },
            ))
        }
        tvEqTrebleVal = rootView.findViewById(R.id.tv_floater_eq_treble_val)

        seekEqPreamp = rootView.findViewById<SeekBar>(R.id.seek_floater_eq_preamp)?.apply {
            max = 180
            setOnSeekBarChangeListener(createSimpleSeekBarListener(
                onChanged = { p ->
                    val db = -12.0f + (p * 18.0f / 180.0f)
                    setEqPreampDb(ctx, db)
                    SongPlayer.refreshAudioEffects(ctx)
                    tvEqPreampVal?.text = String.format(Locale.US, "%+.1f dB", db)
                },
            ))
        }
        tvEqPreampVal = rootView.findViewById(R.id.tv_floater_eq_preamp_val)

        toggleNormalizer = rootView.findViewById<TextView>(R.id.toggle_floater_normalizer)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                val next = !isAudioNormalizerEnabled(ctx)
                setAudioNormalizerEnabled(ctx, next)
                SongPlayer.refreshAudioEffects(ctx)
                syncAudioFxUiFromPreferences()
                performHaptic(view, isAccent = next)
            }
        }
        toggleLimiter = rootView.findViewById<TextView>(R.id.toggle_floater_limiter)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                val next = !isLimiterEnabled(ctx)
                setLimiterEnabled(ctx, next)
                SongPlayer.refreshAudioEffects(ctx)
                syncAudioFxUiFromPreferences()
                performHaptic(view, isAccent = next)
            }
        }
        btnEqReset = rootView.findViewById<TextView>(R.id.btn_floater_eq_reset)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                performHaptic(view, isAccent = true)
                EqPresetCatalog.resetFlat(ctx)
                SongPlayer.refreshAudioEffects(ctx)
                buildEqPresetChips(ctx)
                syncAudioFxUiFromPreferences()
                DevConsoleManager.log(LogLevel.INFO, TAG, "Floater reset EQ curve to Flat")
            }
        }

        // 3D Controls
        toggle3dView = rootView.findViewById<TextView>(R.id.toggle_floater_3d)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                val next = !isEqSpatialEnabled(ctx)
                setEqSpatialEnabled(ctx, next)
                SongPlayer.refreshAudioEffects(ctx)
                syncAudioFxUiFromPreferences()
                performHaptic(view, isAccent = next)
                DevConsoleManager.log(LogLevel.INFO, TAG, "Floater 3D Spatial ${if (next) "ENABLED" else "DISABLED"}")
            }
        }
        tvRouteInfo = rootView.findViewById(R.id.tv_floater_route_info)

        seekSpatialStrength = rootView.findViewById<SeekBar>(R.id.seek_floater_spatial_strength)?.apply {
            max = 1000
            setOnSeekBarChangeListener(createSimpleSeekBarListener(
                onChanged = { p ->
                    setSpatialStrength(ctx, p)
                    SongPlayer.refreshAudioEffects(ctx)
                    tvSpatialStrengthVal?.text = "${(p * 100f / 1000f).roundToInt()}%"
                },
                onCommit = {
                    DevConsoleManager.log(LogLevel.INFO, TAG, "Floater 3D Depth -> ${getSpatialStrength(ctx)}")
                },
            ))
        }
        tvSpatialStrengthVal = rootView.findViewById(R.id.tv_floater_spatial_strength_val)

        seekRoomDelay = rootView.findViewById<SeekBar>(R.id.seek_floater_room_delay)?.apply {
            max = 26
            setOnSeekBarChangeListener(createSimpleSeekBarListener(
                onChanged = { p ->
                    val ms = 2f + p.toFloat()
                    setSpatialRoomDelayMs(ctx, ms)
                    SongPlayer.refreshAudioEffects(ctx)
                    tvRoomDelayVal?.text = "${ms.roundToInt()} ms"
                },
            ))
        }
        tvRoomDelayVal = rootView.findViewById(R.id.tv_floater_room_delay_val)

        seekElevation = rootView.findViewById<SeekBar>(R.id.seek_floater_elevation)?.apply {
            max = 100
            setOnSeekBarChangeListener(createSimpleSeekBarListener(
                onChanged = { p ->
                    val depth = p.toFloat() / 100f
                    setSpatialElevationDepth(ctx, depth)
                    SongPlayer.refreshAudioEffects(ctx)
                    tvElevationVal?.text = "$p%"
                },
            ))
        }
        tvElevationVal = rootView.findViewById(R.id.tv_floater_elevation_val)

        row8dSpeed = rootView.findViewById(R.id.row_floater_8d_speed)
        seek8dSpeed = rootView.findViewById<SeekBar>(R.id.seek_floater_8d_speed)?.apply {
            max = 140
            setOnSeekBarChangeListener(createSimpleSeekBarListener(
                onChanged = { p ->
                    val hz = 0.10f + (p.toFloat() / 100f)
                    setSpatialOrbitSpeed(ctx, hz)
                    SongPlayer.refreshAudioEffects(ctx)
                    tv8dSpeedVal?.text = String.format(Locale.US, "%.2fHz", hz)
                },
            ))
        }
        tv8dSpeedVal = rootView.findViewById(R.id.tv_floater_8d_speed_val)

        toggle8d = rootView.findViewById<TextView>(R.id.toggle_floater_8d)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                val next = !isSpatial8dOrbitEnabled(ctx)
                setSpatial8dOrbitEnabled(ctx, next)
                SongPlayer.refreshAudioEffects(ctx)
                syncAudioFxUiFromPreferences()
                performHaptic(view, isAccent = next)
                DevConsoleManager.log(LogLevel.INFO, TAG, "Floater 8D Orbit ${if (next) "ON" else "OFF"}")
            }
        }
        toggleCrossfeed = rootView.findViewById<TextView>(R.id.toggle_floater_crossfeed)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                val next = !isHeadphoneCrossfeedEnabled(ctx)
                setHeadphoneCrossfeedEnabled(ctx, next)
                SongPlayer.refreshAudioEffects(ctx)
                syncAudioFxUiFromPreferences()
                performHaptic(view, isAccent = next)
            }
        }
        toggleCorrGuard = rootView.findViewById<TextView>(R.id.toggle_floater_corr_guard)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                val next = !isSpatialCorrelationGuardEnabled(ctx)
                setSpatialCorrelationGuardEnabled(ctx, next)
                SongPlayer.refreshAudioEffects(ctx)
                syncAudioFxUiFromPreferences()
                performHaptic(view, isAccent = next)
            }
        }
    }

    private fun createSimpleSeekBarListener(
        onChanged: (Int) -> Unit,
        onCommit: (() -> Unit)? = null,
    ): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    resetAutoDismissTimer()
                    onChanged(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                cancelAutoDismissTimer()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                performHaptic(seekBar, isAccent = false)
                onCommit?.invoke()
                resetAutoDismissTimer()
            }
        }
    }

    private fun syncAudioFxUiFromPreferences() {
        val ctx = this
        val eqActive = isEqEnabled(ctx)
        toggleEqView?.apply {
            text = if (eqActive) "EQ ON" else "EQ OFF"
            setBackgroundResource(if (eqActive) R.drawable.bg_mini_chip_active else R.drawable.bg_mini_chip_inactive)
            setTextColor(if (eqActive) Color.parseColor("#FF0B0E11") else Color.WHITE)
        }
        seekBassBoost?.progress = getBassBoostStrength(ctx)
        tvBassBoostVal?.text = "${(getBassBoostStrength(ctx) * 100f / 1000f).roundToInt()}%"

        val bassVal = getEqBass(ctx)
        seekEqBass?.progress = bassVal.roundToInt()
        tvEqBassVal?.text = String.format(Locale.US, "%+.1f dB", ((bassVal - 50f) * 12f / 50f))

        val vocalVal = getEqVocal(ctx)
        seekEqVocal?.progress = vocalVal.roundToInt()
        tvEqVocalVal?.text = String.format(Locale.US, "%+.1f dB", ((vocalVal - 50f) * 12f / 50f))

        val trebleVal = getEqTreble(ctx)
        seekEqTreble?.progress = trebleVal.roundToInt()
        tvEqTrebleVal?.text = String.format(Locale.US, "%+.1f dB", ((trebleVal - 50f) * 12f / 50f))

        val preampDb = getEqPreampDb(ctx)
        seekEqPreamp?.progress = ((preampDb + 12f) * 180f / 18f).roundToInt().coerceIn(0, 180)
        tvEqPreampVal?.text = String.format(Locale.US, "%+.1f dB", preampDb)

        val normActive = isAudioNormalizerEnabled(ctx)
        toggleNormalizer?.setBackgroundResource(if (normActive) R.drawable.bg_mini_chip_active else R.drawable.bg_mini_chip_inactive)
        toggleNormalizer?.setTextColor(if (normActive) Color.parseColor("#FF0B0E11") else Color.WHITE)

        val limitActive = isLimiterEnabled(ctx)
        toggleLimiter?.setBackgroundResource(if (limitActive) R.drawable.bg_mini_chip_active else R.drawable.bg_mini_chip_inactive)
        toggleLimiter?.setTextColor(if (limitActive) Color.parseColor("#FF0B0E11") else Color.WHITE)

        val d3Active = isEqSpatialEnabled(ctx)
        toggle3dView?.apply {
            text = if (d3Active) "3D ON" else "3D OFF"
            setBackgroundResource(if (d3Active) R.drawable.bg_mini_chip_active else R.drawable.bg_mini_chip_inactive)
            setTextColor(if (d3Active) Color.parseColor("#FF0B0E11") else Color.WHITE)
        }
        val routeInfo = AudioEffectController.routeState.value
        tvRouteInfo?.text = "Route: ${routeInfo.route.label} (${routeInfo.deviceName})"

        val spStrength = getSpatialStrength(ctx)
        seekSpatialStrength?.progress = spStrength
        tvSpatialStrengthVal?.text = "${(spStrength * 100f / 1000f).roundToInt()}%"

        val delayMs = getSpatialRoomDelayMs(ctx)
        seekRoomDelay?.progress = (delayMs - 2f).roundToInt().coerceIn(0, 26)
        tvRoomDelayVal?.text = "${delayMs.roundToInt()} ms"

        val elev = getSpatialElevationDepth(ctx)
        seekElevation?.progress = (elev * 100f).roundToInt().coerceIn(0, 100)
        tvElevationVal?.text = "${(elev * 100f).roundToInt()}%"

        val orbitEnabled = isSpatial8dOrbitEnabled(ctx)
        row8dSpeed?.visibility = if (orbitEnabled) View.VISIBLE else View.GONE
        toggle8d?.setBackgroundResource(if (orbitEnabled) R.drawable.bg_mini_chip_active else R.drawable.bg_mini_chip_inactive)
        toggle8d?.setTextColor(if (orbitEnabled) Color.parseColor("#FF0B0E11") else Color.WHITE)
        val speedHz = getSpatialOrbitSpeed(ctx)
        seek8dSpeed?.progress = ((speedHz - 0.10f) * 100f).roundToInt().coerceIn(0, 140)
        tv8dSpeedVal?.text = String.format(Locale.US, "%.2fHz", speedHz)

        val crossfeedActive = isHeadphoneCrossfeedEnabled(ctx)
        toggleCrossfeed?.setBackgroundResource(if (crossfeedActive) R.drawable.bg_mini_chip_active else R.drawable.bg_mini_chip_inactive)
        toggleCrossfeed?.setTextColor(if (crossfeedActive) Color.parseColor("#FF0B0E11") else Color.WHITE)

        val corrActive = isSpatialCorrelationGuardEnabled(ctx)
        toggleCorrGuard?.setBackgroundResource(if (corrActive) R.drawable.bg_mini_chip_active else R.drawable.bg_mini_chip_inactive)
        toggleCorrGuard?.setTextColor(if (corrActive) Color.parseColor("#FF0B0E11") else Color.WHITE)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createDragTouchListener(
        params: WindowManager.LayoutParams,
        targetView: View,
        touchSlop: Int,
    ): View.OnTouchListener {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var dragging = false

        return View.OnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_OUTSIDE -> {
                    hideOverlay()
                    true
                }
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragging = false
                    resetAutoDismissTimer()
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                        cancelAutoDismissTimer()
                        performHaptic(v, isAccent = false)
                    }
                    if (dragging) {
                        val rawX = initialX + dx.roundToInt()
                        val rawY = initialY + dy.roundToInt()
                        val visHeight = currentMeasuredHeight(targetView, params)
                        val (clampedX, clampedY) = clampCoordinatesToDisplay(rawX, rawY, params.width, visHeight)
                        params.x = clampedX
                        params.y = clampedY

                        runCatching {
                            if (targetView.isAttachedToWindow) {
                                windowManager?.updateViewLayout(targetView, params)
                            }
                        }
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val wasDragging = dragging
                    dragging = false
                    if (wasDragging) {
                        saveDragCoordinates(resources.configuration.orientation, params.x, params.y, currentMeasuredHeight(targetView, params), params.width)
                        performHaptic(v, isAccent = false)
                    }
                    resetAutoDismissTimer()
                    wasDragging
                }
                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createResizeTouchListener(
        params: WindowManager.LayoutParams,
        targetView: View,
        context: Context,
    ): View.OnTouchListener {
        var initialWidth = 0
        var initialHeight = 0
        var initialX = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var resizing = false
        var isBottomLeft = false
        var lastHapticStepW = 0
        var lastHapticStepH = 0

        return View.OnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialWidth = if (params.width > 0) params.width else targetView.width.coerceAtLeast(dpToPx(296))
                    initialHeight = currentMeasuredHeight(targetView, params)
                    initialX = params.x
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isBottomLeft = v.id == R.id.resize_corner_bl
                    resizing = true
                    lastHapticStepW = initialWidth
                    lastHapticStepH = initialHeight
                    cancelAutoDismissTimer()
                    performHaptic(v, isAccent = false)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (resizing) {
                        val dx = (event.rawX - initialTouchX).roundToInt()
                        val dy = (event.rawY - initialTouchY).roundToInt()
                        val metrics = resources.displayMetrics
                        val maxW = (metrics.widthPixels - dpToPx(12)).coerceAtMost(dpToPx(600))
                        val minW = dpToPx(292)
                        val maxH = (metrics.heightPixels - dpToPx(32)).coerceAtLeast(dpToPx(280))
                        val minExpandedH = dpToPx(280)

                        if (!isDrawerExpanded && dy > dpToPx(36)) {
                            isDrawerExpanded = true
                            studioDrawerLayout?.visibility = View.VISIBLE
                            updateFxToggleButtonTint()
                            performHaptic(v, isAccent = true)
                        }

                        if (isBottomLeft) {
                            val rightEdge = initialX + initialWidth
                            val maxWForEdge = (rightEdge - dpToPx(4)).coerceIn(minW, maxW)
                            val newW = (initialWidth - dx).coerceIn(minW, maxWForEdge)
                            params.width = newW
                            params.x = (rightEdge - newW).coerceAtLeast(dpToPx(4))
                        } else {
                            val maxWForX = (metrics.widthPixels - params.x - dpToPx(4)).coerceIn(minW, maxW)
                            params.width = (initialWidth + dx).coerceIn(minW, maxWForX)
                        }

                        if (isDrawerExpanded) {
                            val maxHForY = (metrics.heightPixels - params.y - dpToPx(8)).coerceIn(minExpandedH, maxH)
                            params.height = (initialHeight + dy).coerceIn(minExpandedH, maxHForY)
                        } else {
                            params.height = WindowManager.LayoutParams.WRAP_CONTENT
                        }

                        val curH = currentMeasuredHeight(targetView, params)
                        if (abs(params.width - lastHapticStepW) >= dpToPx(24) ||
                            (isDrawerExpanded && abs(curH - lastHapticStepH) >= dpToPx(24))
                        ) {
                            lastHapticStepW = params.width
                            lastHapticStepH = curH
                            performHaptic(v, isAccent = false)
                        }

                        updateGeometryBadge(params.width, params.height, targetView)
                        runCatching {
                            if (targetView.isAttachedToWindow) {
                                windowManager?.updateViewLayout(targetView, params)
                            }
                        }
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (resizing) {
                        resizing = false
                        val finalH = currentMeasuredHeight(targetView, params)
                        saveGeometry(resources.configuration.orientation, params.width, finalH)
                        saveDragCoordinates(resources.configuration.orientation, params.x, params.y, finalH, params.width)
                        performHaptic(v, isAccent = true)
                    }
                    resetAutoDismissTimer()
                    true
                }
                else -> false
            }
        }
    }

    private fun currentMeasuredHeight(targetView: View, params: WindowManager.LayoutParams): Int =
        when {
            params.height > 0 -> params.height
            targetView.height > 0 -> targetView.height
            else -> dpToPx(if (isDrawerExpanded) 440 else 236)
        }

    private fun updateGeometryBadge(widthPx: Int, heightPx: Int, rootView: View? = overlayView) {
        val badge = statusBadgeTextView ?: return
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val wDp = (widthPx / density).roundToInt()
        val resolvedH = when {
            heightPx > 0 -> heightPx
            (rootView?.height ?: 0) > 0 -> rootView!!.height
            else -> dpToPx(236)
        }
        val hDp = (resolvedH / density).roundToInt()
        badge.text = "BOOSTER · ${wDp}×${hDp}"
    }

    private fun openMainAppAndDismiss() {
        runCatching {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP,
                )
            }
            startActivity(intent)
        }.onFailure { err ->
            DevConsoleManager.log(LogLevel.WARNING, TAG, "Failed opening SpotUI from Floater: ${err.message}", err)
        }
        hideOverlay()
    }

    private fun performHaptic(anchorView: View?, isAccent: Boolean) {
        if (!isFloaterHapticsEnabled(this)) return
        val strength = getFloaterHapticStrength(this)
        val durationMs = if (isAccent) strength.accentDurationMs else strength.clickDurationMs
        val amplitude = strength.amplitude

        anchorView?.let { v ->
            runCatching {
                val flag = if (isAccent) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        HapticFeedbackConstants.CONFIRM
                    } else {
                        HapticFeedbackConstants.LONG_PRESS
                    }
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

    private fun hideOverlay() {
        cancelAutoDismissTimer()
        overlayScope?.cancel()
        overlayScope = null

        val view = overlayView
        overlayView = null
        overlayParams = null
        statusBadgeTextView = null
        coverDiscImageView = null
        titleTextView = null
        artistTextView = null
        sourceBadgeTextView = null
        compactProgressBar = null
        likeButton = null
        openAppButton = null
        fxToggleButton = null
        closeButton = null
        shuffleButton = null
        prevButton = null
        playPauseButton = null
        nextButton = null
        repeatButton = null
        volumeMuteButton = null
        volumeSeekBar = null
        volumePercentTextView = null
        volumeStepDownButton = null
        volumeStepUpButton = null
        isUserSeekingVolume = false
        isUserSeekingTrack = false
        studioDrawerLayout = null
        sidebarScrollView = null
        btnSidebarCompactToggle = null
        tvSectionMedia = null
        tvSectionSound = null
        tvSectionTools = null
        navTabPlayer = null
        navTabQueue = null
        navTabEq = null
        navTab3d = null
        navTabTools = null
        ivNavPlayer = null
        ivNavQueue = null
        ivNavEq = null
        ivNav3d = null
        ivNavTools = null
        tvNavPlayer = null
        tvNavQueue = null
        tvNavEq = null
        tvNav3d = null
        tvNavTools = null
        panelPlayerLayout = null
        panelQueueLayout = null
        panelEqLayout = null
        panel3dLayout = null
        panelToolsLayout = null
        tvPlayerTabTitle = null
        tvPlayerTabArtist = null
        tvPlayerTabSource = null
        seekPlayerTrack = null
        tvPlayerPosTime = null
        tvPlayerDurTime = null
        btnPlayerRew10 = null
        btnPlayerFwd10 = null
        chipPlayerShuffle = null
        chipPlayerRepeat = null
        tvPlayerRouteChip = null
        tvQueueCount = null
        btnQueueShuffleUpcoming = null
        btnQueueDedupe = null
        containerQueueItems = null
        lastQueueRenderHash = 0
        eqPresetsContainer = null
        spatialProfilesContainer = null
        toggleEqView = null
        seekBassBoost = null
        tvBassBoostVal = null
        seekEqBass = null
        tvEqBassVal = null
        seekEqVocal = null
        tvEqVocalVal = null
        seekEqTreble = null
        tvEqTrebleVal = null
        seekEqPreamp = null
        tvEqPreampVal = null
        toggleNormalizer = null
        toggleLimiter = null
        btnEqReset = null
        toggle3dView = null
        tvRouteInfo = null
        seekSpatialStrength = null
        tvSpatialStrengthVal = null
        seekRoomDelay = null
        tvRoomDelayVal = null
        seekElevation = null
        tvElevationVal = null
        row8dSpeed = null
        seek8dSpeed = null
        tv8dSpeedVal = null
        toggle8d = null
        toggleCrossfeed = null
        toggleCorrGuard = null
        seekToolsSpeed = null
        tvToolsSpeedVal = null
        seekToolsPitch = null
        tvToolsPitchVal = null
        btnToolsSpeedReset = null
        toggleToolsSkipSilence = null
        tvToolsSleepStatus = null
        btnSleepOff = null
        btnSleep10m = null
        btnSleep15m = null
        btnSleep30m = null
        btnSleep60m = null
        tvToolsAbStatus = null
        btnAbSetA = null
        btnAbSetB = null
        btnAbClear = null
        btnToolsAutohide = null
        btnToolsHaptics = null

        if (view != null) {
            runCatching {
                if (view.isAttachedToWindow) {
                    windowManager?.removeViewImmediate(view)
                }
            }.onFailure { err ->
                DevConsoleManager.log(
                    LogLevel.WARNING,
                    TAG,
                    "Error detaching Booster Floater cleanly: ${err.message}",
                    err,
                )
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
        val estHeight = if (params.height > 0) params.height else dpToPx(236)
        val defaultX = ((metrics.widthPixels - params.width) / 2).coerceAtLeast(dpToPx(8))
        val defaultY = if (isLandscape) {
            dpToPx(20)
        } else {
            (metrics.heightPixels - estHeight - dpToPx(88)).coerceAtLeast(dpToPx(20))
        }

        val hasSaved = prefs.getBoolean("${prefix}_saved", false)
        val targetX = if (hasSaved) prefs.getInt("${prefix}_x", defaultX) else defaultX
        val targetY = if (hasSaved) prefs.getInt("${prefix}_y", defaultY) else defaultY

        val (clampedX, clampedY) = clampCoordinatesToDisplay(targetX, targetY, params.width, estHeight)
        params.x = clampedX
        params.y = clampedY
    }

    private fun saveDragCoordinates(orientation: Int, x: Int, y: Int, estHeight: Int, widthPx: Int) {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val prefix = if (isLandscape) KEY_LANDSCAPE_PREFIX else KEY_PORTRAIT_PREFIX
        val (clampedX, clampedY) = clampCoordinatesToDisplay(x, y, widthPx, estHeight)
        prefs.edit()
            .putBoolean("${prefix}_saved", true)
            .putInt("${prefix}_x", clampedX)
            .putInt("${prefix}_y", clampedY)
            .apply()
    }

    private fun saveGeometry(orientation: Int, widthPx: Int, heightPx: Int) {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val prefix = if (isLandscape) KEY_LANDSCAPE_PREFIX else KEY_PORTRAIT_PREFIX
        val keyW = if (isDrawerExpanded) "${prefix}_w_exp" else "${prefix}_w_comp"
        val editor = prefs.edit().putInt(keyW, widthPx)
        if (isDrawerExpanded && heightPx > 0) {
            editor.putInt("${prefix}_h_exp", heightPx)
        }
        editor.apply()
    }

    private fun getSavedWidthPx(orientation: Int, expanded: Boolean): Int {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val prefix = if (isLandscape) KEY_LANDSCAPE_PREFIX else KEY_PORTRAIT_PREFIX
        val metrics = resources.displayMetrics
        val maxW = (metrics.widthPixels - dpToPx(12)).coerceAtMost(dpToPx(600))
        val minW = dpToPx(292)
        val defW = dpToPx(if (isLandscape) 396 else 356).coerceIn(minW, maxW)
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
        val defH = dpToPx(if (isLandscape) 310 else 470).coerceIn(minH, maxH)
        return prefs.getInt("${prefix}_h_exp", defH).coerceIn(minH, maxH)
    }

    private fun clampCoordinatesToDisplay(
        x: Int,
        y: Int,
        widthPx: Int,
        heightPx: Int,
    ): Pair<Int, Int> {
        val metrics = resources.displayMetrics
        val pad = dpToPx(4)
        val maxX = (metrics.widthPixels - widthPx - pad).coerceAtLeast(pad)
        val maxY = (metrics.heightPixels - heightPx - dpToPx(8)).coerceAtLeast(dpToPx(8))
        val clampedX = x.coerceIn(pad, maxX)
        val clampedY = y.coerceIn(dpToPx(8), maxY)
        return clampedX to clampedY
    }

    private fun ensureMediaControllerConnected() {
        val existing = mediaController
        if (existing != null && existing.isConnected) {
            syncPlaybackSnapshotToUi()
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
                val controller = runCatching { future.get() }.getOrNull()
                if (controller == null) {
                    controllerFuture = null
                    return@addListener
                }
                mediaController?.removeListener(playerListener)
                mediaController = controller
                controller.addListener(playerListener)
                syncPlaybackSnapshotToUi()
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun releaseMediaController() {
        mediaController?.removeListener(playerListener)
        mediaController = null
        val future = controllerFuture
        controllerFuture = null
        if (future != null) {
            runCatching { MediaController.releaseFuture(future) }
        }
    }

    private fun formatDurationMs(ms: Long): String {
        val totalSec = (ms.coerceAtLeast(0L) / 1000L)
        val min = totalSec / 60L
        val sec = totalSec % 60L
        return String.format(Locale.US, "%d:%02d", min, sec)
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val TAG = "MediaAccessibility"
        private const val KEY_PORTRAIT_PREFIX = "portrait"
        private const val KEY_LANDSCAPE_PREFIX = "landscape"
        private const val KEY_ACTIVE_TAB = "active_tab"
        private const val KEY_SIDEBAR_COMPACT = "sidebar_compact"
    }
}
