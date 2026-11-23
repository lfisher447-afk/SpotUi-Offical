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
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageButton
import android.widget.LinearLayout
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
import com.music.spotui.data.preferences.setHeadphoneCrossfeedEnabled
import com.music.spotui.data.preferences.setLimiterEnabled
import com.music.spotui.data.preferences.setSpatial8dOrbitEnabled
import com.music.spotui.data.preferences.setSpatialCorrelationGuardEnabled
import com.music.spotui.data.preferences.setSpatialElevationDepth
import com.music.spotui.data.preferences.setSpatialOrbitSpeed
import com.music.spotui.data.preferences.setSpatialProfile
import com.music.spotui.data.preferences.setSpatialRoomDelayMs
import com.music.spotui.data.preferences.setSpatialStrength
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.notification.PlaybackService
import com.music.spotui.util.DevConsoleManager
import com.music.spotui.util.DevConsoleManager.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Samsung One UI 9.0 / Android 17 Accessibility Button Floating Studio Console.
 *
 * Uses [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY] with
 * `canRetrieveWindowContent="false"` and `isAccessibilityTool="true"` so Spotui never
 * requires `SYSTEM_ALERT_WINDOW` and displays safely over full-screen landscape games
 * without disrupting gameplay.
 *
 * Architecture & capabilities:
 * - **In-App Volume Control**: Adjusts [SongPlayer] internal gain (0–100%) without mutating
 *   Android OS volume, with mute/unmute toggle, `-5%` / `+5%` step pills, and live SeekBar.
 * - **Interactive EQ & 3D Studio Drawer**: Full 7-band EQ presets ([EqPresetCatalog]), Bass Boost,
 *   Bass/Vocal/Treble/Preamp sliders, Normalizer, Soft Limiter, and 3D Spatial Audio (profiles,
 *   depth, room delay, elevation, 8D orbit + speed, HRTF crossfeed, phase guard).
 * - **Top-Start Coordinate & Corner-Drag Resizing**: Bottom-left & bottom-right resize grips
 *   update width/height cleanly with anchored opposite corners, live `"W × H dp"` geometry badge,
 *   and per-orientation persisted placement/sizes.
 * - **Dedicated Haptic Engine**: Uses [VibratorManager]/[Vibrator] + [VibrationEffect] scaled by
 *   user-configured [FloaterHapticStrength].
 */
class MediaAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlayScope: CoroutineScope? = null

    private var windowManager: WindowManager? = null
    private val prefs: SharedPreferences by lazy(LazyThreadSafetyMode.NONE) {
        applicationContext.getSharedPreferences(FLOATER_OVERLAY_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var themedContextRef: Context? = null
    private var currentOrientation: Int = Configuration.ORIENTATION_UNDEFINED
    private var isDrawerExpanded: Boolean = false
    private var isEqTabActive: Boolean = true

    private var statusBadgeTextView: TextView? = null
    private var titleTextView: TextView? = null
    private var artistTextView: TextView? = null
    private var prevButton: ImageButton? = null
    private var playPauseButton: ImageButton? = null
    private var nextButton: ImageButton? = null
    private var closeButton: ImageButton? = null
    private var openAppButton: ImageButton? = null
    private var fxToggleButton: ImageButton? = null

    // In-App Volume
    private var volumeMuteButton: ImageButton? = null
    private var volumeSeekBar: SeekBar? = null
    private var volumePercentTextView: TextView? = null
    private var volumeStepDownButton: TextView? = null
    private var volumeStepUpButton: TextView? = null
    private var isUserSeekingVolume = false
    private var lastUnmutedVolumePct = 100

    // Studio FX Drawer & Tabs
    private var studioDrawerLayout: LinearLayout? = null
    private var tabEqTextView: TextView? = null
    private var tab3dTextView: TextView? = null
    private var panelEqLayout: LinearLayout? = null
    private var panel3dLayout: LinearLayout? = null
    private var eqPresetsContainer: LinearLayout? = null
    private var spatialProfilesContainer: LinearLayout? = null

    // EQ Controls
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

    // 3D Controls
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

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var accessibilityButtonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoDismissRunnable = Runnable {
        DevConsoleManager.log(LogLevel.INFO, TAG, "Studio Floater auto-dismissed after idle timeout")
        hideOverlay()
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseIcon(isPlaying)
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            updateTrackMetadataUi(mediaMetadata)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            mediaController?.let { syncUiFromController(it) }
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
            "MediaAccessibilityService connected (One UI Studio Floater ready)",
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
                "Failed updating Studio Floater layout on orientation change: ${err.message}",
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
            themedContextRef = themedContext
            val rootView = LayoutInflater.from(themedContext).inflate(R.layout.layout_mini_player, null)

            val fxAllowed = isFloaterEqAnd3dEnabled(this)
            isDrawerExpanded = fxAllowed && isFloaterStartExpanded(this)

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
            mediaController?.let { syncUiFromController(it) }
            syncVolumeFlowAndUi()
            syncAudioFxUiFromPreferences()
            startReactiveCollectors(themedContext, rootView, params)
            resetAutoDismissTimer()

            DevConsoleManager.log(
                LogLevel.INFO,
                TAG,
                "Studio Floater opened (expanded=$isDrawerExpanded, fxHook=$fxAllowed, inAppVol=${SongPlayer.getAppVolumePercent(this)}%)",
            )
        } catch (t: Throwable) {
            DevConsoleManager.log(
                LogLevel.ERROR,
                TAG,
                "Failed to inflate/attach Studio Floater overlay: ${t.message}",
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
                tvRouteInfo?.text = "Route: ${routeInfo.route.label} (${routeInfo.deviceName})"
            }
        }
        scope.launch {
            floaterSettingsUpdates.collectLatest {
                val fxAllowed = isFloaterEqAnd3dEnabled(themedContext)
                fxToggleButton?.visibility = if (fxAllowed) View.VISIBLE else View.GONE
                if (!fxAllowed && isDrawerExpanded) {
                    setDrawerExpanded(expanded = false, rootView = rootView, params = params)
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
        titleTextView = rootView.findViewById<TextView>(R.id.tv_mini_title)?.apply {
            isSelected = true
        }
        artistTextView = rootView.findViewById(R.id.tv_mini_artist)

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
        closeButton = rootView.findViewById<ImageButton>(R.id.btn_mini_close)?.apply {
            setOnClickListener { view ->
                performHaptic(view, isAccent = false)
                hideOverlay()
            }
        }
        openAppButton = rootView.findViewById<ImageButton>(R.id.btn_mini_open_app)?.apply {
            setOnClickListener { view ->
                performHaptic(view, isAccent = true)
                openMainAppAndDismiss()
            }
        }
        fxToggleButton = rootView.findViewById<ImageButton>(R.id.btn_mini_fx_toggle)?.apply {
            visibility = if (fxAllowed) View.VISIBLE else View.GONE
            setOnClickListener { view ->
                if (!isFloaterEqAnd3dEnabled(themedContext)) return@setOnClickListener
                performHaptic(view, isAccent = true)
                setDrawerExpanded(!isDrawerExpanded, rootView, params)
                resetAutoDismissTimer()
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
                        "Floater adjusted In-App Volume -> ${seekBar?.progress ?: 0}% (OS untouched)",
                    )
                    resetAutoDismissTimer()
                }
            })
        }

        // Studio Drawer & Tabs
        studioDrawerLayout = rootView.findViewById(R.id.layout_studio_drawer)
        tabEqTextView = rootView.findViewById<TextView>(R.id.tab_floater_eq)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                performHaptic(view, isAccent = false)
                switchStudioTab(isEqTab = true)
            }
        }
        tab3dTextView = rootView.findViewById<TextView>(R.id.tab_floater_3d)?.apply {
            setOnClickListener { view ->
                resetAutoDismissTimer()
                performHaptic(view, isAccent = false)
                switchStudioTab(isEqTab = false)
            }
        }
        panelEqLayout = rootView.findViewById(R.id.panel_floater_eq)
        panel3dLayout = rootView.findViewById(R.id.panel_floater_3d)
        eqPresetsContainer = rootView.findViewById(R.id.container_eq_presets)
        spatialProfilesContainer = rootView.findViewById(R.id.container_spatial_profiles)

        buildEqPresetChips(themedContext)
        buildSpatialProfileChips(themedContext)
        bindEqAnd3dControls(rootView, themedContext)
        switchStudioTab(isEqTabActive)

        // Dismiss on outside touch
        rootView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                hideOverlay()
                true
            } else {
                false
            }
        }

        // Panning gesture on top header pill bar
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

        // Apply initial expanded/collapsed visual state cleanly
        studioDrawerLayout?.visibility = if (isDrawerExpanded) View.VISIBLE else View.GONE
        updateFxToggleButtonTint()
        updateGeometryBadge(params.width, params.height, rootView)
    }

    private fun setDrawerExpanded(
        expanded: Boolean,
        rootView: View,
        params: WindowManager.LayoutParams,
    ) {
        if (isDrawerExpanded == expanded) return
        if (expanded && !isFloaterEqAnd3dEnabled(this)) return

        isDrawerExpanded = expanded
        studioDrawerLayout?.visibility = if (expanded) View.VISIBLE else View.GONE
        updateFxToggleButtonTint()

        params.width = getSavedWidthPx(currentOrientation, expanded)
        params.height = if (expanded) {
            getSavedExpandedHeightPx(currentOrientation)
        } else {
            WindowManager.LayoutParams.WRAP_CONTENT
        }

        val estHeight = if (params.height > 0) params.height else dpToPx(196)
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
        val tintColor = if (isDrawerExpanded) Color.parseColor("#FF1DB954") else Color.WHITE
        btn.imageTintList = ColorStateList.valueOf(tintColor)
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
            if (muted) Color.parseColor("#FFFF6B6B") else Color.parseColor("#FF1DB954"),
        )
    }

    private fun switchStudioTab(isEqTab: Boolean) {
        isEqTabActive = isEqTab
        val eqTab = tabEqTextView ?: return
        val d3Tab = tab3dTextView ?: return
        val eqPanel = panelEqLayout ?: return
        val d3Panel = panel3dLayout ?: return

        if (isEqTab) {
            eqTab.setBackgroundResource(R.drawable.bg_mini_chip_active)
            eqTab.setTextColor(Color.parseColor("#FF0B0E11"))
            d3Tab.setBackgroundResource(R.drawable.bg_mini_chip_inactive)
            d3Tab.setTextColor(Color.WHITE)
            eqPanel.visibility = View.VISIBLE
            d3Panel.visibility = View.GONE
        } else {
            d3Tab.setBackgroundResource(R.drawable.bg_mini_chip_active)
            d3Tab.setTextColor(Color.parseColor("#FF0B0E11"))
            eqTab.setBackgroundResource(R.drawable.bg_mini_chip_inactive)
            eqTab.setTextColor(Color.WHITE)
            d3Panel.visibility = View.VISIBLE
            eqPanel.visibility = View.GONE
        }
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
                    tvEqBassVal?.text = String.format("%+.1f dB", ((p - 50f) * 12f / 50f))
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
                    tvEqVocalVal?.text = String.format("%+.1f dB", ((p - 50f) * 12f / 50f))
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
                    tvEqTrebleVal?.text = String.format("%+.1f dB", ((p - 50f) * 12f / 50f))
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
                    tvEqPreampVal?.text = String.format("%+.1f dB", db)
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
                    tv8dSpeedVal?.text = String.format("%.2fHz", hz)
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
        tvEqBassVal?.text = String.format("%+.1f dB", ((bassVal - 50f) * 12f / 50f))

        val vocalVal = getEqVocal(ctx)
        seekEqVocal?.progress = vocalVal.roundToInt()
        tvEqVocalVal?.text = String.format("%+.1f dB", ((vocalVal - 50f) * 12f / 50f))

        val trebleVal = getEqTreble(ctx)
        seekEqTreble?.progress = trebleVal.roundToInt()
        tvEqTrebleVal?.text = String.format("%+.1f dB", ((trebleVal - 50f) * 12f / 50f))

        val preampDb = getEqPreampDb(ctx)
        seekEqPreamp?.progress = ((preampDb + 12f) * 180f / 18f).roundToInt().coerceIn(0, 180)
        tvEqPreampVal?.text = String.format("%+.1f dB", preampDb)

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
        tv8dSpeedVal?.text = String.format("%.2fHz", speedHz)

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
                    initialWidth = if (params.width > 0) params.width else targetView.width.coerceAtLeast(dpToPx(280))
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
                        val maxW = (metrics.widthPixels - dpToPx(12)).coerceAtMost(dpToPx(560))
                        val minW = dpToPx(276)
                        val maxH = (metrics.heightPixels - dpToPx(32)).coerceAtLeast(dpToPx(240))
                        val minExpandedH = dpToPx(260)

                        // Auto-expand into Studio Drawer if user drags a bottom corner downward > 36dp while collapsed
                        if (!isDrawerExpanded && dy > dpToPx(36) && isFloaterEqAnd3dEnabled(context)) {
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
                        val wDp = (params.width / resources.displayMetrics.density).roundToInt()
                        val hDp = (finalH / resources.displayMetrics.density).roundToInt()
                        DevConsoleManager.log(LogLevel.INFO, TAG, "Floater resized to ${wDp}x${hDp}dp (expanded=$isDrawerExpanded)")
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
            else -> dpToPx(if (isDrawerExpanded) 380 else 196)
        }

    private fun updateGeometryBadge(widthPx: Int, heightPx: Int, rootView: View? = overlayView) {
        val badge = statusBadgeTextView ?: return
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val wDp = (widthPx / density).roundToInt()
        val resolvedH = when {
            heightPx > 0 -> heightPx
            (rootView?.height ?: 0) > 0 -> rootView!!.height
            else -> dpToPx(196)
        }
        val hDp = (resolvedH / density).roundToInt()
        badge.text = "${wDp}×${hDp}dp"
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
        themedContextRef = null
        statusBadgeTextView = null
        titleTextView = null
        artistTextView = null
        prevButton = null
        playPauseButton = null
        nextButton = null
        closeButton = null
        openAppButton = null
        fxToggleButton = null
        volumeMuteButton = null
        volumeSeekBar = null
        volumePercentTextView = null
        volumeStepDownButton = null
        volumeStepUpButton = null
        isUserSeekingVolume = false
        studioDrawerLayout = null
        tabEqTextView = null
        tab3dTextView = null
        panelEqLayout = null
        panel3dLayout = null
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

        if (view != null) {
            runCatching {
                if (view.isAttachedToWindow) {
                    windowManager?.removeViewImmediate(view)
                }
            }.onFailure { err ->
                DevConsoleManager.log(
                    LogLevel.WARNING,
                    TAG,
                    "Error detaching Studio Floater cleanly: ${err.message}",
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
        val estHeight = if (params.height > 0) params.height else dpToPx(196)
        val defaultX = ((metrics.widthPixels - params.width) / 2).coerceAtLeast(dpToPx(8))
        val defaultY = if (isLandscape) {
            dpToPx(24)
        } else {
            (metrics.heightPixels - estHeight - dpToPx(96)).coerceAtLeast(dpToPx(24))
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
        val maxW = (metrics.widthPixels - dpToPx(12)).coerceAtMost(dpToPx(560))
        val minW = dpToPx(276)
        val defW = dpToPx(if (isLandscape) 372 else 336).coerceIn(minW, maxW)
        val keyW = if (expanded) "${prefix}_w_exp" else "${prefix}_w_comp"
        val fallback = if (expanded) prefs.getInt("${prefix}_w_comp", defW) else defW
        return prefs.getInt(keyW, fallback).coerceIn(minW, maxW)
    }

    private fun getSavedExpandedHeightPx(orientation: Int): Int {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val prefix = if (isLandscape) KEY_LANDSCAPE_PREFIX else KEY_PORTRAIT_PREFIX
        val metrics = resources.displayMetrics
        val maxH = (metrics.heightPixels - dpToPx(32)).coerceAtLeast(dpToPx(240))
        val minH = dpToPx(260)
        val defH = dpToPx(if (isLandscape) 290 else 430).coerceIn(minH, maxH)
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
            syncUiFromController(existing)
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
                syncUiFromController(controller)
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

    private fun syncUiFromController(controller: MediaController) {
        updatePlayPauseIcon(controller.isPlaying)
        val metadata = controller.mediaMetadata.takeIf { !it.title.isNullOrBlank() }
            ?: controller.currentMediaItem?.mediaMetadata
        updateTrackMetadataUi(metadata)
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        val button = playPauseButton ?: return
        button.setImageResource(
            if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )
        button.contentDescription = if (isPlaying) "Pause" else "Play"
    }

    private fun updateTrackMetadataUi(metadata: MediaMetadata?) {
        val titleView = titleTextView ?: return
        val artistView = artistTextView ?: return
        val title = metadata?.title?.toString()?.takeIf { it.isNotBlank() }
            ?: getString(R.string.mini_player_idle_title)
        val artist = metadata?.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: metadata?.albumArtist?.toString()?.takeIf { it.isNotBlank() }
            ?: getString(R.string.mini_player_idle_artist)

        if (titleView.text?.toString() != title) {
            titleView.text = title
        }
        titleView.isSelected = true
        if (artistView.text?.toString() != artist) {
            artistView.text = artist
        }
    }

    private fun handlePlayPauseClick() {
        val ctrl = mediaController
        if (ctrl != null && ctrl.isConnected) {
            if (ctrl.isPlaying) {
                ctrl.pause()
            } else {
                ctrl.play()
            }
        } else {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            ensureMediaControllerConnected()
        }
    }

    private fun handleNextClick() {
        val ctrl = mediaController
        if (ctrl != null && ctrl.isConnected) {
            ctrl.seekToNext()
        } else {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            ensureMediaControllerConnected()
        }
    }

    private fun handlePreviousClick() {
        val ctrl = mediaController
        if (ctrl != null && ctrl.isConnected) {
            ctrl.seekToPrevious()
        } else {
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            ensureMediaControllerConnected()
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val am = runCatching { getSystemService(Context.AUDIO_SERVICE) as? AudioManager }.getOrNull() ?: return
        val now = SystemClock.uptimeMillis()
        runCatching {
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val TAG = "MediaAccessibility"
        private const val KEY_PORTRAIT_PREFIX = "portrait"
        private const val KEY_LANDSCAPE_PREFIX = "landscape"
    }
}
