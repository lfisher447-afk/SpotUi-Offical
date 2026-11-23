package com.music.spotui.accessibility

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
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
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.music.spotui.R
import com.music.spotui.ui.notification.PlaybackService
import com.music.spotui.util.DevConsoleManager
import com.music.spotui.util.DevConsoleManager.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Samsung One UI 9.0 / Android 17 Accessibility Button Floating Mini-Player Service.
 *
 * Uses [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY] with
 * `canRetrieveWindowContent="false"` and `isAccessibilityTool="true"` so Spotui never
 * requires `SYSTEM_ALERT_WINDOW` and displays safely over full-screen landscape games
 * (e.g. Call of Duty: Mobile) without disrupting gameplay.
 *
 * Hardened features:
 * - Prioritizes bound Media3 [MediaController] IPC calls (`play`, `pause`, `seekToNext`,
 *   `seekToPrevious`) over raw background `startService()` calls for Android 17 Background
 *   Audio Hardening compatibility.
 * - 5-second inactivity auto-dismiss timer reset on transport taps, drags, or volume seeks.
 * - Separate Portrait / Landscape persisted `(x, y)` drag coordinates clamped to display bounds.
 */
class MediaAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var windowManager: WindowManager? = null
    private var audioManager: AudioManager? = null
    private val prefs: SharedPreferences by lazy(LazyThreadSafetyMode.NONE) {
        applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var currentOrientation: Int = Configuration.ORIENTATION_UNDEFINED

    private var titleTextView: TextView? = null
    private var artistTextView: TextView? = null
    private var prevButton: ImageButton? = null
    private var playPauseButton: ImageButton? = null
    private var nextButton: ImageButton? = null
    private var closeButton: ImageButton? = null
    private var volumeSeekBar: SeekBar? = null
    private var volumePercentTextView: TextView? = null

    private var isUserSeekingVolume = false
    private var isVolumeObserverRegistered = false

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var accessibilityButtonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoDismissRunnable = Runnable {
        hideOverlay()
    }

    private val volumeObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            onChange(selfChange, null)
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (!isUserSeekingVolume) {
                syncVolumeSliderFromSystem()
            }
        }
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
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        currentOrientation = resources.configuration.orientation

        registerAccessibilityButton()
        ensureMediaControllerConnected()

        DevConsoleManager.log(
            LogLevel.INFO,
            TAG,
            "MediaAccessibilityService connected (One UI 9.0 overlay ready)",
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally unused: canRetrieveWindowContent="false" keeps sideloading minimal and privacy-safe.
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
        audioManager = null
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
                LogLevel.ERROR,
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

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
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

            registerVolumeObserver()
            syncVolumeSliderFromSystem()
            ensureMediaControllerConnected()
            mediaController?.let { syncUiFromController(it) }
            resetAutoDismissTimer()
        } catch (t: Throwable) {
            DevConsoleManager.log(
                LogLevel.ERROR,
                TAG,
                "Failed to inflate/attach TYPE_ACCESSIBILITY_OVERLAY mini-player: ${t.message}",
                t,
            )
            hideOverlay()
        }
    }

    private fun bindOverlayViews(
        rootView: View,
        themedContext: Context,
        params: WindowManager.LayoutParams,
    ) {
        val pillRoot = rootView.findViewById<View>(R.id.mini_player_root)
        titleTextView = rootView.findViewById<TextView>(R.id.tv_mini_title)?.apply {
            isSelected = true
        }
        artistTextView = rootView.findViewById(R.id.tv_mini_artist)
        prevButton = rootView.findViewById<ImageButton>(R.id.btn_mini_prev)?.apply {
            setOnClickListener {
                resetAutoDismissTimer()
                handlePreviousClick()
            }
        }
        playPauseButton = rootView.findViewById<ImageButton>(R.id.btn_mini_play_pause)?.apply {
            setOnClickListener {
                resetAutoDismissTimer()
                handlePlayPauseClick()
            }
        }
        nextButton = rootView.findViewById<ImageButton>(R.id.btn_mini_next)?.apply {
            setOnClickListener {
                resetAutoDismissTimer()
                handleNextClick()
            }
        }
        closeButton = rootView.findViewById<ImageButton>(R.id.btn_mini_close)?.apply {
            setOnClickListener { hideOverlay() }
        }
        volumePercentTextView = rootView.findViewById(R.id.tv_mini_volume_pct)
        volumeSeekBar = rootView.findViewById<SeekBar>(R.id.seek_mini_volume)?.apply {
            val am = audioManager ?: (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
            max = (am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    updateVolumePercentLabel(progress, seekBar?.max ?: 15)
                    if (fromUser) {
                        resetAutoDismissTimer()
                        runCatching {
                            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                        }
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isUserSeekingVolume = true
                    cancelAutoDismissTimer()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    isUserSeekingVolume = false
                    resetAutoDismissTimer()
                }
            })
        }

        // Dismiss immediately on outside touch if user taps away.
        rootView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                hideOverlay()
                true
            } else {
                false
            }
        }

        // Smooth drag gesture anywhere on the pill card body without fighting button clicks.
        val touchSlop = ViewConfiguration.get(themedContext).scaledTouchSlop
        pillRoot?.setOnTouchListener(
            createDragTouchListener(
                params = params,
                targetView = rootView,
                touchSlop = touchSlop,
            ),
        )
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
                    }
                    resetAutoDismissTimer()
                    wasDragging
                }

                else -> false
            }
        }
    }

    private fun hideOverlay() {
        cancelAutoDismissTimer()
        unregisterVolumeObserver()
        val view = overlayView
        overlayView = null
        overlayParams = null
        titleTextView = null
        artistTextView = null
        prevButton = null
        playPauseButton = null
        nextButton = null
        closeButton = null
        volumeSeekBar = null
        volumePercentTextView = null
        isUserSeekingVolume = false

        if (view != null) {
            runCatching {
                if (view.isAttachedToWindow) {
                    windowManager?.removeViewImmediate(view)
                }
            }.onFailure { err ->
                DevConsoleManager.log(
                    LogLevel.WARNING,
                    TAG,
                    "Error detaching mini-player overlay cleanly: ${err.message}",
                    err,
                )
            }
        }
    }

    private fun resetAutoDismissTimer() {
        if (overlayView == null) return
        mainHandler.removeCallbacks(autoDismissRunnable)
        mainHandler.postDelayed(autoDismissRunnable, AUTO_DISMISS_TIMEOUT_MS)
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

    private fun clampCoordinatesToDisplay(x: Int, y: Int): Pair<Int, Int> {
        val metrics = resources.displayMetrics
        val halfPillWidthPx = dpToPx(168)
        val pillHeightMarginPx = dpToPx(96)
        val maxOffsetX = ((metrics.widthPixels / 2) - halfPillWidthPx / 4).coerceAtLeast(80)
        val maxOffsetY = (metrics.heightPixels - pillHeightMarginPx).coerceAtLeast(80)
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
                    DevConsoleManager.log(
                        LogLevel.WARNING,
                        TAG,
                        "MediaController connection failed; fallback media keys remain active",
                    )
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
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
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
            // Prioritize direct MediaController Binder IPC over startService() for Android 17 background audio compliance.
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
        val am = audioManager ?: (getSystemService(Context.AUDIO_SERVICE) as? AudioManager).also {
            audioManager = it
        } ?: return
        val now = SystemClock.uptimeMillis()
        runCatching {
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        }.onFailure { err ->
            DevConsoleManager.log(
                LogLevel.WARNING,
                TAG,
                "MediaKey fallback failed for keyCode=$keyCode: ${err.message}",
                err,
            )
        }
    }

    private fun registerVolumeObserver() {
        if (isVolumeObserverRegistered) return
        runCatching {
            contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                volumeObserver,
            )
            isVolumeObserverRegistered = true
        }
    }

    private fun unregisterVolumeObserver() {
        if (!isVolumeObserverRegistered) return
        isVolumeObserverRegistered = false
        runCatching {
            contentResolver.unregisterContentObserver(volumeObserver)
        }
    }

    private fun syncVolumeSliderFromSystem() {
        val seekBar = volumeSeekBar ?: return
        val am = audioManager ?: return
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxVol)
        if (seekBar.max != maxVol) seekBar.max = maxVol
        if (seekBar.progress != curVol) seekBar.progress = curVol
        updateVolumePercentLabel(curVol, maxVol)
    }

    private fun updateVolumePercentLabel(progress: Int, maxVol: Int) {
        val pct = if (maxVol <= 0) 0 else ((progress * 100f) / maxVol).roundToInt().coerceIn(0, 100)
        volumePercentTextView?.text = "$pct%"
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val TAG = "MediaAccessibility"
        private const val PREFS_NAME = "spotui_accessibility_mini_player"
        private const val KEY_PORTRAIT_PREFIX = "portrait"
        private const val KEY_LANDSCAPE_PREFIX = "landscape"
        private const val AUTO_DISMISS_TIMEOUT_MS = 5_000L
    }
}
