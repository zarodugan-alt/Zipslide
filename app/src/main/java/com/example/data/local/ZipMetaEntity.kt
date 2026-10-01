package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** User-owned metadata. Scan facts live in [ScanCacheEntry] so they can be refreshed independently. */
@Entity(tableName = "zip_meta")
data class ZipMetaEntity(
    @PrimaryKey val path: String,
    val favorite: Boolean = false,
    val lastFrameIndex: Int = 0,
    val watchCount: Int = 0,
    val updatedAt: Long = 0L
)

/** Facts discovered while inspecting a zip's central directory. */
@Entity(tableName = "scan_cache")
data class ScanCacheEntry(
    @PrimaryKey val path: String,
    val volumeId: String,
    val fileName: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val imageCount: Int,
    val hasCover: Boolean,
    val isEncrypted: Boolean,
    val isCorrupt: Boolean,
    val scannedAt: Long
)
