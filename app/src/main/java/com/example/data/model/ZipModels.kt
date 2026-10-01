package com.example.data.model

import android.net.Uri
import java.io.File

/** The backing storage for an archive. A content URI is retained verbatim. */
sealed interface ZipSource {
    data class Direct(val file: File) : ZipSource
    data class Saf(val uri: Uri, val displayName: String) : ZipSource
}

data class ZipItem(
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val volumeId: String,
    val volumeName: String,
    val isMounted: Boolean = true,
    val isSaf: Boolean = false,
    val imageCount: Int = 0,
    val hasCover: Boolean = false,
    val coverEntryName: String? = null,
    val thumbnailPath: String? = null,
    val isEncrypted: Boolean = false,
    val isCorrupt: Boolean = false,
    val isZeroImages: Boolean = false,
    val isFavorite: Boolean = false,
    val lastFrameIndex: Int = 0,
    val isReadOnly: Boolean = false,
    val parentFolder: String = "",
    val watchCount: Int = 0
) {
    /** Names used by repository contracts while preserving the established UI API. */
    val displayName: String get() = name
    val sizeBytes: Long get() = size
    val source: ZipSource
        get() = if (isSaf) ZipSource.Saf(Uri.parse(path), name) else ZipSource.Direct(File(path))
}

data class ZipEntryInfo(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val compressedSize: Long
)

data class ZipEntryItem(
    val entryPath: String,
    val basename: String,
    val isImage: Boolean,
    val size: Long,
    val compressedSize: Long,
    val width: Int = 0,
    val height: Int = 0,
    val folderPath: String = ""
) {
    fun toInfo() = ZipEntryInfo(entryPath, false, size, compressedSize)
}

/** Canonical storage representation exposed by the repository layer. */
data class Volume(
    val id: String,
    val label: String,
    val root: File?,
    val safUri: Uri?,
    val isRemovable: Boolean,
    val isMounted: Boolean,
    val isReadable: Boolean
)

/** UI-compatible volume projection. */
data class StorageVolumeInfo(
    val id: String,
    val name: String,
    val rootFile: File?,
    val uriString: String? = null,
    val isMounted: Boolean = true,
    val isInternal: Boolean = true,
    val isSdCard: Boolean = false,
    val isUsb: Boolean = false,
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val readableDirect: Boolean = true,
    val readableSaf: Boolean = false,
    val zipCount: Int = 0,
    val totalZipSize: Long = 0L
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ViewMode {
    GRID_SMALL, GRID_MEDIUM, GRID_LARGE, LIST;

    companion object {
        // Compatibility names for the existing, intentionally unchanged UI shell.
        val SMALL get() = GRID_SMALL
        val MEDIUM get() = GRID_MEDIUM
        val LARGE get() = GRID_LARGE
    }
}

enum class SortBy {
    NAME, DATE_MODIFIED, DATE_CREATED, SIZE, IMAGE_COUNT, RANDOM;

    companion object {
        val MODIFIED get() = DATE_MODIFIED
        val CREATED get() = DATE_CREATED
    }
}

enum class VolumeFilter { ALL, INTERNAL, SD, USB }

enum class BrowserFilter { ALL, FAVORITES, CONTINUE, MISSING_COVER }

/**
 * Frame-to-frame motion used by the slideshow pager. Every style is driven by the live pager
 * offset so a manual swipe and an automatic advance share exactly the same curve.
 */
enum class SlideTransition(val label: String) {
    FADE("Dissolve"),
    SLIDE("Glide"),
    ZOOM("Zoom"),
    DEPTH("Depth");

    companion object {
        fun fromName(value: String?): SlideTransition =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FADE
    }
}
