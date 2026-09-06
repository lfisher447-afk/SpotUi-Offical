// File: app/src/main/java/com/music/spotui/engine/archive/ArchiveRecovery.kt
package com.music.spotui.engine.archive

import android.content.Context
import android.net.Uri
import com.music.spotui.util.Result
import com.music.spotui.util.runSuspendCatching
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Lists and retries delivery of app-private staged archives after destination interruptions. */
@Singleton
class ArchiveRecovery @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Returns every recoverable staged archive. */
    fun list() = com.music.spotui.data.export.NovaAcExportManager.listRecoverableArchives(context)

    /** Retries delivery of [recoveryId] to a user-selected [destination]. */
    suspend fun retry(destination: Uri, recoveryId: String): Result<Unit> =
        runSuspendCatching("Unable to recover archive") {
            com.music.spotui.data.export.NovaAcExportManager.retryRecoveryDelivery(context, destination, recoveryId)
                .getOrElse { throw it }
            Unit
        }
}
