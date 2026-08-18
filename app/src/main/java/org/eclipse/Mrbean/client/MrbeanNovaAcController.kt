package org.eclipse.Mrbean.client

import android.content.Context
import com.music.spotui.data.export.NovaAcExportManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Dedicated MrBean control plane for NovaAc archival work. This is intentionally
 * separate from MrBean media playback networking: it governs only local archive
 * staging, framed encryption throughput, SAF delivery, and recovery telemetry.
 */
object MrbeanNovaAcController {
    private const val PREFS = "mrbean_novaac_controller"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PROFILE = "profile"
    private const val KEY_KEEP_RECOVERY = "keep_recovery"
    private const val KEY_LAST_SUMMARY = "last_summary"

    enum class Profile(
        val archiveBufferBytes: Int,
        val deliveryBufferBytes: Int,
        val label: String,
    ) {
        FAST(256 * 1024, 512 * 1024, "Fast"),
        BALANCED(128 * 1024, 256 * 1024, "Balanced"),
        RESILIENT(64 * 1024, 128 * 1024, "Resilient"),
    }

    data class Snapshot(
        val enabled: Boolean,
        val profile: Profile,
        val keepRecoveryCopies: Boolean,
    ) {
        val archiveBufferBytes: Int get() = if (enabled) profile.archiveBufferBytes else 128 * 1024
        val deliveryBufferBytes: Int get() = if (enabled) profile.deliveryBufferBytes else 256 * 1024
    }

    data class OperationRecord(
        val operationId: String,
        val collectionName: String,
        val profile: Profile,
        val startedAtMillis: Long,
        val stage: NovaAcExportManager.ExportStage,
        val sourceBytes: Long,
        val archiveBytes: Long,
        val bytesPerSecond: Long,
        val etaMillis: Long,
        val includedCount: Int,
        val skippedCount: Int,
        val terminalMessage: String = "",
        val failureCode: String? = null,
    )

    private val active = ConcurrentHashMap<String, OperationRecord>()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun snapshot(context: Context): Snapshot {
        val raw = prefs(context).getString(KEY_PROFILE, Profile.BALANCED.name)
        val profile = runCatching { Profile.valueOf(raw.orEmpty()) }.getOrDefault(Profile.BALANCED)
        return Snapshot(
            enabled = prefs(context).getBoolean(KEY_ENABLED, true),
            profile = profile,
            keepRecoveryCopies = prefs(context).getBoolean(KEY_KEEP_RECOVERY, true),
        )
    }

    fun setEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()

    fun setProfile(context: Context, profile: Profile) =
        prefs(context).edit().putString(KEY_PROFILE, profile.name).apply()

    fun setKeepRecoveryCopies(context: Context, keep: Boolean) =
        prefs(context).edit().putBoolean(KEY_KEEP_RECOVERY, keep).apply()

    fun archiveBufferBytes(context: Context): Int = snapshot(context).archiveBufferBytes

    fun deliveryBufferBytes(context: Context): Int = snapshot(context).deliveryBufferBytes

    fun start(context: Context, operationId: String, collectionName: String): OperationRecord {
        val profile = snapshot(context).profile
        return OperationRecord(
            operationId = operationId,
            collectionName = collectionName,
            profile = profile,
            startedAtMillis = System.currentTimeMillis(),
            stage = NovaAcExportManager.ExportStage.PLANNING,
            sourceBytes = 0L,
            archiveBytes = 0L,
            bytesPerSecond = 0L,
            etaMillis = 0L,
            includedCount = 0,
            skippedCount = 0,
        ).also { active[operationId] = it }
    }

    fun record(operationId: String, progress: NovaAcExportManager.ExportProgress): OperationRecord? {
        val prior = active[operationId] ?: return null
        val next = prior.copy(
            stage = progress.stage,
            sourceBytes = progress.sourceBytesProcessed,
            archiveBytes = progress.archiveBytesWritten,
            bytesPerSecond = progress.bytesPerSecond,
            etaMillis = progress.etaMillis,
            includedCount = progress.includedAudioTrackCount,
            skippedCount = progress.skippedAudioTrackCount,
            terminalMessage = progress.message,
            failureCode = progress.failureCode,
        )
        active[operationId] = next
        return next
    }

    fun finish(context: Context, operationId: String, message: String, failureCode: String? = null) {
        val prior = active.remove(operationId) ?: return
        val terminal = prior.copy(terminalMessage = message, failureCode = failureCode)
        prefs(context).edit().putString(KEY_LAST_SUMMARY, summary(terminal)).apply()
    }

    fun activeRecord(operationId: String): OperationRecord? = active[operationId]

    fun lastSummary(context: Context): String = prefs(context).getString(KEY_LAST_SUMMARY, "No NovaAc MrBean operation recorded yet.").orEmpty()

    private fun summary(record: OperationRecord): String = buildString {
        append(record.profile.label).append(" NovaAc · ")
        append(record.stage.name).append(" · ")
        append(record.sourceBytes).append(" source bytes · ")
        append(record.archiveBytes).append(" archive bytes · ")
        append(record.bytesPerSecond).append(" B/s")
        if (!record.failureCode.isNullOrBlank()) append(" · failure ").append(record.failureCode)
        if (record.terminalMessage.isNotBlank()) append(" · ").append(record.terminalMessage)
    }
}
