package com.example.data.model

import java.io.File

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
    val hasCover: Boolean = true,
    val coverEntryName: String? = null,
    val thumbnailPath: String? = null,
    val isEncrypted: Boolean = false,
    val isCorrupt: Boolean = false,
    val isZeroImages: Boolean = false,
    val isFavorite: Boolean = false,
    val lastFrameIndex: Int = 0,
    val isReadOnly: Boolean = false,
    val parentFolder: String = ""
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
)

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

enum class ViewMode {
    SMALL, MEDIUM, LARGE, LIST
}

enum class SortBy {
    NAME, MODIFIED, CREATED, SIZE, IMAGE_COUNT, RANDOM
}

enum class BrowserFilter {
    ALL, FAVORITES, MISSING_COVER
}
