package com.music.spotui.data.cache

import android.content.Context
import com.music.spotui.util.AppDiagnostics
import java.io.File

/**
 * Enterprise Cache Storage Manager for SpotUI.
 *
 * Provides granular storage accounting, category-specific purges, automatic cache eviction,
 * and isolated management for NovaAc temporary export artifacts.
 */
object CacheStorageManager {

    enum class CacheCategory {
        ALL,
        MEDIA,
        ARTWORK,
        NOVAAC_TEMP,
        OTHER
    }

    data class Summary(
        val totalBytes: Long,
        val mediaBytes: Long,
        val artworkBytes: Long,
        val novaAcTempBytes: Long,
        val otherBytes: Long,
    ) {
        val displaySize: String get() = humanBytes(totalBytes)
        val detail: String
            get() = "Media ${humanBytes(mediaBytes)} • Artwork ${humanBytes(artworkBytes)} • NovaAc ${humanBytes(novaAcTempBytes)} • Other ${humanBytes(otherBytes)}"
    }

    /**
     * Calculates storage footprint across all app cache directories.
     */
    fun summarize(context: Context): Summary {
        val cacheRoot = context.cacheDir
        val media = directorySize(File(cacheRoot, "media"))
        val novaAcTemp = directorySize(File(cacheRoot, "novaac_temp"))
        val artwork = listOf(
            File(cacheRoot, "image_manager_disk_cache"),
            File(cacheRoot, "glide"),
            File(cacheRoot, "artwork"),
        ).sumOf(::directorySize)
        
        val total = directorySize(cacheRoot)
        val accounted = media + artwork + novaAcTemp
        
        return Summary(
            totalBytes = total,
            mediaBytes = media,
            artworkBytes = artwork.coerceAtMost(total),
            novaAcTempBytes = novaAcTemp.coerceAtMost(total),
            otherBytes = (total - accounted).coerceAtLeast(0L),
        )
    }

    /**
     * Purges cache files based on the specified category.
     */
    fun clearCache(context: Context, category: CacheCategory = CacheCategory.ALL): Result<Summary> = runCatching {
        val root = context.cacheDir

        when (category) {
            CacheCategory.ALL -> {
                root.listFiles()?.forEach { child ->
                    if (!child.deleteRecursively()) {
                        AppDiagnostics.warning("CacheStorage", "Could not fully clear ${child.name}")
                    }
                }
            }
            CacheCategory.MEDIA -> {
                File(root, "media").deleteRecursively()
            }
            CacheCategory.ARTWORK -> {
                listOf(
                    File(root, "image_manager_disk_cache"),
                    File(root, "glide"),
                    File(root, "artwork")
                ).forEach { it.deleteRecursively() }
            }
            CacheCategory.NOVAAC_TEMP -> {
                File(root, "novaac_temp").deleteRecursively()
            }
            CacheCategory.OTHER -> {
                val knownDirs = setOf("media", "image_manager_disk_cache", "glide", "artwork", "novaac_temp")
                root.listFiles()?.forEach { child ->
                    if (child.name !in knownDirs) {
                        child.deleteRecursively()
                    }
                }
            }
        }

        val summary = summarize(context)
        AppDiagnostics.info("CacheStorage", "Cache cleared for category: $category")
        summary
    }.onFailure {
        AppDiagnostics.error("CacheStorage", "Cache cleanup failed for category $category", it)
    }

    /**
     * Legacy wrapper for full transient cache cleanup.
     */
    fun clearTransientCache(context: Context): Result<Summary> = clearCache(context, CacheCategory.ALL)

    /**
     * Automatically evicts the oldest media pre-buffer files when cache exceeds [maxBytes].
     */
    fun autoEvictExcessCache(context: Context, maxBytes: Long = 512L * 1024L * 1024L): Long {
        val mediaDir = File(context.cacheDir, "media")
        if (!mediaDir.exists() || !mediaDir.isDirectory) return 0L

        var currentSize = directorySize(mediaDir)
        if (currentSize <= maxBytes) return 0L

        val files = mediaDir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return 0L
        var freedBytes = 0L

        for (file in files) {
            if (currentSize <= maxBytes) break
            val fileSize = file.length()
            if (file.delete()) {
                currentSize -= fileSize
                freedBytes += fileSize
            }
        }

        if (freedBytes > 0) {
            AppDiagnostics.info("CacheStorage", "Auto-evicted ${humanBytes(freedBytes)} of excess media cache")
        }
        return freedBytes
    }

    private fun directorySize(file: File): Long = when {
        !file.exists() -> 0L
        file.isFile -> file.length()
        else -> file.listFiles()?.sumOf(::directorySize) ?: 0L
    }

    private fun humanBytes(bytes: Long): String = when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
