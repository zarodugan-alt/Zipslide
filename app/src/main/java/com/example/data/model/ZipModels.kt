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
 * Frame-to-frame motion used by the slideshow pager.
 *
 * The catalogue mirrors the vocabulary established by the classic ViewPager / ViewPager2
 * page-transformer collections (depth, cube in/out, flips, rotations, stack, fan, gate,
 * accordion, tablet, fore/background, zooms) re-implemented as Compose `graphicsLayer` maths so
 * every style is driven by the live pager offset — a swipe and an automatic advance share one curve.
 *
 * Persisted by `name`, so entries may be appended freely.
 */
enum class SlideTransition(val label: String) {
    FADE("Dissolve"),
    SLIDE("Glide"),
    ZOOM("Zoom out"),
    DEPTH("Depth"),
    PUSH("Slide"),
    PARALLAX("Parallax"),
    ZOOM_IN("Zoom in"),
    CUBE_IN("Cube in"),
    CUBE_OUT("Cube out"),
    FLIP("Flip"),
    FLIP_VERTICAL("Flip up"),
    ROTATE_UP("Rotate up"),
    ROTATE_DOWN("Rotate down"),
    STACK("Stack"),
    FAN("Fan"),
    GATE("Gate"),
    ACCORDION("Accordion"),
    TABLET("Tablet"),
    PULL_BACK("Pull back"),
    PUSH_FORWARD("Push forward"),
    VERTICAL("Vertical"),
    SHUTTER("Shutter"),
    CAROUSEL("Carousel"),
    RANDOM("Surprise me");

    val isRandom: Boolean get() = this == RANDOM

    companion object {
        /** Everything the viewer can actually draw; excludes the meta "Surprise me" entry. */
        val concrete: List<SlideTransition> by lazy { entries.filterNot { it.isRandom } }

        fun randomConcrete(): SlideTransition = concrete.random()

        fun fromName(value: String?): SlideTransition =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FADE
    }
}
