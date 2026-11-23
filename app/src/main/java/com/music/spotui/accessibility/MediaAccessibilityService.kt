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
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageButton
import android.widget.ImageView
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
import com.music.spotui.data.preferences.FloaterHapticStrength
import com.music.spotui.data.preferences.SpatialProfile
import com.music.spotui.data.preferences.dspSettingsUpdates
import com.music.spotui.data.preferences.floaterSettingsUpdates
import com.music.spotui.data.preferences.getAudioNormalizerGainMb
import com.music.spotui.data.preferences.getBassBoostStrength
import com.music.spotui.data.preferences.getEqBass
import com.music.spotui.data.preferences.getEqPreampDb
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
 * Upgraded features:
 * - **In-App Volume Control**: Directly controls [SongPlayer] internal audio gain (0–100%)
 *   without changing Android OS volume, with mute/unmute, -5%/+5% step buttons, and live SeekBar.
 * - **Interactive EQ & 3D Studio Drawer**: Full parametric 7-band EQ, 12 presets, bass boost,
 *   preamp, limiter, normalizer, 3D spatial profiles, room delay, elevation, 8D orbit, and crossfeed.
 * - **Corner-Drag Resizing**: Bottom-right and bottom-left corner grip handles updating
 *   [WindowManager.LayoutParams] width/height smoothly in real time with live `"W × H dp"` badge.
 * - **Tactile Vibration Engine**: Haptic feedback on transport, volume detents, preset switches,
 *   and corner resizing scaled by preference strength.
 */
class MediaAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var windowManager: WindowManager? = null
    private val prefs: SharedPreferences by lazy(LazyThreadSafetyMode.NONE) {
        applicationContext.getSharedPreferences(FLOATER_OVERLAY_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var currentOrientation: Int = Configuration.ORIENTATION_UNDEFINED

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

    // FX Drawer
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

        registerAccessibilityButton()
        ensureMediaControllerConnected()

        DevConsoleManager.log(
            LogLevel.INFO,
            TAG,
            "MediaAccessibilityService connected (One UI Studio Floater ready)",
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally unused: canRetrieveWindowContent="false" keeps accessibility secure.
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

        applyOrientationPlacement(params, newConfig.orientation)
        runCatching {
            if (view.isAttachedToWindow) {
                wm.updateViewLayout(view, params)
            }
        }.onFailure { err ->
            DevConsoleManager.log(
                LogLevel.WARNING,
                TAG,
                "Failed updating mini-player layout after orientation change: ${err.message}",
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
        }
        accessibilityButtonCallback = callback
        runCatching {
            controller.registerAccessibilityButtonCallback(callback)
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

            val widthPx = getSavedWidthPx(currentOrientation)
            val heightPx = getSavedHeightPx(currentOrientation, isFloaterStartExpanded(this))

            val params = WindowManager.LayoutParams(
                widthPx,
                heightPx,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            applyOrientationPlacement(params, currentOrientation)

            bindOverlayViews(rootView, themedContext, params)
            wm.addView(rootView, params)

            overlayView = rootView
            overlayParams = params

            performHaptic(FloaterHapticStrength.LIGHT, isAccent = false)
            ensureMediaControllerConnected()
            mediaController?.let { syncUiFromController(it) }
            syncVolumeFlowAndUi()
            syncAudioFxUiFromPreferences()
            startReactiveCollectors()
            resetAutoDismissTimer()
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

    private fun startReactiveCollectors() {
        serviceScope.launch {
            SongPlayer.appVolumePercentFlow.collectLatest { pct ->
                val sb = volumeSeekBar ?: return@collectLatest
                if (!isUserSeekingVolume) {
                    if (sb.progress != pct) sb.progress = pct
                    volumePercentTextView?.text = "$pct%"
                    if (pct > 0) lastUnmutedVolumePct = pct
                }
            }
        }
        serviceScope.launch {
            dspSettingsUpdates.collectLatest {
                syncAudioFxUiFromPreferences()
            }
        }
        serviceScope.launch {
            floaterSettingsUpdates.collectLatest {
                // Settings updated externally
            }
        }
    }

    private fun bindOverlayViews(
        rootView: View,
        themedContext: Context,
        params: WindowManager.LayoutParams,
    ) {
        val pillRoot = rootView.findViewById<View>(R.id.mini_player_root)
        statusBadgeTextView = rootView.findViewById(R.id.tv_mini_status_badge)
        titleTextView = rootView.findViewById<TextView>(R.id.tv_mini_title)?.apply {
            isSelected = true
        }
        artistTextView = rootView.findViewById(R.id.tv_mini_artist)

        prevButton = rootView.findViewById<ImageButton>(R.id.btn_mini_prev)?.apply {
            setOnClickListener {
                resetAutoDismissTimer()
                performHaptic(FloaterHapticStrength.LIGHT, isAccent = false)
                handlePreviousClick()
            }
        }
        playPauseButton = rootView.findViewById<ImageButton>(R.id.btn_mini_play_pause)?.apply {
            setOnClickListener {
                resetAutoDismissTimer()
                performHaptic(FloaterHapticStrength.MEDIUM, isAccent = true)
                handlePlayPauseClick()
            }
        }
        nextButton = rootView.findViewById<ImageButton>(R.id.btn_mini_next)?.apply {
            setOnClickListener {
                resetAutoDismissTimer()
                performHaptic(FloaterHapticStrength.LIGHT, isAccent = false)
                handleNextClick()
            }
        }
        closeButton = rootView.findViewById<ImageButton>(R.id.btn_mini_close)?.apply {
            setOnClickListener {
                performHaptic(FloaterHapticStrength.LIGHT, isAccent = false)
                hideOverlay()
            }
        }
        openAppButton = rootView.findViewById<ImageButton>(R.id.btn_mini_open_app)?.apply {
            setOnClickListener {
                performHaptic(FloaterHapticStrength.MEDIUM, isAccent = true)
                openMainAppAndDismiss()
            }
        }
        fxToggleButton = rootView.findViewById<ImageButton>(R.id.btn_mini_fx_toggle)?.apply {
            setOnClickListener {
                performHaptic(FloaterHapticStrength.MEDIUM, isAccent = true)
                toggleStudioDrawer(rootView, params)
            }
        }

        // In-App Volume Controls
        volumeMuteButton = rootView.findViewById<ImageButton>(R.id.btn_mini_vol_mute)?.apply {
            setOnClickListener {
                performHaptic(FloaterHapticStrength.LIGHT, isAccent = false)
                toggleMute()
            }
        }
        volumePercentTextView = rootView.findViewById(R.id.tv_mini_volume_pct)
        volumeStepDownButton = rootView.findViewById<TextView>(R.id.btn_mini_vol_down)?.apply {
            setOnClickListener {
                performHaptic(FloaterHapticStrength.LIGHT, isAccent = false)
                adjustVolumeStep(-5)
            }
        }
        volumeStepUpButton = rootView.findViewById<TextView>(R.id.btn_mini_vol_up)?.apply {
            setOnClickListener {
                performHaptic(FloaterHapticStrength.LIGHT, isAccent = false)
                adjustVolumeStep(5)
            }
        }
        volumeSeekBar = rootView.findViewById<SeekBar>(R.id.seek_mini_volume)?.apply {
            max = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    volumePercentTextView?.text = "$progress%"
                    if (fromUser) {
                        resetAutoDismissTimer()
                        SongPlayer.setAppVolumePercent(themedContext, progress)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isUserSeekingVolume = true
                    cancelAutoDismissTimer()
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    isUserSeekingVolume = false
                    performHaptic(FloaterHapticStrength.LIGHT, isAccent = false)
                    resetAutoDismissTimer()
                }
            })
        }

        // Studio Drawer & Tabs
        studioDrawerLayout = rootView.findViewById(R.id.layout_studio_drawer)
        tabEqTextView = rootView.findViewById<TextView>(R.id.tab_floater_eq)?.apply {
            setOnClickListener {
                performHaptic(FloaterHapticStrength.LIGHT, isAccent = false)
                switchStudioTab(isEqTab = true)
            }
        }
        tab3dTextView = rootView.findViewById<TextView>(R.id.tab_floater_3d)?.apply {
            setOnClickListener {
                performHaptic(FloaterHapticStrength.LIGHT, isAccent = false)
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

        // Dismiss on outside touch
        rootView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                hideOverlay()
                true
            } else {
                false
            }
        }

        // Drag handle / top bar panning gesture
        val touchSlop = ViewConfiguration.get(themedContext).scaledTouchSlop
        val dragHandleView = rootView.findViewById<View>(R.id.view_drag_handle)
        val topBarView = rootView.findViewById<View>(R.id.layout_top_bar)
        val dragListener = createDragTouchListener(params, rootView, touchSlop)
        dragHandleView?.setOnTouchListener(dragListener)
        topBarView?.setOnTouchListener(dragListener)

        // Bottom-Right and Bottom-Left Corner Resize Grips
        val resizeBr = rootView.findViewById<View>(R.id.resize_corner_br)
        val resizeBl = rootView.findViewById<View>(R.id.resize_corner_bl)
        val resizeListener = createResizeTouchListener(params, rootView, themedContext)
        resizeBr?.setOnTouchListener(resizeListener)
        resizeBl?.setOnTouchListener(resizeListener)

        // If start expanded preference is true, open drawer immediately
        if (isFloaterStartExpanded(themedContext)) {
            studioDrawerLayout?.visibility = View.VISIBLE
            params.height = getSavedHeightPx(currentOrientation, expanded = true)
            updateGeometryBadge(params.width, params.height)
        } else {
            updateGeometryBadge(params.width, params.height)
        }
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
        } else {
            val target = lastUnmutedVolumePct.coerceIn(10, 100)
            SongPlayer.setAppVolumePercent(this, target)
            updateVolumeMuteIcon(muted = false)
        }
    }

    private fun adjustVolumeStep(delta: Int) {
        val cur = SongPlayer.getAppVolumePercent(this)
        val next = (cur + delta).coerceIn(0, 100)
        SongPlayer.setAppVolumePercent(this, next)
        performHaptic(FloaterHapticStrength.LIGHT, isAccent = false)
    }

    private fun updateVolumeMuteIcon(muted: Boolean) {
        val btn = volumeMuteButton ?: return
        btn.setImageResource(
            if (muted) R.drawable.ic_mini_vol_off else R.drawable.ic_mini_vol_up,
        )
    }

    private fun toggleStudioDrawer(rootView: View, params: WindowManager.LayoutParams) {
        val drawer = studioDrawerLayout ?: return
        val wm = windowManager ?: return
        val isExpanded = drawer.visibility == View.VISIBLE
        if (isExpanded) {
            drawer.visibility = View.GONE
            params.height = getSavedHeightPx(currentOrientation, expanded = false)
        } else {
            drawer.visibility = View.VISIBLE
            params.height = getSavedHeightPx(currentOrientation, expanded = true)
        }
        updateGeometryBadge(params.width, params.height)
        runCatching {
            if (rootView.isAttachedToWindow) {
                wm.updateViewLayout(rootView, params)
            }
        }
    }

    private fun switchStudioTab(isEqTab: Boolean) {
        val eqTab = tabEqTextView ?: return
        val d3Tab = tab3dTextView ?: return
        val eqPanel = panelEqLayout ?: return
        val d3Panel = panel3dLayout ?: return

        if (isEqTab) {
            eqTab.setBackgroundResource(R.drawable.bg_mini_chip_active)
            eqTab.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            d3Tab.setBackgroundResource(R.drawable.bg_mini_chip_inactive)
            d3Tab.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            eqPanel.visibility = View.VISIBLE
            d3Panel.visibility = View.GONE
        } else {
            d3Tab.setBackgroundResource(R.drawable.bg_mini_chip_active)
            d3Tab.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            eqTab.setBackgroundResource(R.drawable.bg_mini_chip_inactive)
            eqTab.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            d3Panel.visibility = View.VISIBLE
            eqPanel.visibility = View.GONE
        }
    }

    private fun buildEqPresetChips(context: Context) {
        val container = eqPresetsContainer ?: return
        container.removeAllViews()
        val currentPreset = com.music.spotui.data.preferences.getEqPreset(context)

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
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (isSelected) android.R.color.black else android.R.color.white,
                    ),
                )
                setOnClickListener {
                    performHaptic(FloaterHapticStrength.LIGHT, isAccent = false)
                    EqPresetCatalog.applyPreset(context, preset)
                    SongPlayer.refreshAudioEffects(context)
                    buildEqPresetChips(context)
                    syncAudioFxUiFromPreferences()
                }
            }
            container.addView(chip)
        }
    }

    private fun buildSpatialProfileChips(context: Context) {
        val container = spatialProfilesContainer ?: return
        container.removeAllViews()
        val currentProfile = getSpatialProfile(context)

        SpatialProfile.values().forEach { profile ->
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
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (isSelected) android.R.color.black else android.R.color.white,
                    ),
                )
                setOnClickListener {
                    performHaptic(FloaterHapticStrength.LIGHT, isAccent = false)
                    setSpatialProfile(context, profile)
                    SongPlayer.refreshAudioEffects(context)
                    buildSpatialProfileChips(context)
                    syncAudioFxUiFromPreferences()
                }
            }
            container.addView(chip)
        }
    }

    private fun bindEqAnd3dControls(rootView: View, ctx: Context) {
        // Master EQ Toggle
        toggleEqView = rootView.findViewById<TextView>(R.id.toggle_floater_eq)?.apply {
            setOnClickListener {
                val next = !isEqEnabled(ctx)
                setEqEnabled(ctx, next)
                SongPlayer.refreshAudioEffects(ctx)
                syncAudioFxUiFromPreferences()
                performHaptic(FloaterHapticStrength.LIGHT, isAccent = false)
            }
        }
        // Bass Boost
        seekBassBoost = rootView.findViewById<SeekBar>(R.id.seek_floater_bass_boost)?.apply {
            max = 1000
            setOnSeekBarChangeListener(createSimpleSeekBarListener { p ->
                setBassBoostStrength(ctx, p)
                SongPlayer.refreshAudioEffects(ctx)
                tvBassBoostVal?.text = "${(p * 100f / 1000f).roundToInt()}%"
            })
        }
        tvBassBoostVal = rootView.findViewById(R.id.tv_floater_bass_boost_val)

        // EQ 64Hz
        seekEqBass = rootView.findViewById<SeekBar>(R.id.seek_floater_eq_bass)?.apply {
            max = 100
            setOnSeekBarChangeListener(createSimpleSeekBarListener { p ->
                setEqBass(ctx, p.toFloat())
                SongPlayer.refreshAudioEffects(ctx)
                val db = ((p - 50f) * 12f / 50f)
                tvEqBassVal?.text = String.format("%+.1f dB", db)
            })
        }
        tvEqBassVal = rootView.findViewById(R.id.tv_floater_eq_bass_val)

        // EQ Vocal 1kHz
        seekEqVocal = rootView.findViewById<SeekBar>(R.id.seek_floater_eq_vocal)?.apply {
            max = 100
            setOnSeekBarChangeListener(createSimpleSeekBarListener { p ->
                setEqVocal(ctx, p.toFloat())
                SongPlayer.refreshAudioEffects(ctx)
                val db = ((p - 50f) * 12f / 50f)
                tvEqVocalVal?.text = String.format("%+.1f dB", db)
            })
        }
        tvEqVocalVal = rootView.findViewById(R.id.tv_floater_eq_vocal_val)

        // EQ Treble 10kHz
        seekEqTreble = rootView.findViewById<SeekBar>(R.id.seek_floater_eq_treble)?.apply {
            max = 100
            setOnSeekBarChangeListener(createSimpleSeekBarListener { p ->
                setEqTreble(ctx, p.toFloat())
                SongPlayer.refreshAudioEffects(ctx)
                val db = ((p - 50f) * 12f / 50f)
                tvEqTrebleVal?.text = String.format("%+.1f dB", db)
            })
        }
        tvEqTrebleVal = rootView.findViewById(R.id.tv_floater_eq_treble_val)

        // Preamp (-12..+6 dB mapped to 0..180 steps of 0.1dB)
        seekEqPreamp = rootView.findViewById<SeekBar>(R.id.seek_floater_eq_preamp)?.apply {
            max = 180
            setOnSeekBarChangeListener(createSimpleSeekBarListener { p ->
                val db = -12.0f + (p * 18.0f / 180.0f)
                setEqPreampDb(ctx, db)
                SongPlayer.refreshAudioEffects(ctx)
                tvEqPreampVal?.text = String.format("%+.1f dB", db)
            })
        }
        tvEqPreampVal = rootView.findViewById(R.id.tv_floater_eq_preamp_val)

        // Normalizer & Limiter & Reset Flat
        toggleNormalizer = rootView.findViewById<TextView>(R.id.toggle_floater_normalizer)?.apply {
            setOnClickListener {
                val next = !isAudioNormalizerEnabled(ctx)
                setAudioNormalizerEnabled(ctx, next)
                SongPlayer.refreshAudioEffects(ctx)
                syncAudioFxUiFromPreferences()
            }
        }
        toggleLimiter = rootView.findViewById<TextView>(R.id.toggle_floater_limiter)?.apply {
            setOnClickListener {
                val next = !isLimiterEnabled(ctx)
                setLimiterEnabled(ctx, next)
                SongPlayer.refreshAudioEffects(ctx)
                syncAudioFxUiFromPreferences()
            }
        }
        btnEqReset = rootView.findViewById<TextView>(R.id.btn_floater_eq_reset)?.apply {
            setOnClickListener {
                performHaptic(FloaterHapticStrength.MEDIUM, isAccent = true)
                EqPresetCatalog.resetFlat(ctx)
                SongPlayer.refreshAudioEffects(ctx)
                buildEqPresetChips(ctx)
                syncAudioFxUiFromPreferences()
            }
        }

        // 3D Controls
        toggle3dView = rootView.findViewById<TextView>(R.id.toggle_floater_3d)?.apply {
            setOnClickListener {
                val next = !isEqSpatialEnabled(ctx)
                com.music.spotui.data.preferences.setEqSpatialEnabled(ctx, next)
                SongPlayer.refreshAudioEffects(ctx)
                syncAudioFxUiFromPreferences()
                performHaptic(FloaterHapticStrength.LIGHT, isAccent = false)
            }
        }
        tvRouteInfo = rootView.findViewById(R.id.tv_floater_route_info)

        seekSpatialStrength = rootView.findViewById<SeekBar>(R.id.seek_floater_spatial_strength)?.apply {
            max = 1000
            setOnSeekBarChangeListener(createSimpleSeekBarListener { p ->
                setSpatialStrength(ctx, p)
                SongPlayer.refreshAudioEffects(ctx)
                tvSpatialStrengthVal?.text = "${(p * 100f / 1000f).roundToInt()}%"
            })
        }
        tvSpatialStrengthVal = rootView.findViewById(R.id.tv_floater_spatial_strength_val)

        seekRoomDelay = rootView.findViewById<SeekBar>(R.id.seek_floater_room_delay)?.apply {
            max = 26
            setOnSeekBarChangeListener(createSimpleSeekBarListener { p ->
                val ms = 2f + p.toFloat()
                setSpatialRoomDelayMs(ctx, ms)
                SongPlayer.refreshAudioEffects(ctx)
                tvRoomDelayVal?.text = "${ms.roundToInt()} ms"
            })
        }
        tvRoomDelayVal = rootView.findViewById(R.id.tv_floater_room_delay_val)

        seekElevation = rootView.findViewById<SeekBar>(R.id.seek_floater_elevation)?.apply {
            max = 100
            setOnSeekBarChangeListener(createSimpleSeekBarListener { p ->
                val depth = p.toFloat() / 100f
                setSpatialElevationDepth(ctx, depth)
                SongPlayer.refreshAudioEffects(ctx)
                tvElevationVal?.text = "$p%"
            })
        }
        tvElevationVal = rootView.findViewById(R.id.tv_floater_elevation_val)

        row8dSpeed = rootView.findViewById(R.id.row_floater_8d_speed)
        seek8dSpeed = rootView.findViewById<SeekBar>(R.id.seek_floater_8d_speed)?.apply {
            max = 140
            setOnSeekBarChangeListener(createSimpleSeekBarListener { p ->
                val hz = 0.10f + (p.toFloat() / 100f)
                setSpatialOrbitSpeed(ctx, hz)
                SongPlayer.refreshAudioEffects(ctx)
                tv8dSpeedVal?.text = String.format("%.2fHz", hz)
            })
        }
        tv8dSpeedVal = rootView.findViewById(R.id.tv_floater_8d_speed_val)

        toggle8d = rootView.findViewById<TextView>(R.id.toggle_floater_8d)?.apply {
            setOnClickListener {
                val next = !isSpatial8dOrbitEnabled(ctx)
                setSpatial8dOrbitEnabled(ctx, next)
                SongPlayer.refreshAudioEffects(ctx)
                syncAudioFxUiFromPreferences()
            }
        }
        toggleCrossfeed = rootView.findViewById<TextView>(R.id.toggle_floater_crossfeed)?.apply {
            setOnClickListener {
                val next = !isHeadphoneCrossfeedEnabled(ctx)
                setHeadphoneCrossfeedEnabled(ctx, next)
                SongPlayer.refreshAudioEffects(ctx)
                syncAudioFxUiFromPreferences()
            }
        }
        toggleCorrGuard = rootView.findViewById<TextView>(R.id.toggle_floater_corr_guard)?.apply {
            setOnClickListener {
                val next = !isSpatialCorrelationGuardEnabled(ctx)
                setSpatialCorrelationGuardEnabled(ctx, next)
                SongPlayer.refreshAudioEffects(ctx)
                syncAudioFxUiFromPreferences()
            }
        }
    }

    private fun createSimpleSeekBarListener(onChanged: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
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
            setTextColor(ContextCompat.getColor(ctx, if (eqActive) android.R.color.black else android.R.color.white))
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
        toggleNormalizer?.setTextColor(ContextCompat.getColor(ctx, if (normActive) android.R.color.black else android.R.color.white))

        val limitActive = isLimiterEnabled(ctx)
        toggleLimiter?.setBackgroundResource(if (limitActive) R.drawable.bg_mini_chip_active else R.drawable.bg_mini_chip_inactive)
        toggleLimiter?.setTextColor(ContextCompat.getColor(ctx, if (limitActive) android.R.color.black else android.R.color.white))

        // 3D
        val d3Active = isEqSpatialEnabled(ctx)
        toggle3dView?.apply {
            text = if (d3Active) "3D ON" else "3D OFF"
            setBackgroundResource(if (d3Active) R.drawable.bg_mini_chip_active else R.drawable.bg_mini_chip_inactive)
            setTextColor(ContextCompat.getColor(ctx, if (d3Active) android.R.color.black else android.R.color.white))
        }
        val routeInfo = AudioEffectController.refreshOutputRoute(ctx)
        tvRouteInfo?.text = "Route: ${routeInfo.route.label} (${routeInfo.deviceName})"

        seekSpatialStrength?.progress = com.music.spotui.data.preferences.getSpatialStrength(ctx)
        tvSpatialStrengthVal?.text = "${(com.music.spotui.data.preferences.getSpatialStrength(ctx) * 100f / 1000f).roundToInt()}%"

        val delayMs = getSpatialRoomDelayMs(ctx)
        seekRoomDelay?.progress = (delayMs - 2f).roundToInt().coerceIn(0, 26)
        tvRoomDelayVal?.text = "${delayMs.roundToInt()} ms"

        val elev = getSpatialElevationDepth(ctx)
        seekElevation?.progress = (elev * 100f).roundToInt().coerceIn(0, 100)
        tvElevationVal?.text = "${(elev * 100f).roundToInt()}%"

        val orbitEnabled = isSpatial8dOrbitEnabled(ctx)
        row8dSpeed?.visibility = if (orbitEnabled) View.VISIBLE else View.GONE
        toggle8d?.setBackgroundResource(if (orbitEnabled) R.drawable.bg_mini_chip_active else R.drawable.bg_mini_chip_inactive)
        toggle8d?.setTextColor(ContextCompat.getColor(ctx, if (orbitEnabled) android.R.color.black else android.R.color.white))
        val speedHz = getSpatialOrbitSpeed(ctx)
        seek8dSpeed?.progress = ((speedHz - 0.10f) * 100f).roundToInt().coerceIn(0, 140)
        tv8dSpeedVal?.text = String.format("%.2fHz", speedHz)

        val crossfeedActive = isHeadphoneCrossfeedEnabled(ctx)
        toggleCrossfeed?.setBackgroundResource(if (crossfeedActive) R.drawable.bg_mini_chip_active else R.drawable.bg_mini_chip_inactive)
        toggleCrossfeed?.setTextColor(ContextCompat.getColor(ctx, if (crossfeedActive) android.R.color.black else android.R.color.white))

        val corrActive = isSpatialCorrelationGuardEnabled(ctx)
        toggleCorrGuard?.setBackgroundResource(if (corrActive) R.drawable.bg_mini_chip_active else R.drawable.bg_mini_chip_inactive)
        toggleCorrGuard?.setTextColor(ContextCompat.getColor(ctx, if (corrActive) android.R.color.black else android.R.color.white))
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

        return View.OnTouchListener { _, event ->
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
                        performHaptic(FloaterHapticStrength.LIGHT, isAccent = false)
                    }
                    if (dragging) {
                        val isBottomAnchored = (params.gravity and Gravity.BOTTOM) == Gravity.BOTTOM
                        val rawX = initialX + dx.roundToInt()
                        val rawY = if (isBottomAnchored) {
                            initialY - dy.roundToInt()
                        } else {
                            initialY + dy.roundToInt()
                        }
                        val (clampedX, clampedY) = clampCoordinatesToDisplay(rawX, rawY)
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
                        saveDragCoordinates(resources.configuration.orientation, params.x, params.y)
                        performHaptic(FloaterHapticStrength.LIGHT, isAccent = false)
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

        return View.OnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialWidth = params.width
                    initialHeight = params.height
                    initialX = params.x
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isBottomLeft = v.id == R.id.resize_corner_bl
                    resizing = true
                    lastHapticStepW = initialWidth
                    cancelAutoDismissTimer()
                    performHaptic(FloaterHapticStrength.LIGHT, isAccent = false)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (resizing) {
                        val dx = (event.rawX - initialTouchX).roundToInt()
                        val dy = (event.rawY - initialTouchY).roundToInt()
                        val metrics = resources.displayMetrics
                        val maxW = (metrics.widthPixels - dpToPx(16)).coerceAtMost(dpToPx(560))
                        val minW = dpToPx(280)
                        val maxH = (metrics.heightPixels - dpToPx(48))
                        val minH = dpToPx(120)

                        if (isBottomLeft) {
                            val newW = (initialWidth - dx).coerceIn(minW, maxW)
                            val actualDeltaW = initialWidth - newW
                            params.width = newW
                            params.x = initialX + actualDeltaW
                        } else {
                            params.width = (initialWidth + dx).coerceIn(minW, maxW)
                        }
                        params.height = (initialHeight + dy).coerceIn(minH, maxH)

                        if (abs(params.width - lastHapticStepW) >= dpToPx(24)) {
                            lastHapticStepW = params.width
                            performHaptic(FloaterHapticStrength.LIGHT, isAccent = false)
                        }

                        updateGeometryBadge(params.width, params.height)
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
                        saveGeometry(resources.configuration.orientation, params.width, params.height)
                        performHaptic(FloaterHapticStrength.MEDIUM, isAccent = true)
                    }
                    resetAutoDismissTimer()
                    true
                }
                else -> false
            }
        }
    }

    private fun updateGeometryBadge(widthPx: Int, heightPx: Int) {
        val badge = statusBadgeTextView ?: return
        val wDp = (widthPx / resources.displayMetrics.density).roundToInt()
        val hDp = (heightPx / resources.displayMetrics.density).roundToInt()
        badge.text = "${wDp}×${hDp}dp"
    }

    private fun openMainAppAndDismiss() {
        runCatching {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        }
        hideOverlay()
    }

    private fun performHaptic(strengthPref: FloaterHapticStrength, isAccent: Boolean) {
        if (!isFloaterHapticsEnabled(this)) return
        val configuredStrength = getFloaterHapticStrength(this)
        val durationMs = if (isAccent) configuredStrength.accentDurationMs else configuredStrength.clickDurationMs
        val amplitude = configuredStrength.amplitude

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vib = vm?.defaultVibrator ?: return@runCatching
                if (vib.hasVibrator()) {
                    val effect = VibrationEffect.createOneShot(durationMs, amplitude)
                    vib.vibrate(effect)
                }
            } else {
                @Suppress("DEPRECATION")
                val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (vib != null && vib.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(durationMs)
                    }
                }
            }
        }
    }

    private fun hideOverlay() {
        cancelAutoDismissTimer()
        val view = overlayView
        overlayView = null
        overlayParams = null
        statusBadgeTextView = null
        titleTextView = null
        artistTextView = null
        prevButton = null
        playPauseButton = null
        nextButton = null
        closeButton = null
        openAppButton = null
        fxToggleButton = null
        volumeSeekBar = null
        volumePercentTextView = null
        isUserSeekingVolume = false
        studioDrawerLayout = null

        if (view != null) {
            runCatching {
                if (view.isAttachedToWindow) {
                    windowManager?.removeViewImmediate(view)
                }
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

    private fun applyOrientationPlacement(params: WindowManager.LayoutParams, orientation: Int) {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        params.gravity = if (isLandscape) {
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        } else {
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }

        val prefix = if (isLandscape) KEY_LANDSCAPE_PREFIX else KEY_PORTRAIT_PREFIX
        val defaultY = dpToPx(if (isLandscape) 48 else 140)
        val hasSaved = prefs.getBoolean("${prefix}_saved", false)
        val targetX = if (hasSaved) prefs.getInt("${prefix}_x", 0) else 0
        val targetY = if (hasSaved) prefs.getInt("${prefix}_y", defaultY) else defaultY

        val (clampedX, clampedY) = clampCoordinatesToDisplay(targetX, targetY)
        params.x = clampedX
        params.y = clampedY
    }

    private fun saveDragCoordinates(orientation: Int, x: Int, y: Int) {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val prefix = if (isLandscape) KEY_LANDSCAPE_PREFIX else KEY_PORTRAIT_PREFIX
        val (clampedX, clampedY) = clampCoordinatesToDisplay(x, y)
        prefs.edit()
            .putBoolean("${prefix}_saved", true)
            .putInt("${prefix}_x", clampedX)
            .putInt("${prefix}_y", clampedY)
            .apply()
    }

    private fun saveGeometry(orientation: Int, widthPx: Int, heightPx: Int) {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val prefix = if (isLandscape) KEY_LANDSCAPE_PREFIX else KEY_PORTRAIT_PREFIX
        val isExpanded = studioDrawerLayout?.visibility == View.VISIBLE
        val keyW = if (isExpanded) "${prefix}_w_exp" else "${prefix}_w_comp"
        val keyH = if (isExpanded) "${prefix}_h_exp" else "${prefix}_h_comp"
        prefs.edit()
            .putInt(keyW, widthPx)
            .putInt(keyH, heightPx)
            .apply()
    }

    private fun getSavedWidthPx(orientation: Int): Int {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val prefix = if (isLandscape) KEY_LANDSCAPE_PREFIX else KEY_PORTRAIT_PREFIX
        val defW = dpToPx(if (isLandscape) 360 else 328)
        val isExpanded = studioDrawerLayout?.visibility == View.VISIBLE
        val keyW = if (isExpanded) "${prefix}_w_exp" else "${prefix}_w_comp"
        return prefs.getInt(keyW, defW)
    }

    private fun getSavedHeightPx(orientation: Int, expanded: Boolean): Int {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        val prefix = if (isLandscape) KEY_LANDSCAPE_PREFIX else KEY_PORTRAIT_PREFIX
        val defH = dpToPx(if (expanded) (if (isLandscape) 260 else 340) else 108)
        val keyH = if (expanded) "${prefix}_h_exp" else "${prefix}_h_comp"
        return prefs.getInt(keyH, defH)
    }

    private fun clampCoordinatesToDisplay(x: Int, y: Int): Pair<Int, Int> {
        val metrics = resources.displayMetrics
        val halfW = dpToPx(180)
        val maxOffsetX = ((metrics.widthPixels / 2) - halfW / 4).coerceAtLeast(80)
        val maxOffsetY = (metrics.heightPixels - dpToPx(96)).coerceAtLeast(80)
        val clampedX = x.coerceIn(-maxOffsetX, maxOffsetX)
        val clampedY = y.coerceIn(dpToPx(8), maxOffsetY)
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
        val am = runCatching { getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager }.getOrNull() ?: return
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
        const val FLOATER_OVERLAY_PREFS_NAME = "spotui_accessibility_mini_player"
        private const val KEY_PORTRAIT_PREFIX = "portrait"
        private const val KEY_LANDSCAPE_PREFIX = "landscape"
    }
}
