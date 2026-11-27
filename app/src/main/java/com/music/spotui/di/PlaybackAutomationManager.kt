package com.music.spotui.di

import android.content.Context
import com.music.spotui.data.preferences.ProAudioPreferences
import com.music.spotui.data.preferences.SpeedPitchPreset
import com.music.spotui.data.preferences.TrackBookmark
import com.music.spotui.data.preferences.setPlaybackPitchPref
import com.music.spotui.data.preferences.setPlaybackSpeedPref
import com.music.spotui.translator.GameVoiceTranslatorEngine
import com.music.spotui.translator.TranslationEnginePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Coordinates smart playback automation:
 * - Teammate Voice Ducking synchronized with [GameVoiceTranslatorEngine.teammateSpeaking]
 * - Finish-Current-Track Sleep Timer with smooth fade-out
 * - Per-track timestamp bookmarks
 * - Speed/Pitch preset coordination
 */
object PlaybackAutomationManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _duckingGain = MutableStateFlow(1.0f)
    val duckingGain: StateFlow<Float> = _duckingGain.asStateFlow()

    @Volatile
    private var observerStarted = false

    fun ensureStarted(context: Context) {
        val appCtx = context.applicationContext
        ProAudioPreferences.hydrateIfNeeded(appCtx)
        TranslationEnginePreferences.hydrateIfNeeded(appCtx)
        if (observerStarted) return
        synchronized(this) {
            if (observerStarted) return
            observerStarted = true
            scope.launch {
                combine(
                    GameVoiceTranslatorEngine.teammateSpeaking,
                    TranslationEnginePreferences.state,
                ) { speaking, cfg ->
                    if (speaking && cfg.teammateVoiceDuckingPct > 0) {
                        (1f - (cfg.teammateVoiceDuckingPct.coerceIn(0, 80) / 100f)).coerceIn(0.2f, 1.0f)
                    } else {
                        1.0f
                    }
                }.collect { targetGain ->
                    if (_duckingGain.value != targetGain) {
                        _duckingGain.value = targetGain
                        SongPlayer.refreshLiveVolume()
                    }
                }
            }
        }
    }

    fun currentDuckingGain(): Float = _duckingGain.value

    fun setFinishTrackSleep(context: Context, enabled: Boolean) {
        ProAudioPreferences.setFinishTrackSleepEnabled(context.applicationContext, enabled)
    }

    /**
     * Checks whether the finish-current-track sleep timer is armed; if so, disarms it and returns true
     * so PlaybackService pauses cleanly instead of advancing to the next track.
     */
    fun consumeFinishTrackSleepIfArmed(context: Context): Boolean {
        val appCtx = context.applicationContext
        val cfg = ProAudioPreferences.hydrateIfNeeded(appCtx)
        if (!cfg.finishTrackSleepEnabled) return false
        ProAudioPreferences.setFinishTrackSleepEnabled(appCtx, false)
        return true
    }

    fun applySpeedPitchPreset(context: Context, preset: SpeedPitchPreset) {
        val appCtx = context.applicationContext
        setPlaybackSpeedPref(appCtx, preset.speed)
        setPlaybackPitchPref(appCtx, preset.pitch)
        ProAudioPreferences.setActiveSpeedPitchPreset(appCtx, preset)
        SongPlayer.refreshPlaybackTuning(appCtx)
        SongPlayer.refreshAudioOffloadPolicy(appCtx)
    }

    fun addBookmark(
        context: Context,
        trackKey: String,
        trackTitle: String,
        positionMs: Long,
        note: String = "",
    ): TrackBookmark {
        val appCtx = context.applicationContext
        val current = ProAudioPreferences.hydrateIfNeeded(appCtx).bookmarks
        val cleanNote = note.trim().ifBlank { formatBookmarkLabel(positionMs) }
        val entry = TrackBookmark(
            id = UUID.randomUUID().toString(),
            trackKey = trackKey,
            trackTitle = trackTitle,
            positionMs = positionMs.coerceAtLeast(0L),
            note = cleanNote,
            createdAtMs = System.currentTimeMillis(),
        )
        ProAudioPreferences.saveBookmarks(appCtx, listOf(entry) + current)
        return entry
    }

    fun deleteBookmark(context: Context, id: String) {
        val appCtx = context.applicationContext
        val current = ProAudioPreferences.hydrateIfNeeded(appCtx).bookmarks
        ProAudioPreferences.saveBookmarks(appCtx, current.filterNot { it.id == id })
    }

    fun bookmarksForTrack(context: Context, trackKey: String): List<TrackBookmark> {
        val all = ProAudioPreferences.hydrateIfNeeded(context.applicationContext).bookmarks
        return if (trackKey.isBlank()) all else all.filter { it.trackKey == trackKey }
    }

    private fun formatBookmarkLabel(ms: Long): String {
        val sec = (ms / 1000L).coerceAtLeast(0L)
        return "Drop @ ${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"
    }
}
