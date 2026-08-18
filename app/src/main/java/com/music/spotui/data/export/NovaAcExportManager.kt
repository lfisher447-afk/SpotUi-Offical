package com.music.spotui.data.export

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.preferences.OfflineCollectionsPref
import com.music.spotui.util.AppDiagnostics
import org.eclipse.Mrbean.client.MrbeanNovaAcController
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.IOException
import java.io.OutputStream
import javax.crypto.CipherOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.time.Instant
import java.util.Properties
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil

/**
 * NovaAc (v5) Portable Audio Archive Engine.
 *
 * Implements a variable-length quantity (VarInt/LEB128) binary instruction protocol (NovaBytecode v5)
 * with CRC32 frame checksums, hardware AES-256-GCM encryption, and direct audio cache bundling.
 */
object NovaAcExportManager {
    const val FILE_EXTENSION = "novaac"
    const val FORMAT_VERSION = 8

    private const val MAGIC = "NOVAAC5"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "spotui_novaac_manifest_key_v4"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val WEB_KDF_ITERATIONS = 210_000
    private const val ARCHIVE_BUFFER_BYTES = 256 * 1024
    private const val DELIVERY_BUFFER_BYTES = 512 * 1024
    private const val FRAME_PLAINTEXT_BYTES = 64 * 1024
    private const val FRAMED_PROTOCOL = "NovaBytecode-v8-FramedGcm"
    private const val LEGACY_FRAMED_PROTOCOL = "NovaBytecode-v7-FramedGcm"
    private const val LEGACY_PROTOCOL = "NovaBytecode-v5-PortableAudio"
    private const val STAGING_DIRECTORY = "novaac_staging"
    private const val RECOVERY_DIRECTORY = "novaac_recovery"

    // NovaBytecode v4 Opcode Table
    private const val OP_META_NAME: Byte       = 0x01
    private const val OP_META_SOURCE: Byte     = 0x02
    private const val OP_META_TIMESTAMP: Byte  = 0x03
    private const val OP_TRACK_BEGIN: Byte     = 0x10
    private const val OP_TRACK_STABLE_ID: Byte = 0x11
    private const val OP_TRACK_TITLE: Byte     = 0x12
    private const val OP_TRACK_ARTIST: Byte    = 0x13
    private const val OP_TRACK_ALBUM: Byte     = 0x14
    private const val OP_TRACK_ARTWORK: Byte   = 0x15
    private const val OP_TRACK_DURATION: Byte  = 0x16
    private const val OP_TRACK_EXPLICIT: Byte  = 0x17
    private const val OP_TRACK_ARTIST_IDS: Byte= 0x18
    private const val OP_AUDIO_PAYLOAD: Byte   = 0x20
    private const val OP_AUDIO_CONTAINER: Byte = 0x21
    private const val OP_AUDIO_FILE_NAME: Byte = 0x22
    private const val OP_AUDIO_SHA256: Byte    = 0x23
    private const val OP_TRACK_END: Byte       = 0x1F
    private const val OP_ARCHIVE_END: Byte     = -0x01

    data class NovaAcTrack(
        val stableId: String,
        val title: String,
        val artist: String,
        val album: String,
        val artworkUri: String,
        val durationMs: Int,
        val explicit: Boolean,
        val artistIds: String,
        val audioBytes: ByteArray? = null,
        val audioContainer: String = "m4a",
        val audioFileName: String = "",
        val audioSha256: String = "",
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as NovaAcTrack
            return stableId == other.stableId
        }

        override fun hashCode(): Int = stableId.hashCode()
    }

    data class PreparedExport(
        val fileName: String,
        val bytes: ByteArray,
        val trackCount: Int,
        val header: Header,
    )

    /** Controls whether an archive carries no audio, every available local payload, or only proceeds when every selected track is locally available. */
    enum class AudioPayloadMode {
        METADATA_ONLY,
        INCLUDE_AVAILABLE_LOCAL_AUDIO,
        REQUIRE_ALL_LOCAL_AUDIO,
    }

    /** Device-secure archives use Android Keystore; web-passphrase archives are explicitly portable and decryptable only with the chosen passphrase. */
    enum class ArchiveSecurityMode {
        DEVICE_SECURE,
        WEB_PASSPHRASE,
    }

    data class LocalAudioCoverage(
        val songId: Int,
        val title: String,
        val available: Boolean,
        val bytes: Long,
        val path: String? = null,
    )

    data class FullAudioArchivePlan(
        val collectionName: String,
        val sourceType: String,
        val songs: List<SongsModel>,
        val mode: AudioPayloadMode,
        val securityMode: ArchiveSecurityMode,
        val browserPassphrase: String?,
        val coverage: List<LocalAudioCoverage>,
        val estimatedArchiveBytes: Long,
        val fileName: String,
    ) {
        val availableTrackCount: Int get() = coverage.count { it.available }
        val missingTrackCount: Int get() = coverage.count { !it.available }
        val localAudioBytes: Long get() = coverage.sumOf { it.bytes }
        val canExport: Boolean get() = songs.isNotEmpty() &&
            (mode != AudioPayloadMode.REQUIRE_ALL_LOCAL_AUDIO || missingTrackCount == 0)
    }

    data class FullAudioArchiveResult(
        val trackCount: Int,
        val includedAudioTrackCount: Int,
        val skippedAudioTrackCount: Int,
        val archiveBytes: Long,
        val estimatedArchiveBytes: Long,
        val durationMillis: Long,
        val header: Header,
        val archiveSha256: String,
        val stagedArchiveBytes: Long,
        val deliveryVerified: Boolean,
        val recoveryId: String? = null,
    )

    /** A persisted app-private staged archive that can be delivered again after a destination interruption. */
    data class RecoverableArchive(
        val id: String,
        val collectionName: String,
        val stagedFile: File,
        val archiveBytes: Long,
        val archiveSha256: String,
        val createdAtMillis: Long,
        val lastFailure: String = "",
    )

    data class RecoveryDeliveryResult(
        val recoveryId: String,
        val archiveBytes: Long,
        val deliveryVerified: Boolean,
        val recoveryRetained: Boolean,
    )

    enum class ExportFailureCode {
        STAGING_STORAGE_INSUFFICIENT,
        STAGING_WRITE_FAILED,
        SOURCE_CHANGED,
        DESTINATION_OPEN_FAILED,
        DESTINATION_WRITE_INTERRUPTED,
        DESTINATION_VERIFICATION_FAILED,
        CANCELLED,
        UNKNOWN,
    }

    private class NovaAcExportException(
        val code: ExportFailureCode,
        message: String,
        cause: Throwable? = null,
    ) : IOException(message, cause)

    enum class ExportStage {
        PLANNING,
        OPENING_DESTINATION,
        WRITING_METADATA,
        HASHING_PAYLOAD,
        COPYING_PAYLOAD,
        CHECKING_STAGING,
        BUILDING_STAGE,
        VERIFYING_STAGE,
        DELIVERING_DESTINATION,
        VERIFYING_DESTINATION,
        RECOVERY_READY,
        FINALIZING,
        COMPLETED,
        FAILED,
    }

    /** A bounded-memory export telemetry record suitable for direct Compose rendering. */
    data class ExportProgress(
        val stage: ExportStage,
        val currentTrackIndex: Int,
        val totalTrackCount: Int,
        val currentTrackTitle: String,
        val currentTrackBytes: Long,
        val currentTrackTotalBytes: Long,
        val sourceBytesProcessed: Long,
        val sourceBytesPlanned: Long,
        val archiveBytesWritten: Long,
        val includedAudioTrackCount: Int,
        val skippedAudioTrackCount: Int,
        val startedAtMillis: Long,
        val message: String = "",
        val failureCode: String? = null,
        val recoveryId: String? = null,
        val bytesPerSecond: Long = 0L,
        val etaMillis: Long = 0L,
    ) {
        val fraction: Float
            get() = when {
                sourceBytesPlanned > 0L -> (sourceBytesProcessed.toDouble() / sourceBytesPlanned).coerceIn(0.0, 1.0).toFloat()
                totalTrackCount > 0 -> (currentTrackIndex.toDouble() / totalTrackCount).coerceIn(0.0, 1.0).toFloat()
                else -> 0f
            }
        val elapsedMillis: Long get() = (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0L)
    }

    data class Header(
        val formatVersion: Int,
        val appVersion: String,
        val createdAt: String,
        val collectionName: String,
        val sourceType: String,
        val trackCount: Int,
        val payloadBytes: Long,
        val includesAudioPayload: Boolean = false,
        val requestedAudioMode: String = AudioPayloadMode.METADATA_ONLY.name,
        val includedAudioTrackCount: Int = 0,
        val missingAudioTrackCount: Int = 0,
        val localAudioBytes: Long = 0L,
        val securityMode: String = ArchiveSecurityMode.DEVICE_SECURE.name,
        val kdfSaltBase64: String = "",
        val kdfIterations: Int = 0,
        val protocol: String = LEGACY_PROTOCOL,
    ) {
        val compatibilityWarning: String?
            get() = when {
                formatVersion > FORMAT_VERSION ->
                    "This NovaAc file uses format v$formatVersion. Update SpotUI before importing it."
                formatVersion < 7 ->
                    "This NovaAc file uses an older format (v$formatVersion). Re-exporting is recommended."
                else -> null
            }
    }

    /** Scans selected tracks and produces an exact local-audio coverage and size estimate before any archive is written. */
    fun planFullAudioArchive(
        context: Context,
        collectionName: String,
        sourceType: String,
        selectedSongs: Collection<SongsModel>,
        mode: AudioPayloadMode,
        securityMode: ArchiveSecurityMode = ArchiveSecurityMode.DEVICE_SECURE,
        browserPassphrase: String? = null,
    ): FullAudioArchivePlan {
        require(selectedSongs.isNotEmpty()) { "Select at least one track to create a NovaAc archive." }
        require(securityMode != ArchiveSecurityMode.WEB_PASSPHRASE || !browserPassphrase.isNullOrBlank()) {
            "Enter a passphrase for a browser-compatible archive."
        }
        val songs = selectedSongs.toList()
        val coverage = songs.map { song ->
            val path = localAudioPath(context, song)
            val file = path?.let(::File)
            // A NovaAc archive is an encrypted stream. Do not reject a valid local payload just
            // because it exceeds the old in-memory ByteArray / Int ceiling.
            val available = file?.isFile == true && file.length() > 0L
            LocalAudioCoverage(
                songId = song.id,
                title = song.title,
                available = available,
                bytes = if (available) file!!.length() else 0L,
                path = if (available) file!!.absolutePath else null,
            )
        }
        val audioBytes = coverage.sumOf { it.bytes }
        // Binary metadata, compression framing, AES-GCM authentication tag, header, and filename overhead.
        val estimated = (audioBytes + (songs.size * 1_024L) + 16_384L).coerceAtLeast(16_384L)
        val safeTitle = collectionName.ifBlank { "Untitled collection" }
        val estimatedMegabytes = ceil(estimated / (1024.0 * 1024.0)).toLong().coerceAtLeast(1L)
        return FullAudioArchivePlan(
            collectionName = safeTitle,
            sourceType = sourceType,
            songs = songs,
            mode = mode,
            securityMode = securityMode,
            browserPassphrase = browserPassphrase,
            coverage = coverage,
            estimatedArchiveBytes = estimated,
            fileName = "${safeFileName(safeTitle)}_${songs.size}tracks_${estimatedMegabytes}mb.$FILE_EXTENSION",
        )
    }

    /**
     * Builds one complete archive in private staging storage before it touches the document provider.
     * This deliberately separates sustained encryption/compression from potentially fragile SAF writes.
     */
    fun writeFullAudioArchive(
        context: Context,
        destination: Uri,
        plan: FullAudioArchivePlan,
        onProgress: (ExportProgress) -> Unit = {},
        shouldCancel: () -> Boolean = { false },
    ): Result<FullAudioArchiveResult> {
        val startedAt = System.currentTimeMillis()
        val recoveryId = UUID.randomUUID().toString()
        MrbeanNovaAcController.start(context, recoveryId, plan.collectionName)
        var lastProgress: ExportProgress? = null
        var lastSpeedSampleAt = startedAt
        var lastSpeedSampleBytes = 0L
        var bytesPerSecond = 0L
        fun publish(
            stage: ExportStage,
            index: Int = 0,
            title: String = "",
            currentBytes: Long = 0L,
            currentTotal: Long = 0L,
            sourceProcessed: Long = 0L,
            archiveWritten: Long = 0L,
            included: Int = 0,
            skipped: Int = 0,
            message: String = "",
            failureCode: String? = null,
            recovery: String? = null,
        ) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastSpeedSampleAt
            if (elapsed >= 500L) {
                val bytesDelta = (sourceProcessed - lastSpeedSampleBytes).coerceAtLeast(0L)
                bytesPerSecond = if (elapsed > 0L) (bytesDelta * 1000L) / elapsed else 0L
                lastSpeedSampleAt = now
                lastSpeedSampleBytes = sourceProcessed
            }
            val eta = if (bytesPerSecond > 0L && plan.localAudioBytes > sourceProcessed) {
                ((plan.localAudioBytes - sourceProcessed) * 1000L) / bytesPerSecond
            } else 0L
            val progress = ExportProgress(
                stage = stage,
                currentTrackIndex = index,
                totalTrackCount = plan.songs.size,
                currentTrackTitle = title,
                currentTrackBytes = currentBytes,
                currentTrackTotalBytes = currentTotal,
                sourceBytesProcessed = sourceProcessed,
                sourceBytesPlanned = plan.localAudioBytes,
                archiveBytesWritten = archiveWritten,
                includedAudioTrackCount = included,
                skippedAudioTrackCount = skipped,
                startedAtMillis = startedAt,
                message = message,
                failureCode = failureCode,
                recoveryId = recovery,
                bytesPerSecond = bytesPerSecond,
                etaMillis = eta,
            )
            lastProgress = progress
            MrbeanNovaAcController.record(recoveryId, progress)
            onProgress(progress)
        }

        return runCatching {
            require(plan.canExport) {
                "${plan.missingTrackCount} track(s) are not downloaded locally. Download all tracks or choose Include available local audio."
            }
            publish(ExportStage.CHECKING_STAGING, message = "Checking private staging storage")
            ensureStagingCapacity(context, plan)
            val stage = buildStageArchive(
                context = context,
                plan = plan,
                recoveryId = recoveryId,
                publish = ::publish,
                shouldCancel = shouldCancel,
            )
            publish(
                stage = ExportStage.VERIFYING_STAGE,
                index = plan.songs.size,
                sourceProcessed = stage.counters.sourceBytesCopied,
                archiveWritten = stage.archiveBytes,
                included = stage.counters.includedAudioTrackCount,
                skipped = stage.counters.skippedAudioTrackCount,
                message = "Authenticating every encrypted archive frame",
            )
            verifyFramedStageArchive(context, stage.file, plan)
            val stageDigest = sha256Of(stage.file)
            val recovery = writeRecoveryRecord(
                context = context,
                id = recoveryId,
                collectionName = plan.collectionName,
                stagedFile = stage.file,
                archiveBytes = stage.archiveBytes,
                archiveSha256 = stageDigest,
                lastFailure = "",
            )
            publish(
                stage = ExportStage.DELIVERING_DESTINATION,
                index = plan.songs.size,
                sourceProcessed = stage.counters.sourceBytesCopied,
                archiveWritten = 0L,
                included = stage.counters.includedAudioTrackCount,
                skipped = stage.counters.skippedAudioTrackCount,
                message = "Delivering verified archive to selected destination",
                recovery = recovery.id,
            )
            val delivery = deliverStageArchive(
                context = context,
                destination = destination,
                stage = recovery,
                publish = ::publish,
                shouldCancel = shouldCancel,
            )
            val retainRecovery = MrbeanNovaAcController.snapshot(context).keepRecoveryCopies
            if (delivery.verified && !retainRecovery) {
                recovery.stagedFile.delete()
                recoveryManifestFile(context, recovery.id).delete()
            }
            publish(
                stage = ExportStage.COMPLETED,
                index = plan.songs.size,
                sourceProcessed = stage.counters.sourceBytesCopied,
                archiveWritten = delivery.bytesCopied,
                included = stage.counters.includedAudioTrackCount,
                skipped = stage.counters.skippedAudioTrackCount,
                message = when {
                    delivery.verified && retainRecovery -> "Archive delivered and verified; MrBean recovery copy retained"
                    delivery.verified -> "Archive delivered and readback-verified"
                    else -> "Archive delivered; private recovery copy retained"
                },
                recovery = if (delivery.verified && !retainRecovery) null else recovery.id,
            )
            MrbeanNovaAcController.finish(context, recoveryId, "NovaAc archive completed")
            FullAudioArchiveResult(
                trackCount = plan.songs.size,
                includedAudioTrackCount = stage.counters.includedAudioTrackCount,
                skippedAudioTrackCount = stage.counters.skippedAudioTrackCount,
                archiveBytes = delivery.bytesCopied,
                estimatedArchiveBytes = plan.estimatedArchiveBytes,
                durationMillis = System.currentTimeMillis() - startedAt,
                header = stage.header,
                archiveSha256 = stageDigest,
                stagedArchiveBytes = stage.archiveBytes,
                deliveryVerified = delivery.verified,
                recoveryId = if (delivery.verified && !retainRecovery) null else recovery.id,
            )
        }.onFailure { error ->
            val code = (error as? NovaAcExportException)?.code ?: when (error) {
                is java.util.concurrent.CancellationException -> ExportFailureCode.CANCELLED
                else -> ExportFailureCode.UNKNOWN
            }
            val previous = lastProgress
            AppDiagnostics.error("NovaAc", "Large archive export failed [${code.name}]", error)
            publish(
                stage = ExportStage.FAILED,
                index = previous?.currentTrackIndex ?: 0,
                title = previous?.currentTrackTitle.orEmpty(),
                currentBytes = previous?.currentTrackBytes ?: 0L,
                currentTotal = previous?.currentTrackTotalBytes ?: 0L,
                sourceProcessed = previous?.sourceBytesProcessed ?: 0L,
                archiveWritten = previous?.archiveBytesWritten ?: 0L,
                included = previous?.includedAudioTrackCount ?: 0,
                skipped = previous?.skippedAudioTrackCount ?: 0,
                message = error.message ?: "Archive export failed",
                failureCode = code.name,
                recovery = recoveryId,
            )
            MrbeanNovaAcController.finish(context, recoveryId, error.message ?: "NovaAc archive failed", code.name)
        }
    }

    private data class StagedArchive(
        val file: File,
        val header: Header,
        val counters: StreamingCounters,
        val archiveBytes: Long,
    )

    private data class DeliveryOutcome(
        val bytesCopied: Long,
        val verified: Boolean,
    )

    private fun ensureStagingCapacity(context: Context, plan: FullAudioArchivePlan) {
        val stagingRoot = stagingRoot(context).apply { mkdirs() }
        if (!stagingRoot.isDirectory) {
            throw NovaAcExportException(ExportFailureCode.STAGING_STORAGE_INSUFFICIENT, "Spotui cannot create private archive staging storage")
        }
        val available = StatFs(stagingRoot.absolutePath).availableBytes
        // This is diagnostic only. Do not create an arbitrary application-side size rejection:
        // output can compress differently from the source estimate and Android owns the actual
        // free-space/filesystem decision. A real ENOSPC during staging is reported precisely.
        AppDiagnostics.info(
            "NovaAc",
            "Staging capacity diagnostic: ${formatBytes(available)} free; source estimate ${formatBytes(plan.estimatedArchiveBytes)}",
        )
    }

    private fun buildStageArchive(
        context: Context,
        plan: FullAudioArchivePlan,
        recoveryId: String,
        publish: (ExportStage, Int, String, Long, Long, Long, Long, Int, Int, String, String?, String?) -> Unit,
        shouldCancel: () -> Boolean,
    ): StagedArchive {
        val stagingRoot = stagingRoot(context).apply { mkdirs() }
        val partialFile = File(stagingRoot, "$recoveryId.partial")
        val completeFile = File(stagingRoot, "$recoveryId.$FILE_EXTENSION")
        partialFile.delete()
        completeFile.delete()
        val createdAt = Instant.now().toString()
        val includeAudio = plan.mode != AudioPayloadMode.METADATA_ONLY
        val salt = if (plan.securityMode == ArchiveSecurityMode.WEB_PASSPHRASE) ByteArray(16).also(SecureRandom()::nextBytes) else ByteArray(0)
        val encryptionKey = if (plan.securityMode == ArchiveSecurityMode.WEB_PASSPHRASE) {
            derivePassphraseKey(plan.browserPassphrase.orEmpty(), salt)
        } else {
            getOrCreateKey()
        }
        val frameNonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val header = Header(
            formatVersion = FORMAT_VERSION,
            appVersion = appVersion(context),
            createdAt = createdAt,
            collectionName = plan.collectionName,
            sourceType = plan.sourceType,
            trackCount = plan.songs.size,
            payloadBytes = -1L,
            includesAudioPayload = includeAudio,
            requestedAudioMode = plan.mode.name,
            includedAudioTrackCount = plan.availableTrackCount,
            missingAudioTrackCount = plan.missingTrackCount,
            localAudioBytes = plan.localAudioBytes,
            securityMode = plan.securityMode.name,
            kdfSaltBase64 = if (salt.isNotEmpty()) Base64.encodeToString(salt, Base64.NO_WRAP) else "",
            kdfIterations = if (salt.isNotEmpty()) WEB_KDF_ITERATIONS else 0,
            protocol = FRAMED_PROTOCOL,
        )
        publish(ExportStage.BUILDING_STAGE, 0, "Preparing fixed staged archive", 0L, 0L, 0L, 0L, 0, 0, "Creating private archive stage", null, recoveryId)
        val counters = try {
            FileOutputStream(partialFile).use { fileOutput ->
                val counters = writeFramedStreamingContainer(
                    rawOutput = CloseShieldOutputStream(fileOutput),
                    header = header,
                    plan = plan,
                    encryptionKey = encryptionKey,
                    baseNonce = frameNonce,
                    payloadBufferBytes = MrbeanNovaAcController.archiveBufferBytes(context),
                    publish = { stage, index, title, currentBytes, currentTotal, sourceProcessed, archiveWritten, included, skipped, message ->
                        publish(stage, index, title, currentBytes, currentTotal, sourceProcessed, archiveWritten, included, skipped, message, null, recoveryId)
                    },
                    shouldCancel = shouldCancel,
                )
                fileOutput.fd.sync()
                counters
            }
        } catch (error: Throwable) {
            partialFile.delete()
            throw when (error) {
                is NovaAcExportException -> error
                is java.util.concurrent.CancellationException -> error
                else -> NovaAcExportException(ExportFailureCode.STAGING_WRITE_FAILED, "Unable to build private staged archive: ${error.message ?: error.javaClass.simpleName}", error)
            }
        }
        val bytes = partialFile.length()
        if (bytes <= 0L || bytes != counters.archiveBytesWritten) {
            partialFile.delete()
            throw NovaAcExportException(ExportFailureCode.STAGING_WRITE_FAILED, "Staged archive byte count did not finalize correctly")
        }
        if (!partialFile.renameTo(completeFile)) {
            partialFile.delete()
            throw NovaAcExportException(ExportFailureCode.STAGING_WRITE_FAILED, "Unable to finalize staged archive file")
        }
        return StagedArchive(completeFile, header, counters, bytes)
    }

    private fun verifyFramedStageArchive(
        context: Context,
        stagedFile: File,
        plan: FullAudioArchivePlan,
    ) {
        try {
            DataInputStream(BufferedInputStream(FileInputStream(stagedFile), MrbeanNovaAcController.archiveBufferBytes(context))).use { input ->
                val header = readHeader(input)
                require(header.protocol == FRAMED_PROTOCOL) { "Staged archive protocol was not finalized as framed NovaAc" }
                val nonceSize = input.readUnsignedByte()
                require(nonceSize == 12) { "Invalid framed archive base nonce" }
                val baseNonce = ByteArray(nonceSize)
                input.readFully(baseNonce)
                val security = runCatching { ArchiveSecurityMode.valueOf(header.securityMode) }
                    .getOrDefault(ArchiveSecurityMode.DEVICE_SECURE)
                val key = if (security == ArchiveSecurityMode.WEB_PASSPHRASE) {
                    val salt = Base64.decode(header.kdfSaltBase64, Base64.DEFAULT)
                    derivePassphraseKey(plan.browserPassphrase.orEmpty(), salt, header.kdfIterations.coerceIn(100_000, 500_000))
                } else {
                    getOrCreateKey()
                }
                var expectedIndex = 0L
                val frameAad = ByteArray(8)
                val aadHeader = if (header.protocol == FRAMED_PROTOCOL) header.toMetaString().encodeToByteArray() else ByteArray(0)
                while (true) {
                    val frameIndex = input.readLong()
                    val plainLength = input.readInt()
                    val cipherLength = input.readInt()
                    if (frameIndex == -1L) {
                        require(plainLength == 0 && cipherLength == 0) { "Malformed framed archive terminator" }
                        break
                    }
                    require(frameIndex == expectedIndex) { "Framed archive sequence interrupted at frame $expectedIndex" }
                    require(plainLength in 1..FRAME_PLAINTEXT_BYTES) { "Invalid framed archive plaintext length" }
                    require(cipherLength in (plainLength + 16)..(plainLength + 32)) { "Invalid framed archive ciphertext length" }
                    val encrypted = ByteArray(cipherLength)
                    input.readFully(encrypted)
                    val cipher = Cipher.getInstance(AES_TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, frameNonce(baseNonce, frameIndex)))
                    if (aadHeader.isNotEmpty()) {
                        cipher.updateAAD(aadHeader)
                        writeLongBigEndian(frameAad, frameIndex)
                        cipher.updateAAD(frameAad)
                    }
                    val plaintext = cipher.doFinal(encrypted)
                    require(plaintext.size == plainLength) { "Framed archive plaintext length mismatch at frame $frameIndex" }
                    expectedIndex++
                }
                require(expectedIndex > 0L) { "Framed archive contained no encrypted body frames" }
            }
        } catch (error: Throwable) {
            throw when (error) {
                is NovaAcExportException -> error
                else -> NovaAcExportException(
                    ExportFailureCode.STAGING_WRITE_FAILED,
                    "Staged archive frame verification failed: ${error.message ?: error.javaClass.simpleName}",
                    error,
                )
            }
        }
    }

    private fun frameNonce(baseNonce: ByteArray, index: Long): ByteArray {
        val nonce = baseNonce.copyOf()
        for (offset in 0 until 8) {
            val position = nonce.lastIndex - offset
            val indexByte = ((index ushr (offset * 8)) and 0xFFL).toInt()
            nonce[position] = (nonce[position].toInt() xor indexByte).toByte()
        }
        return nonce
    }

    private fun writeLongBigEndian(target: ByteArray, value: Long) {
        require(target.size >= 8) { "Frame AAD buffer must be at least eight bytes" }
        for (offset in 0 until 8) {
            target[offset] = (value ushr ((7 - offset) * 8)).toByte()
        }
    }

    private fun deliverStageArchive(
        context: Context,
        destination: Uri,
        stage: RecoverableArchive,
        publish: (ExportStage, Int, String, Long, Long, Long, Long, Int, Int, String, String?, String?) -> Unit,
        shouldCancel: () -> Boolean,
    ): DeliveryOutcome {
        val copied = try {
            context.contentResolver.openOutputStream(destination, "w")?.use { destinationOutput ->
                copyFileToOutput(
                    source = stage.stagedFile,
                    output = destinationOutput,
                    buffer = ByteArray(MrbeanNovaAcController.deliveryBufferBytes(context)),
                    shouldCancel = shouldCancel,
                ) { bytes ->
                    publish(ExportStage.DELIVERING_DESTINATION, 0, "Delivering archive", bytes, stage.archiveBytes, 0L, bytes, 0, 0, "Writing staged archive to destination", null, stage.id)
                }
            } ?: throw NovaAcExportException(ExportFailureCode.DESTINATION_OPEN_FAILED, "Unable to open the selected archive destination")
        } catch (error: Throwable) {
            writeRecoveryRecord(context, stage.id, stage.collectionName, stage.stagedFile, stage.archiveBytes, stage.archiveSha256, error.message ?: error.javaClass.simpleName)
            throw when (error) {
                is NovaAcExportException -> error
                is java.util.concurrent.CancellationException -> error
                else -> NovaAcExportException(ExportFailureCode.DESTINATION_WRITE_INTERRUPTED, "Destination delivery stopped after ${formatBytes(stage.archiveBytes)} staged: ${error.message ?: error.javaClass.simpleName}", error)
            }
        }
        if (copied != stage.archiveBytes) {
            writeRecoveryRecord(context, stage.id, stage.collectionName, stage.stagedFile, stage.archiveBytes, stage.archiveSha256, "Byte count mismatch: copied=$copied")
            throw NovaAcExportException(ExportFailureCode.DESTINATION_WRITE_INTERRUPTED, "Destination received ${formatBytes(copied)} but staged archive requires ${formatBytes(stage.archiveBytes)}")
        }
        publish(ExportStage.VERIFYING_DESTINATION, 0, "Verifying delivered archive", copied, stage.archiveBytes, 0L, copied, 0, 0, "Checking destination readback", null, stage.id)
        val readback = runCatching {
            context.contentResolver.openInputStream(destination)?.use(::sha256Of)
        }.getOrNull()
        if (readback != null && !readback.equals(stage.archiveSha256, ignoreCase = true)) {
            writeRecoveryRecord(context, stage.id, stage.collectionName, stage.stagedFile, stage.archiveBytes, stage.archiveSha256, "Destination digest mismatch")
            throw NovaAcExportException(ExportFailureCode.DESTINATION_VERIFICATION_FAILED, "Destination verification mismatch. The complete archive is retained for retry.")
        }
        return DeliveryOutcome(bytesCopied = copied, verified = readback != null)
    }

    private fun copyFileToOutput(
        source: File,
        output: OutputStream,
        buffer: ByteArray,
        shouldCancel: () -> Boolean,
        onProgress: (Long) -> Unit,
    ): Long {
        var copied = 0L
        FileInputStream(source).buffered(buffer.size).use { input ->
            while (true) {
                if (shouldCancel()) throw java.util.concurrent.CancellationException("NovaAc delivery cancelled")
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                copied += read.toLong()
                onProgress(copied)
            }
            output.flush()
        }
        return copied
    }

    /**
     * Prefer app-specific external storage for large staged archives. It does not need broad storage
     * permission, generally shares the device's larger media volume, and is removed with app data.
     * Internal files remain the compatibility fallback for devices without an external app volume.
     */
    private fun stagingRoot(context: Context): File =
        (context.getExternalFilesDir(STAGING_DIRECTORY) ?: File(context.filesDir, STAGING_DIRECTORY)).apply { mkdirs() }

    private fun recoveryRoot(context: Context): File =
        (context.getExternalFilesDir(RECOVERY_DIRECTORY) ?: File(context.filesDir, RECOVERY_DIRECTORY)).apply { mkdirs() }
    private fun recoveryManifestFile(context: Context, id: String): File = File(recoveryRoot(context), "$id.properties")

    private fun writeRecoveryRecord(
        context: Context,
        id: String,
        collectionName: String,
        stagedFile: File,
        archiveBytes: Long,
        archiveSha256: String,
        lastFailure: String,
    ): RecoverableArchive {
        val manifest = recoveryManifestFile(context, id)
        Properties().apply {
            setProperty("format", "novaac-staged-v1")
            setProperty("id", id)
            setProperty("collectionName", collectionName)
            setProperty("stagedPath", stagedFile.absolutePath)
            setProperty("archiveBytes", archiveBytes.toString())
            setProperty("archiveSha256", archiveSha256)
            setProperty("createdAtMillis", System.currentTimeMillis().toString())
            setProperty("lastFailure", lastFailure)
            FileOutputStream(manifest).use { store(it, "Spotui NovaAc recoverable staged archive") }
        }
        return RecoverableArchive(id, collectionName, stagedFile, archiveBytes, archiveSha256, System.currentTimeMillis(), lastFailure)
    }

    /** Lists only complete staged archives whose manifest and data file still agree. */
    fun listRecoverableArchives(context: Context): List<RecoverableArchive> = recoveryRoot(context)
        .listFiles { file -> file.extension == "properties" }
        .orEmpty()
        .mapNotNull { manifest ->
            runCatching {
                val props = Properties().apply { FileInputStream(manifest).use(::load) }
                val id = props.getProperty("id").orEmpty()
                val staged = File(props.getProperty("stagedPath").orEmpty())
                val bytes = props.getProperty("archiveBytes")?.toLongOrNull() ?: -1L
                require(id.isNotBlank() && staged.isFile && staged.length() == bytes) { "Staged recovery record is incomplete" }
                RecoverableArchive(
                    id = id,
                    collectionName = props.getProperty("collectionName").orEmpty(),
                    stagedFile = staged,
                    archiveBytes = bytes,
                    archiveSha256 = props.getProperty("archiveSha256").orEmpty(),
                    createdAtMillis = props.getProperty("createdAtMillis")?.toLongOrNull() ?: manifest.lastModified(),
                    lastFailure = props.getProperty("lastFailure").orEmpty(),
                )
            }.getOrNull()
        }
        .sortedByDescending { it.createdAtMillis }

    /** Verifies retained bytes before retrying delivery; rebuilding the playlist archive is not required. */
    fun retryRecoveryDelivery(context: Context, destination: Uri, recoveryId: String): Result<RecoveryDeliveryResult> = runCatching {
        val archive = listRecoverableArchives(context).firstOrNull { it.id == recoveryId }
            ?: error("No completed NovaAc recovery archive was found for this ID")
        require(sha256Of(archive.stagedFile).equals(archive.archiveSha256, ignoreCase = true)) {
            "The retained NovaAc recovery archive did not pass its integrity check"
        }
        val delivery = deliverStageArchive(
            context = context,
            destination = destination,
            stage = archive,
            publish = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
            shouldCancel = { false },
        )
        if (delivery.verified && !MrbeanNovaAcController.snapshot(context).keepRecoveryCopies) {
            archive.stagedFile.delete()
            recoveryManifestFile(context, archive.id).delete()
        }
        RecoveryDeliveryResult(
            recoveryId = archive.id,
            archiveBytes = delivery.bytesCopied,
            deliveryVerified = delivery.verified,
            recoveryRetained = !delivery.verified || MrbeanNovaAcController.snapshot(context).keepRecoveryCopies,
        )
    }

    fun purgeRecoveryArchive(context: Context, recoveryId: String): Boolean {
        val archive = listRecoverableArchives(context).firstOrNull { it.id == recoveryId } ?: return false
        val stageDeleted = !archive.stagedFile.exists() || archive.stagedFile.delete()
        val manifestDeleted = !recoveryManifestFile(context, recoveryId).exists() || recoveryManifestFile(context, recoveryId).delete()
        return stageDeleted && manifestDeleted
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    data class ImportResult(
        val collectionName: String,
        val sourceType: String,
        val songs: List<SongsModel>,
        val header: Header,
        val importedAudioFileCount: Int
    )

    /**
     * Compiles a collection into custom NovaBytecode v5 and encrypts the resulting payload.
     */
    fun prepareExport(
        context: Context,
        collectionName: String,
        sourceType: String,
        selectedSongs: Collection<SongsModel>,
        includeAudioPayload: Boolean = true
    ): PreparedExport {
        require(selectedSongs.isNotEmpty()) { "Select at least one track to create a NovaAc archive." }
        require(!includeAudioPayload) {
            "Full-audio NovaAc archives must use the streaming export path. Open Advanced NovaAc Export and choose a payload profile."
        }

        val tracks = selectedSongs.map { song ->
            val localFile = localAudioPath(context, song)?.let(::File)
            val audioBytes = if (includeAudioPayload) extractAudioBytes(context, song) else null
            toNovaTrack(song, audioBytes, localFile)
        }

        val compiledBytecode = NovaCompiler.compile(
            collectionName = collectionName.ifBlank { "Untitled collection" },
            sourceType = sourceType,
            createdAt = Instant.now().toString(),
            tracks = tracks
        )

        val compressedBytecode = compressBytes(compiledBytecode)

        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(compressedBytecode)

        val header = Header(
            formatVersion = FORMAT_VERSION,
            appVersion = appVersion(context),
            createdAt = Instant.now().toString(),
            collectionName = collectionName.ifBlank { "Untitled collection" },
            sourceType = sourceType,
            trackCount = tracks.size,
            payloadBytes = ciphertext.size.toLong(),
            includesAudioPayload = includeAudioPayload
        )

        val serialized = serialize(header, cipher.iv, ciphertext)
        val estimatedMegabytes = ceil(serialized.size / (1024.0 * 1024.0)).toInt().coerceAtLeast(1)
        val safeCount = tracks.size.coerceAtLeast(1)
        val name = "cache_${safeCount}_${estimatedMegabytes}mb.$FILE_EXTENSION"

        AppDiagnostics.info("NovaAc", "Compiled NovaBytecode v4 archive ($safeCount tracks, Audio: $includeAudioPayload)")
        return PreparedExport(name, serialized, tracks.size, header)
    }

    /**
     * Writes prepared NovaAc archive to the specified output URI.
     */
    fun writePreparedExport(context: Context, destination: Uri, export: PreparedExport): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(destination, "w")?.use { output ->
            output.write(export.bytes)
            output.flush()
        } ?: error("Unable to open export destination stream")
        AppDiagnostics.info("NovaAc", "Wrote ${export.trackCount}-track NovaBytecode archive (${export.bytes.size} bytes)")
    }.onFailure {
        AppDiagnostics.error("NovaAc", "Export failed", it)
    }

    /**
     * Inspects NovaAc binary header without decrypting the payload block.
     */
    fun inspectHeader(context: Context, source: Uri): Result<Header> = runCatching {
        context.contentResolver.openInputStream(source)?.use { input ->
            DataInputStream(BufferedInputStream(input)).use(::readHeader)
        } ?: error("Unable to open NovaAc file stream")
    }.onFailure {
        AppDiagnostics.warning("NovaAc", "Header inspection failed", it)
    }

    /**
     * Decrypts and interprets NovaBytecode container, restoring metadata and audio files.
     */
    fun importExport(
        context: Context,
        source: Uri,
        saveToOfflineLibrary: Boolean = true,
        browserPassphrase: String? = null,
    ): Result<ImportResult> = runCatching {
        val inspected = inspectHeader(context, source).getOrThrow()
        if (inspected.protocol == FRAMED_PROTOCOL || inspected.protocol == LEGACY_FRAMED_PROTOCOL) {
            return@runCatching importFramedArchive(context, source, saveToOfflineLibrary, browserPassphrase)
        }
        val (header, nonce, ciphertext) = context.contentResolver.openInputStream(source)?.use { input ->
            DataInputStream(BufferedInputStream(input)).use { dataInput ->
                val hdr = readHeader(dataInput)
                val nonceSize = dataInput.readByte().toInt() and 0xFF
                val nonceBytes = ByteArray(nonceSize)
                dataInput.readFully(nonceBytes)
                val cipherBytes = if (hdr.payloadBytes in 0..Int.MAX_VALUE.toLong()) {
                    ByteArray(hdr.payloadBytes.toInt()).also(dataInput::readFully)
                } else {
                    dataInput.readBytes()
                }
                Triple(hdr, nonceBytes, cipherBytes)
            }
        } ?: error("Failed to read input NovaAc file")

        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_BITS, nonce)
        val security = runCatching { ArchiveSecurityMode.valueOf(header.securityMode) }
            .getOrDefault(ArchiveSecurityMode.DEVICE_SECURE)
        val decryptionKey = if (security == ArchiveSecurityMode.WEB_PASSPHRASE) {
            require(!browserPassphrase.isNullOrBlank()) { "This archive needs its browser passphrase before it can be imported." }
            val salt = Base64.decode(header.kdfSaltBase64, Base64.DEFAULT)
            derivePassphraseKey(browserPassphrase, salt, header.kdfIterations.coerceIn(100_000, 500_000))
        } else {
            getOrCreateKey()
        }
        cipher.init(Cipher.DECRYPT_MODE, decryptionKey, spec)

        val compressedBytes = cipher.doFinal(ciphertext)
        val bytecode = decompressBytes(compressedBytes)

        val interpretation = NovaInterpreter.interpret(bytecode)

        val restoredSongs = mutableListOf<SongsModel>()
        var importedAudioCount = 0

        val downloadsDir = File(context.filesDir, "downloads").apply { mkdirs() }

        for (track in interpretation.tracks) {
            var songUrl = "spotify:track:${track.stableId.removePrefix("local:")}"
            val songId = track.stableId.hashCode() and 0x7fffffff

            if (track.audioBytes != null && track.audioBytes.isNotEmpty()) {
                val extension = safeAudioExtension(track.audioContainer)
                val destFile = File(downloadsDir, "$songId.$extension")
                if (track.audioSha256.isNotBlank() && sha256Of(track.audioBytes) != track.audioSha256) {
                    error("NovaAc payload integrity mismatch for ${track.title}")
                }
                destFile.writeBytes(track.audioBytes)
                songUrl = Uri.fromFile(destFile).toString()
                com.music.spotui.data.preferences.addDownload(
                    context,
                    SongsModel(id = songId, title = track.title, album = track.album, singer = track.artist, coverUri = track.artworkUri, url = songUrl),
                    destFile.absolutePath
                )
                importedAudioCount++
            }

            val restoredSong = SongsModel(
                id = songId,
                title = track.title,
                album = track.album,
                singer = track.artist,
                coverUri = track.artworkUri,
                url = songUrl,
                spotifyTrackId = if (track.stableId.startsWith("local:")) "" else track.stableId,
                explicit = track.explicit,
                durationMs = track.durationMs,
                artistIds = track.artistIds
            )
            restoredSongs.add(restoredSong)
        }

        if (saveToOfflineLibrary && restoredSongs.isNotEmpty()) {
            val collectionId = "imported_${System.currentTimeMillis()}"
            OfflineCollectionsPref.saveCollection(
                context = context,
                id = collectionId,
                name = interpretation.collectionName,
                coverUri = restoredSongs.firstOrNull()?.coverUri.orEmpty(),
                artists = restoredSongs.map { it.singer }.distinct().take(3).joinToString(", "),
                isPlaylist = true,
                songs = restoredSongs
            )
        }

        AppDiagnostics.info("NovaAc", "Interpreted ${restoredSongs.size} tracks from NovaBytecode v4 ($importedAudioCount audio files restored)")
        ImportResult(interpretation.collectionName, interpretation.sourceType, restoredSongs, header, importedAudioCount)
    }

    private fun importFramedArchive(
        context: Context,
        source: Uri,
        saveToOfflineLibrary: Boolean,
        browserPassphrase: String?,
    ): ImportResult {
        return context.contentResolver.openInputStream(source)?.use { rawInput ->
            val headerInput = DataInputStream(BufferedInputStream(rawInput, ARCHIVE_BUFFER_BYTES))
            val header = readHeader(headerInput)
            require(header.protocol == FRAMED_PROTOCOL) { "This archive does not use the framed NovaAc protocol" }
            val nonceLength = headerInput.readUnsignedByte()
            require(nonceLength == 12) { "Invalid framed archive nonce" }
            val baseNonce = ByteArray(nonceLength)
            headerInput.readFully(baseNonce)
            val security = runCatching { ArchiveSecurityMode.valueOf(header.securityMode) }
                .getOrDefault(ArchiveSecurityMode.DEVICE_SECURE)
            val key = if (security == ArchiveSecurityMode.WEB_PASSPHRASE) {
                require(!browserPassphrase.isNullOrBlank()) { "This archive needs its Web Passphrase before it can be imported." }
                val salt = Base64.decode(header.kdfSaltBase64, Base64.DEFAULT)
                derivePassphraseKey(browserPassphrase, salt, header.kdfIterations.coerceIn(100_000, 500_000))
            } else {
                getOrCreateKey()
            }
            val frameAadHeader = if (header.protocol == FRAMED_PROTOCOL) header.toMetaString().encodeToByteArray() else ByteArray(0)
            FramedCipherInputStream(headerInput, key, baseNonce, frameAadHeader).use { framedInput ->
                GZIPInputStream(framedInput, ARCHIVE_BUFFER_BYTES).use { gzipInput ->
                    DataInputStream(BufferedInputStream(gzipInput, ARCHIVE_BUFFER_BYTES)).use { bytecode ->
                        interpretFramedBytecode(context, bytecode, header, saveToOfflineLibrary)
                    }
                }
            }
        } ?: error("Failed to open framed NovaAc archive")
    }

    private fun interpretFramedBytecode(
        context: Context,
        input: DataInputStream,
        header: Header,
        saveToOfflineLibrary: Boolean,
    ): ImportResult {
        var collectionName = header.collectionName
        var sourceType = header.sourceType
        val restoredSongs = mutableListOf<SongsModel>()
        var importedAudioCount = 0
        val downloadsDir = File(context.filesDir, "downloads").apply { mkdirs() }
        val transferBuffer = ByteArray(ARCHIVE_BUFFER_BYTES)

        var stableId = ""
        var title = ""
        var artist = ""
        var album = ""
        var artwork = ""
        var duration = 0
        var explicit = false
        var artistIds = ""
        var audioContainer = "m4a"
        var audioFileName = ""
        var audioSha256 = ""
        var restoredAudio: File? = null

        fun resetTrack() {
            stableId = ""
            title = ""
            artist = ""
            album = ""
            artwork = ""
            duration = 0
            explicit = false
            artistIds = ""
            audioContainer = "m4a"
            audioFileName = ""
            audioSha256 = ""
            restoredAudio = null
        }

        while (true) {
            when (input.readByte()) {
                OP_META_NAME -> collectionName = input.readUTF()
                OP_META_SOURCE -> sourceType = input.readUTF()
                OP_META_TIMESTAMP -> input.readUTF() // Header already records archive creation time.
                OP_TRACK_BEGIN -> resetTrack()
                OP_TRACK_STABLE_ID -> stableId = input.readUTF()
                OP_TRACK_TITLE -> title = input.readUTF()
                OP_TRACK_ARTIST -> artist = input.readUTF()
                OP_TRACK_ALBUM -> album = input.readUTF()
                OP_TRACK_ARTWORK -> artwork = input.readUTF()
                OP_TRACK_DURATION -> duration = readVarInt(input)
                OP_TRACK_EXPLICIT -> explicit = input.readBoolean()
                OP_TRACK_ARTIST_IDS -> artistIds = input.readUTF()
                OP_AUDIO_CONTAINER -> audioContainer = input.readUTF()
                OP_AUDIO_FILE_NAME -> audioFileName = input.readUTF()
                OP_AUDIO_SHA256 -> audioSha256 = input.readUTF()
                OP_AUDIO_PAYLOAD -> {
                    val expectedCrc = input.readLong()
                    val payloadLength = readVarLong(input)
                    require(payloadLength >= 0L) { "Negative framed audio payload length" }
                    val songId = stableId.hashCode() and 0x7fffffff
                    val extension = safeAudioExtension(audioContainer)
                    val destination = File(downloadsDir, "$songId.$extension")
                    val partial = File(downloadsDir, ".${songId}.${extension}.partial")
                    partial.delete()
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    val crc = CRC32()
                    var remaining = payloadLength
                    FileOutputStream(partial).buffered(ARCHIVE_BUFFER_BYTES).use { output ->
                        while (remaining > 0L) {
                            val requested = minOf(transferBuffer.size.toLong(), remaining).toInt()
                            val read = input.read(transferBuffer, 0, requested)
                            require(read > 0) { "Archive ended while restoring $title" }
                            output.write(transferBuffer, 0, read)
                            digest.update(transferBuffer, 0, read)
                            crc.update(transferBuffer, 0, read)
                            remaining -= read.toLong()
                        }
                        output.flush()
                    }
                    val digestHex = digest.digest().joinToString("") { "%02x".format(it) }
                    require(crc.value == expectedCrc) { "NovaAc payload CRC32 mismatch for $title" }
                    require(audioSha256.isBlank() || digestHex.equals(audioSha256, ignoreCase = true)) { "NovaAc payload SHA-256 mismatch for $title" }
                    if (destination.exists()) destination.delete()
                    require(partial.renameTo(destination)) { "Unable to finalize restored audio for $title" }
                    restoredAudio = destination
                }
                OP_TRACK_END -> {
                    val songId = stableId.hashCode() and 0x7fffffff
                    val url = restoredAudio?.let { Uri.fromFile(it).toString() }
                        ?: "spotify:track:${stableId.removePrefix("local:")}" 
                    val song = SongsModel(
                        id = songId,
                        title = title,
                        album = album,
                        singer = artist,
                        coverUri = artwork,
                        url = url,
                        spotifyTrackId = if (stableId.startsWith("local:")) "" else stableId,
                        explicit = explicit,
                        durationMs = duration,
                        artistIds = artistIds,
                    )
                    if (restoredAudio != null) {
                        com.music.spotui.data.preferences.addDownload(context, song, restoredAudio!!.absolutePath)
                        importedAudioCount++
                    }
                    restoredSongs.add(song)
                }
                OP_ARCHIVE_END -> break
                else -> error("Unknown framed NovaAc opcode")
            }
        }
        if (saveToOfflineLibrary && restoredSongs.isNotEmpty()) {
            val collectionId = "imported_${System.currentTimeMillis()}"
            OfflineCollectionsPref.saveCollection(
                context = context,
                id = collectionId,
                name = collectionName,
                coverUri = restoredSongs.firstOrNull()?.coverUri.orEmpty(),
                artists = restoredSongs.map { it.singer }.distinct().take(3).joinToString(", "),
                isPlaylist = true,
                songs = restoredSongs,
            )
        }
        return ImportResult(collectionName, sourceType, restoredSongs, header, importedAudioCount)
    }

    /**
     * NovaBytecode v4 Compiler Engine (LEB128 + CRC32 Protocol)
     */
    private object NovaCompiler {
        fun compile(collectionName: String, sourceType: String, createdAt: String, tracks: List<NovaAcTrack>): ByteArray {
            val bos = ByteArrayOutputStream()
            DataOutputStream(bos).use { out ->
                // Global Attributes
                out.writeByte(OP_META_NAME.toInt())
                out.writeUTF(collectionName)

                out.writeByte(OP_META_SOURCE.toInt())
                out.writeUTF(sourceType)

                out.writeByte(OP_META_TIMESTAMP.toInt())
                out.writeUTF(createdAt)

                // Track Compilation
                for (track in tracks) {
                    out.writeByte(OP_TRACK_BEGIN.toInt())

                    out.writeByte(OP_TRACK_STABLE_ID.toInt())
                    out.writeUTF(track.stableId)

                    out.writeByte(OP_TRACK_TITLE.toInt())
                    out.writeUTF(track.title)

                    out.writeByte(OP_TRACK_ARTIST.toInt())
                    out.writeUTF(track.artist)

                    out.writeByte(OP_TRACK_ALBUM.toInt())
                    out.writeUTF(track.album)

                    out.writeByte(OP_TRACK_ARTWORK.toInt())
                    out.writeUTF(track.artworkUri)

                    out.writeByte(OP_TRACK_DURATION.toInt())
                    writeVarInt(out, track.durationMs)

                    out.writeByte(OP_TRACK_EXPLICIT.toInt())
                    out.writeBoolean(track.explicit)

                    out.writeByte(OP_TRACK_ARTIST_IDS.toInt())
                    out.writeUTF(track.artistIds)

                    if (track.audioBytes != null && track.audioBytes.isNotEmpty()) {
                        out.writeByte(OP_AUDIO_CONTAINER.toInt())
                        out.writeUTF(track.audioContainer)
                        out.writeByte(OP_AUDIO_FILE_NAME.toInt())
                        out.writeUTF(track.audioFileName)
                        out.writeByte(OP_AUDIO_SHA256.toInt())
                        out.writeUTF(track.audioSha256)
                        out.writeByte(OP_AUDIO_PAYLOAD.toInt())

                        // Compute CRC32 Checksum
                        val crc = CRC32()
                        crc.update(track.audioBytes)
                        out.writeLong(crc.value)

                        writeVarInt(out, track.audioBytes.size)
                        out.write(track.audioBytes)
                    }

                    out.writeByte(OP_TRACK_END.toInt())
                }

                out.writeByte(OP_ARCHIVE_END.toInt())
            }
            return bos.toByteArray()
        }
    }

    /**
     * NovaBytecode v4 Interpreter Engine
     */
    private object NovaInterpreter {
        data class ExecutionResult(
            val collectionName: String,
            val sourceType: String,
            val createdAt: String,
            val tracks: List<NovaAcTrack>
        )

        fun interpret(bytecode: ByteArray): ExecutionResult {
            var collectionName = "Imported Collection"
            var sourceType = "imported"
            var createdAt = ""
            val tracks = mutableListOf<NovaAcTrack>()

            val dis = DataInputStream(ByteArrayInputStream(bytecode))

            var currentStableId = ""
            var currentTitle = ""
            var currentArtist = ""
            var currentAlbum = ""
            var currentArtwork = ""
            var currentDuration = 0
            var currentExplicit = false
            var currentArtistIds = ""
            var currentAudioBytes: ByteArray? = null
            var currentAudioContainer = "m4a"
            var currentAudioFileName = ""
            var currentAudioSha256 = ""

            while (dis.available() > 0) {
                when (dis.readByte()) {
                    OP_META_NAME       -> collectionName = dis.readUTF()
                    OP_META_SOURCE     -> sourceType = dis.readUTF()
                    OP_META_TIMESTAMP  -> createdAt = dis.readUTF()
                    OP_TRACK_BEGIN     -> {
                        currentStableId = ""
                        currentTitle = ""
                        currentArtist = ""
                        currentAlbum = ""
                        currentArtwork = ""
                        currentDuration = 0
                        currentExplicit = false
                        currentArtistIds = ""
                        currentAudioBytes = null
                        currentAudioContainer = "m4a"
                        currentAudioFileName = ""
                        currentAudioSha256 = ""
                    }
                    OP_TRACK_STABLE_ID -> currentStableId = dis.readUTF()
                    OP_TRACK_TITLE     -> currentTitle = dis.readUTF()
                    OP_TRACK_ARTIST    -> currentArtist = dis.readUTF()
                    OP_TRACK_ALBUM     -> currentAlbum = dis.readUTF()
                    OP_TRACK_ARTWORK   -> currentArtwork = dis.readUTF()
                    OP_TRACK_DURATION  -> currentDuration = readVarInt(dis)
                    OP_TRACK_EXPLICIT  -> currentExplicit = dis.readBoolean()
                    OP_TRACK_ARTIST_IDS-> currentArtistIds = dis.readUTF()
                    OP_AUDIO_CONTAINER -> currentAudioContainer = dis.readUTF()
                    OP_AUDIO_FILE_NAME -> currentAudioFileName = dis.readUTF()
                    OP_AUDIO_SHA256 -> currentAudioSha256 = dis.readUTF()
                    OP_AUDIO_PAYLOAD   -> {
                        val expectedCrc = dis.readLong()
                        val length = readVarInt(dis)
                        val blob = ByteArray(length)
                        dis.readFully(blob)

                        // Verify frame checksum
                        val crc = CRC32()
                        crc.update(blob)
                        if (crc.value == expectedCrc) {
                            currentAudioBytes = blob
                        } else {
                            AppDiagnostics.warning("NovaAc", "Audio payload CRC32 mismatch for track $currentStableId")
                        }
                    }
                    OP_TRACK_END       -> {
                        tracks.add(
                            NovaAcTrack(
                                stableId = currentStableId,
                                title = currentTitle,
                                artist = currentArtist,
                                album = currentAlbum,
                                artworkUri = currentArtwork,
                                durationMs = currentDuration,
                                explicit = currentExplicit,
                                artistIds = currentArtistIds,
                                audioBytes = currentAudioBytes,
                                audioContainer = currentAudioContainer,
                                audioFileName = currentAudioFileName,
                                audioSha256 = currentAudioSha256,
                            )
                        )
                    }
                    OP_ARCHIVE_END     -> break
                }
            }
            return ExecutionResult(collectionName, sourceType, createdAt, tracks)
        }
    }

    // Helper functions for Variable-Length Quantity (VarInt / LEB128) encoding
    private fun writeVarInt(out: DataOutputStream, value: Int) {
        var v = value
        while ((v and -0x80) != 0) {
            out.writeByte((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.writeByte(v and 0x7F)
    }

    /** Unsigned LEB128 long form removes the old 2 GiB per-payload ceiling. */
    private fun writeVarLong(out: DataOutputStream, value: Long) {
        require(value >= 0L) { "Negative NovaAc payload length" }
        var remaining = value
        while ((remaining and -0x80L) != 0L) {
            out.writeByte(((remaining and 0x7FL) or 0x80L).toInt())
            remaining = remaining ushr 7
        }
        out.writeByte((remaining and 0x7FL).toInt())
    }

    private fun readVarInt(dis: DataInputStream): Int {
        var value = 0
        var shift = 0
        while (shift < 35) {
            val b = dis.readByte().toInt()
            value = value or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) return value
            shift += 7
        }
        throw IllegalArgumentException("Variable-length integer overflow")
    }

    private fun readVarLong(dis: DataInputStream): Long {
        var value = 0L
        var shift = 0
        while (shift < 64) {
            val byte = dis.readUnsignedByte()
            value = value or ((byte and 0x7F).toLong() shl shift)
            if ((byte and 0x80) == 0) return value
            shift += 7
        }
        throw IllegalArgumentException("Variable-length long overflow")
    }

    private data class StreamingCounters(
        val archiveBytesWritten: Long,
        val sourceBytesCopied: Long,
        val includedAudioTrackCount: Int,
        val skippedAudioTrackCount: Int,
    )

    private class CountingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        var bytesWritten: Long = 0L
            private set
        override fun write(value: Int) {
            out.write(value)
            bytesWritten++
        }
        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            out.write(buffer, offset, length)
            bytesWritten += length.toLong()
        }
    }

    /** Lets encryption/compression close normally without closing the staged file before fsync. */
    private class CloseShieldOutputStream(output: OutputStream) : FilterOutputStream(output) {
        override fun close() {
            flush()
        }
    }

    /**
     * Each frame is AES-GCM authenticated independently. This prevents provider-level buffering
     * of a playlist-sized message while retaining authenticated encryption for every byte.
     */
    private class FramedCipherOutputStream(
        output: OutputStream,
        private val key: SecretKey,
        private val baseNonce: ByteArray,
        private val aadHeader: ByteArray = ByteArray(0),
    ) : OutputStream() {
        private val data = DataOutputStream(output)
        private val buffer = ByteArray(FRAME_PLAINTEXT_BYTES)
        private val frameAad = ByteArray(8)
        private val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        private var buffered = 0
        private var frameIndex = 0L
        private var closed = false

        override fun write(value: Int) {
            buffer[buffered++] = value.toByte()
            if (buffered == buffer.size) flushFrame()
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            var sourceOffset = offset
            var remaining = length
            while (remaining > 0) {
                val copied = minOf(remaining, buffer.size - buffered)
                System.arraycopy(bytes, sourceOffset, buffer, buffered, copied)
                buffered += copied
                sourceOffset += copied
                remaining -= copied
                if (buffered == buffer.size) flushFrame()
            }
        }

        override fun flush() {
            data.flush()
        }

        override fun close() {
            if (closed) return
            flushFrame()
            data.writeLong(-1L)
            data.writeInt(0)
            data.writeInt(0)
            data.flush()
            closed = true
        }

        private fun flushFrame() {
            if (buffered == 0) return
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonceFor(frameIndex)))
            if (aadHeader.isNotEmpty()) {
                cipher.updateAAD(aadHeader)
                writeLongBigEndian(frameAad, frameIndex)
                cipher.updateAAD(frameAad)
            }
            val encrypted = cipher.doFinal(buffer, 0, buffered)
            data.writeLong(frameIndex)
            data.writeInt(buffered)
            data.writeInt(encrypted.size)
            data.write(encrypted)
            frameIndex++
            buffered = 0
        }

        private fun nonceFor(index: Long): ByteArray {
            val nonce = baseNonce.copyOf()
            for (offset in 0 until 8) {
                val position = nonce.lastIndex - offset
                val indexByte = ((index ushr (offset * 8)) and 0xFFL).toInt()
                nonce[position] = (nonce[position].toInt() xor indexByte).toByte()
            }
            return nonce
        }
    }

    private class FramedCipherInputStream(
        input: InputStream,
        private val key: SecretKey,
        private val baseNonce: ByteArray,
        private val aadHeader: ByteArray = ByteArray(0),
    ) : InputStream() {
        private val data = DataInputStream(BufferedInputStream(input, ARCHIVE_BUFFER_BYTES))
        private val frameAad = ByteArray(8)
        private val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        private var plaintext = ByteArray(0)
        private var position = 0
        private var expectedIndex = 0L
        private var finished = false

        override fun read(): Int {
            if (!ensureFrame()) return -1
            return plaintext[position++].toInt() and 0xFF
        }

        override fun read(target: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (!ensureFrame()) return -1
            val copied = minOf(length, plaintext.size - position)
            System.arraycopy(plaintext, position, target, offset, copied)
            position += copied
            return copied
        }

        override fun close() {
            data.close()
        }

        private fun ensureFrame(): Boolean {
            while (position >= plaintext.size && !finished) loadFrame()
            return position < plaintext.size
        }

        private fun loadFrame() {
            val frameIndex = data.readLong()
            val plainLength = data.readInt()
            val cipherLength = data.readInt()
            if (frameIndex == -1L) {
                require(plainLength == 0 && cipherLength == 0) { "Malformed framed archive terminator" }
                finished = true
                plaintext = ByteArray(0)
                position = 0
                return
            }
            require(frameIndex == expectedIndex) { "Framed archive sequence interrupted at frame $expectedIndex" }
            require(plainLength in 1..FRAME_PLAINTEXT_BYTES) { "Invalid framed archive plaintext length" }
            require(cipherLength in (plainLength + 16)..(plainLength + 32)) { "Invalid framed archive ciphertext length" }
            val encrypted = ByteArray(cipherLength)
            data.readFully(encrypted)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, frameNonce(baseNonce, frameIndex)))
            if (aadHeader.isNotEmpty()) {
                cipher.updateAAD(aadHeader)
                writeLongBigEndian(frameAad, frameIndex)
                cipher.updateAAD(frameAad)
            }
            plaintext = cipher.doFinal(encrypted)
            require(plaintext.size == plainLength) { "Framed archive plaintext length mismatch at frame $frameIndex" }
            position = 0
            expectedIndex++
        }
    }

    private fun writeFramedStreamingContainer(
        rawOutput: OutputStream,
        header: Header,
        plan: FullAudioArchivePlan,
        encryptionKey: SecretKey,
        baseNonce: ByteArray,
        payloadBufferBytes: Int,
        publish: (ExportStage, Int, String, Long, Long, Long, Long, Int, Int, String) -> Unit,
        shouldCancel: () -> Boolean,
    ): StreamingCounters {
        val countingOutput = CountingOutputStream(rawOutput)
        val headerBytes = header.toMetaString().encodeToByteArray()
        DataOutputStream(countingOutput).apply {
            write(MAGIC.encodeToByteArray())
            writeInt(headerBytes.size)
            write(headerBytes)
            writeByte(baseNonce.size)
            write(baseNonce)
            flush()
        }
        var sourceBytesCopied = 0L
        var included = 0
        var skipped = if (plan.mode == AudioPayloadMode.INCLUDE_AVAILABLE_LOCAL_AUDIO) plan.missingTrackCount else 0
        val reusablePayloadBuffer = ByteArray(payloadBufferBytes.coerceIn(64 * 1024, 512 * 1024))
        publish(ExportStage.WRITING_METADATA, 0, "Preparing framed encrypted NovaAc stream", 0L, 0L, 0L, countingOutput.bytesWritten, included, skipped, "MrBean ${MrbeanNovaAcController.snapshot(com.music.spotui.MyApplication.instance).profile.label} profile · archive header written")
        FramedCipherOutputStream(countingOutput, encryptionKey, baseNonce, headerBytes).use { frameOutput ->
            GZIPOutputStream(frameOutput).use { gzipOutput ->
                DataOutputStream(gzipOutput).use { out ->
                    out.writeByte(OP_META_NAME.toInt())
                    out.writeUTF(plan.collectionName)
                    out.writeByte(OP_META_SOURCE.toInt())
                    out.writeUTF(plan.sourceType)
                    out.writeByte(OP_META_TIMESTAMP.toInt())
                    out.writeUTF(header.createdAt)
                    plan.songs.zip(plan.coverage).forEachIndexed { zeroIndex, (song, coverage) ->
                        if (shouldCancel()) throw java.util.concurrent.CancellationException("NovaAc export cancelled")
                        val outcome = writeStreamingTrack(
                            out = out,
                            song = song,
                            localPath = if (plan.mode == AudioPayloadMode.METADATA_ONLY) null else coverage.path,
                            trackIndex = zeroIndex + 1,
                            totalTrackCount = plan.songs.size,
                            sourceBytesAlreadyCopied = sourceBytesCopied,
                            archiveBytesWritten = { countingOutput.bytesWritten },
                            publish = publish,
                            shouldCancel = shouldCancel,
                            reusableBuffer = reusablePayloadBuffer,
                        )
                        sourceBytesCopied += outcome.sourceBytesCopied
                        if (outcome.includedPayload) included++
                        if (outcome.skippedPayload) skipped++
                    }
                    out.writeByte(OP_ARCHIVE_END.toInt())
                    out.flush()
                }
            }
        }
        return StreamingCounters(countingOutput.bytesWritten, sourceBytesCopied, included, skipped)
    }

    private fun writeStreamingContainer(
        rawOutput: OutputStream,
        header: Header,
        plan: FullAudioArchivePlan,
        cipher: Cipher,
        publish: (ExportStage, Int, String, Long, Long, Long, Long, Int, Int, String) -> Unit,
        shouldCancel: () -> Boolean,
    ): StreamingCounters {
        val countingOutput = CountingOutputStream(rawOutput)
        val headerBytes = header.toMetaString().encodeToByteArray()
        DataOutputStream(countingOutput).apply {
            write(MAGIC.encodeToByteArray())
            writeInt(headerBytes.size)
            write(headerBytes)
            writeByte(cipher.iv.size)
            write(cipher.iv)
            flush()
        }
        var sourceBytesCopied = 0L
        var included = 0
        var skipped = if (plan.mode == AudioPayloadMode.INCLUDE_AVAILABLE_LOCAL_AUDIO) plan.missingTrackCount else 0
        val reusablePayloadBuffer = ByteArray(ARCHIVE_BUFFER_BYTES)
        publish(ExportStage.WRITING_METADATA, 0, "Preparing encrypted NovaAc stream", 0L, 0L, 0L, countingOutput.bytesWritten, included, skipped, "Archive header written")
        CipherOutputStream(countingOutput, cipher).use { cipherOutput ->
            GZIPOutputStream(cipherOutput).use { gzipOutput ->
                DataOutputStream(gzipOutput).use { out ->
                    out.writeByte(OP_META_NAME.toInt())
                    out.writeUTF(plan.collectionName)
                    out.writeByte(OP_META_SOURCE.toInt())
                    out.writeUTF(plan.sourceType)
                    out.writeByte(OP_META_TIMESTAMP.toInt())
                    out.writeUTF(header.createdAt)
                    plan.songs.zip(plan.coverage).forEachIndexed { zeroIndex, (song, coverage) ->
                        if (shouldCancel()) throw java.util.concurrent.CancellationException("NovaAc export cancelled")
                        val outcome = writeStreamingTrack(
                            out = out,
                            song = song,
                            localPath = if (plan.mode == AudioPayloadMode.METADATA_ONLY) null else coverage.path,
                            trackIndex = zeroIndex + 1,
                            totalTrackCount = plan.songs.size,
                            sourceBytesAlreadyCopied = sourceBytesCopied,
                            archiveBytesWritten = { countingOutput.bytesWritten },
                            publish = publish,
                            shouldCancel = shouldCancel,
                            reusableBuffer = reusablePayloadBuffer,
                        )
                        sourceBytesCopied += outcome.sourceBytesCopied
                        if (outcome.includedPayload) included++
                        if (outcome.skippedPayload) skipped++
                    }
                    out.writeByte(OP_ARCHIVE_END.toInt())
                    out.flush()
                }
            }
        }
        return StreamingCounters(countingOutput.bytesWritten, sourceBytesCopied, included, skipped)
    }

    private data class StreamingTrackOutcome(
        val includedPayload: Boolean,
        val skippedPayload: Boolean,
        val sourceBytesCopied: Long,
    )

    private fun writeStreamingTrack(
        out: DataOutputStream,
        song: SongsModel,
        localPath: String?,
        trackIndex: Int,
        totalTrackCount: Int,
        sourceBytesAlreadyCopied: Long,
        archiveBytesWritten: () -> Long,
        publish: (ExportStage, Int, String, Long, Long, Long, Long, Int, Int, String) -> Unit,
        shouldCancel: () -> Boolean,
        reusableBuffer: ByteArray,
    ): StreamingTrackOutcome {
        out.writeByte(OP_TRACK_BEGIN.toInt())
        out.writeByte(OP_TRACK_STABLE_ID.toInt())
        out.writeUTF(song.spotifyTrackId.ifBlank { "local:${song.id}" })
        out.writeByte(OP_TRACK_TITLE.toInt())
        out.writeUTF(song.title)
        out.writeByte(OP_TRACK_ARTIST.toInt())
        out.writeUTF(song.singer)
        out.writeByte(OP_TRACK_ALBUM.toInt())
        out.writeUTF(song.album)
        out.writeByte(OP_TRACK_ARTWORK.toInt())
        out.writeUTF(song.coverUri)
        out.writeByte(OP_TRACK_DURATION.toInt())
        writeVarInt(out, song.durationMs.coerceAtLeast(0))
        out.writeByte(OP_TRACK_EXPLICIT.toInt())
        out.writeBoolean(song.explicit)
        out.writeByte(OP_TRACK_ARTIST_IDS.toInt())
        out.writeUTF(song.artistIds)

        val localFile = localPath?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
        // A downloaded entry can be cleared or replaced between planning and writing. Verify the
        // file before emitting any audio frames; in best-effort mode an unavailable payload is
        // omitted while the track metadata remains exportable instead of failing the whole archive.
        val payloadIntegrity = localFile?.let { file ->
            runCatching {
                publish(ExportStage.HASHING_PAYLOAD, trackIndex, song.title, 0L, file.length(), sourceBytesAlreadyCopied, archiveBytesWritten(), 0, 0, "Verifying local payload")
                scanPayload(file, reusableBuffer) { scanned ->
                    if (shouldCancel()) throw java.util.concurrent.CancellationException("NovaAc export cancelled")
                    publish(ExportStage.HASHING_PAYLOAD, trackIndex, song.title, scanned, file.length(), sourceBytesAlreadyCopied, archiveBytesWritten(), 0, 0, "Hashing and checksumming payload")
                }
            }.onFailure { AppDiagnostics.warning("NovaAc", "Skipping unreadable local audio for ${song.title}", it) }.getOrNull()
        }
        if (localFile != null && payloadIntegrity != null) {
            out.writeByte(OP_AUDIO_CONTAINER.toInt())
            out.writeUTF(safeAudioExtension(localFile.extension))
            out.writeByte(OP_AUDIO_FILE_NAME.toInt())
            out.writeUTF(localFile.name)
            out.writeByte(OP_AUDIO_SHA256.toInt())
            out.writeUTF(payloadIntegrity.sha256)
            out.writeByte(OP_AUDIO_PAYLOAD.toInt())
            out.writeLong(payloadIntegrity.crc32)
            writeVarLong(out, payloadIntegrity.byteCount)
            var copied = 0L
            FileInputStream(localFile).buffered(reusableBuffer.size).use { input ->
                while (true) {
                    if (shouldCancel()) throw java.util.concurrent.CancellationException("NovaAc export cancelled")
                    val read = input.read(reusableBuffer)
                    if (read < 0) break
                    out.write(reusableBuffer, 0, read)
                    copied += read.toLong()
                    publish(ExportStage.COPYING_PAYLOAD, trackIndex, song.title, copied, payloadIntegrity.byteCount, sourceBytesAlreadyCopied + copied, archiveBytesWritten(), 0, 0, "Streaming payload to encrypted archive")
                }
            }
            require(copied == payloadIntegrity.byteCount) { "Local payload changed while exporting ${song.title}" }
            out.writeByte(OP_TRACK_END.toInt())
            return StreamingTrackOutcome(includedPayload = true, skippedPayload = false, sourceBytesCopied = copied)
        }
        out.writeByte(OP_TRACK_END.toInt())
        return StreamingTrackOutcome(includedPayload = false, skippedPayload = localPath != null, sourceBytesCopied = 0L)
    }

    private data class StreamingPayloadIntegrity(
        val sha256: String,
        val crc32: Long,
        val byteCount: Long,
    )

    private fun scanPayload(file: File, buffer: ByteArray, onProgress: (Long) -> Unit): StreamingPayloadIntegrity {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val crc = CRC32()
        var total = 0L
        FileInputStream(file).buffered(buffer.size).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                crc.update(buffer, 0, read)
                total += read.toLong()
                onProgress(total)
            }
        }
        require(total == file.length()) { "Local payload changed while it was being verified" }
        return StreamingPayloadIntegrity(digest.digest().joinToString("") { "%02x".format(it) }, crc.value, total)
    }

    private fun sha256Of(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(ARCHIVE_BUFFER_BYTES)
        FileInputStream(file).buffered(buffer.size).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256Of(input: InputStream): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(ARCHIVE_BUFFER_BYTES)
        input.buffered(buffer.size).use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256Of(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun safeAudioExtension(raw: String): String = raw.lowercase().removePrefix(".").takeIf {
        it in setOf("m4a", "mp4", "webm", "opus", "ogg", "flac", "mp3", "wav", "aac")
    } ?: "m4a"

    private fun crc32Of(file: File): Long {
        val crc = CRC32()
        FileInputStream(file).buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                crc.update(buffer, 0, read)
            }
        }
        return crc.value
    }

    private fun serialize(header: Header, nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        require(nonce.size in 1..255) { "Invalid encryption nonce size" }
        val headerBytes = header.toMetaString().encodeToByteArray()
        return ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { data ->
                data.write(MAGIC.encodeToByteArray())
                data.writeInt(headerBytes.size)
                data.write(headerBytes)
                data.writeByte(nonce.size)
                data.write(nonce)
                data.write(ciphertext)
            }
            buffer.toByteArray()
        }
    }

    private fun readHeader(input: DataInputStream): Header {
        val magic = ByteArray(MAGIC.length)
        input.readFully(magic)
        val magicStr = magic.decodeToString()
        check(magicStr.startsWith("NOVAAC")) { "Not a valid SpotUI NovaAc file" }
        val headerLength = input.readInt()
        check(headerLength in 2..65_536) { "Invalid NovaAc header size" }
        val headerBytes = ByteArray(headerLength)
        input.readFully(headerBytes)

        val kvMap = headerBytes.decodeToString().split("\n").associate { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else "" to ""
        }

        return Header(
            formatVersion = kvMap["formatVersion"]?.toIntOrNull() ?: 1,
            appVersion = kvMap["appVersion"] ?: "unknown",
            createdAt = kvMap["createdAt"] ?: "unknown",
            collectionName = kvMap["collectionName"] ?: "Untitled collection",
            sourceType = kvMap["sourceType"] ?: "unknown",
            trackCount = kvMap["trackCount"]?.toIntOrNull() ?: 0,
            payloadBytes = kvMap["payloadBytes"]?.toLongOrNull() ?: 0L,
            includesAudioPayload = kvMap["includesAudioPayload"]?.toBoolean() ?: false,
            requestedAudioMode = kvMap["requestedAudioMode"] ?: AudioPayloadMode.METADATA_ONLY.name,
            includedAudioTrackCount = kvMap["includedAudioTrackCount"]?.toIntOrNull() ?: 0,
            missingAudioTrackCount = kvMap["missingAudioTrackCount"]?.toIntOrNull() ?: 0,
            localAudioBytes = kvMap["localAudioBytes"]?.toLongOrNull() ?: 0L,
            securityMode = kvMap["securityMode"] ?: ArchiveSecurityMode.DEVICE_SECURE.name,
            kdfSaltBase64 = kvMap["kdfSaltBase64"] ?: "",
            kdfIterations = kvMap["kdfIterations"]?.toIntOrNull() ?: 0,
            protocol = kvMap["protocol"] ?: LEGACY_PROTOCOL,
        )
    }

    private fun localAudioPath(context: Context, song: SongsModel): String? =
        com.music.spotui.data.preferences.downloadedPathForQuery(context, song.url)
            ?: song.spotifyTrackId.takeIf { it.isNotBlank() }?.let { id ->
                com.music.spotui.data.preferences.downloadedPathForQuery(context, "spotify:track:$id")
            }

    private fun extractAudioBytes(context: Context, song: SongsModel): ByteArray? = runCatching {
        val file = localAudioPath(context, song)?.let(::File) ?: return@runCatching null
        if (file.isFile && file.length() in 1..Int.MAX_VALUE.toLong()) file.readBytes() else null
    }.getOrNull()

    private fun safeFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').take(96).ifBlank { "novaac_archive" }

    private fun compressBytes(bytes: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(bytes) }
        return bos.toByteArray()
    }

    private fun decompressBytes(bytes: ByteArray): ByteArray {
        val bis = ByteArrayInputStream(bytes)
        val os = ByteArrayOutputStream()
        GZIPInputStream(bis).use { it.copyTo(os) }
        return os.toByteArray()
    }

    private fun derivePassphraseKey(
        passphrase: String,
        salt: ByteArray,
        iterations: Int = WEB_KDF_ITERATIONS,
    ): SecretKey {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        val material = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(material, "AES")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private fun Header.toMetaString(): String = buildString {
        append("formatVersion=").append(formatVersion).append("\n")
        append("appVersion=").append(appVersion).append("\n")
        append("createdAt=").append(createdAt).append("\n")
        append("collectionName=").append(collectionName).append("\n")
        append("sourceType=").append(sourceType).append("\n")
        append("trackCount=").append(trackCount).append("\n")
        append("payloadBytes=").append(payloadBytes).append("\n")
        append("includesAudioPayload=").append(includesAudioPayload).append("\n")
        append("requestedAudioMode=").append(requestedAudioMode).append("\n")
        append("includedAudioTrackCount=").append(includedAudioTrackCount).append("\n")
        append("missingAudioTrackCount=").append(missingAudioTrackCount).append("\n")
        append("localAudioBytes=").append(localAudioBytes).append("\n")
        append("securityMode=").append(securityMode).append("\n")
        append("kdfSaltBase64=").append(kdfSaltBase64).append("\n")
        append("kdfIterations=").append(kdfIterations).append("\n")
        append("protocol=").append(protocol).append("\n")
    }

    private fun toNovaTrack(song: SongsModel, audioBytes: ByteArray?, localFile: File?): NovaAcTrack = NovaAcTrack(
        stableId = song.spotifyTrackId.ifBlank { "local:${song.id}" },
        title = song.title,
        artist = song.singer,
        album = song.album,
        artworkUri = song.coverUri,
        durationMs = song.durationMs,
        explicit = song.explicit,
        artistIds = song.artistIds,
        audioBytes = audioBytes,
        audioContainer = safeAudioExtension(localFile?.extension.orEmpty()),
        audioFileName = localFile?.name.orEmpty(),
        audioSha256 = audioBytes?.let(::sha256Of).orEmpty(),
    )

    private fun appVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty().ifBlank { "unknown" }
}
