package com.music.spotui.accessibility

import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.preferences.SpatialProfile
import com.music.spotui.di.RepeatMode

enum class FloaterTab(val key: String, val label: String) {
    PLAYER("player", "Player"),
    QUEUE("queue", "Queue"),
    EQ("eq", "EQ"),
    SPATIAL_3D("3d", "3D"),
    TOOLS("tools", "Tools"),
    TRANSLATE("translate", "Translate"),
}

data class FloaterUiModel(
    // Playback metadata & transport
    val songId: Int = 0,
    val title: String = "SpotUI Ready",
    val artist: String = "Tap Play to resume",
    val album: String = "",
    val coverUri: String = "",
    val sourceLabel: String = "SPOTUI",
    val qualityLabel: String = "32-bit DSP",
    val isPlaying: Boolean = false,
    val isResolving: Boolean = false,
    val isShuffled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isLiked: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val activeQueueIndex: Int = -1,
    val queue: List<SongsModel> = emptyList(),

    // Dedicated In-App Volume (OS volume untouched)
    val inAppVolumePct: Int = 100,

    // Drawer & Layout State
    val isDrawerExpanded: Boolean = false,
    val floaterEq3dEnabled: Boolean = true,
    val isSidebarCompact: Boolean = false,
    val activeTab: FloaterTab = FloaterTab.PLAYER,
    val widthDp: Int = 360,
    val heightDp: Int = 460,

    // 7-Band EQ + Master Dynamics
    val eqEnabled: Boolean = true,
    val eqPresetName: String = "Flat",
    val bandSubBass: Float = 50f,
    val bandBass: Float = 50f,
    val bandLowMid: Float = 50f,
    val bandVocal: Float = 50f,
    val bandHighMid: Float = 50f,
    val bandTreble: Float = 50f,
    val bandBrilliance: Float = 50f,
    val bassBoostStrength: Int = 0,
    val preampDb: Float = 0f,
    val normalizerEnabled: Boolean = true,
    val limiterEnabled: Boolean = true,

    // 3D Spatial Soundstage
    val spatialEnabled: Boolean = false,
    val spatialProfile: SpatialProfile = SpatialProfile.IMMERSIVE,
    val spatialStrength: Int = 900,
    val spatialRoomDelayMs: Float = 12f,
    val spatialElevationDepth: Float = 0.7f,
    val advanced8dEnabled: Boolean = false,
    val advanced8dDepth: Float = 0.85f,
    val crossfeedEnabled: Boolean = false,
    val correlationGuardEnabled: Boolean = true,
    val audioRouteLabel: String = "Phone Speaker",
    val audioDeviceName: String = "Device Speaker",

    // Pro Tools
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val skipSilence: Boolean = false,
    val sleepTimerRemainingMs: Long = 0L,
    val abLoopStartMs: Long = -1L,
    val abLoopEndMs: Long = -1L,
    val autoHideMs: Long = 12_000L,
    val hapticsEnabled: Boolean = true,

    // Foreground Game Detection & Live Translator State
    val isGameActive: Boolean = false,
    val activeGameLabel: String? = null,
    val alwaysUnlockTranslator: Boolean = false,
    val targetLangCode: String = "en",
    val continuousListen: Boolean = true,
    val micRmsNormalized: Float = 0f,
    val speechEngineAvailable: Boolean = true,
    val speechEngineLabel: String = "",
    val downloadedModelLangs: Set<String> = emptySet(),
    val modelDownloadBusyLang: String? = null,
)

interface FloaterCallbacks {
    fun onInteraction()
    fun onHaptic(isAccent: Boolean = false)
    fun onClose()
    fun onOpenApp()
    fun onToggleLike()
    fun onToggleExpand()
    fun onSelectTab(tab: FloaterTab)
    fun onToggleSidebarCompact()
    fun onSnapOverlayToEdge()

    // Transport
    fun onTogglePlayPause()
    fun onPrevious()
    fun onNext()
    fun onToggleShuffle()
    fun onCycleRepeat()
    fun onSeekTo(positionMs: Long)
    fun onSeekBy(deltaMs: Long)
    fun onPlayQueueIndex(index: Int)
    fun onShuffleUpcomingQueue()
    fun onDeduplicateQueue()

    // In-App Volume
    fun onVolumeChange(percent: Int)
    fun onToggleMute()

    // EQ
    fun onToggleEq(enabled: Boolean)
    fun onSelectEqPreset(name: String)
    fun onEqBandChange(bandIndex: Int, value: Float)
    fun onBassBoostChange(strength: Int)
    fun onPreampChange(db: Float)
    fun onToggleNormalizer(enabled: Boolean)
    fun onToggleLimiter(enabled: Boolean)
    fun onResetEqFlat()

    // 3D Spatial & Advanced 8D
    fun onToggleSpatial(enabled: Boolean)
    fun onSelectSpatialProfile(profile: SpatialProfile)
    fun onSpatialStrengthChange(strength: Int)
    fun onSpatialDelayChange(delayMs: Float)
    fun onSpatialElevationChange(depth: Float)
    fun onToggle8dAudio(enabled: Boolean)
    fun on8dDepthChange(depth: Float)
    fun onToggleCrossfeed(enabled: Boolean)
    fun onToggleCorrelationGuard(enabled: Boolean)

    // Pro Tools
    fun onSpeedChange(speed: Float)
    fun onPitchChange(pitch: Float)
    fun onResetSpeedPitch()
    fun onToggleSkipSilence(enabled: Boolean)
    fun onSetSleepTimer(minutes: Int)
    fun onSetAbStart()
    fun onSetAbEnd()
    fun onClearAbLoop()
    fun onCycleAutoHide()
    fun onToggleHaptics(enabled: Boolean)
    fun onResetGeometry()

    // Drag & Corner Resize (px deltas)
    fun onHeaderDrag(dxPx: Float, dyPx: Float)
    fun onHeaderDragEnd()
    fun onCornerResize(dxPx: Float, dyPx: Float, isLeftCorner: Boolean)
    fun onCornerResizeEnd()

    // Game Live Translation
    fun onRequestMicPermission()
    fun onToggleListening()
    fun onSelectTranslateSource(code: String)
    fun onSelectTranslateTarget(code: String)
    fun onSwapTranslateLanguages()
    fun onToggleContinuousListen(continuous: Boolean)
    fun onDownloadLanguagePack(code: String)
    fun onTranslateManualText(text: String)
    fun onToggleBubbleVisible(enabled: Boolean)
    fun onBubbleWidthChange(widthDp: Float)
    fun onBubbleTextSizeChange(sizeSp: Float)
    fun onBubbleOpacityChange(opacity: Float)
    fun onResetBubblePosition()
    fun onClearTranslationHistory()
}

internal fun formatFloaterDuration(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(java.util.Locale.US, "%d:%02d", min, sec)
}
