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
    val scannedAt: Long,
    /** Filesystem creation time where the platform exposes one, else 0. */
    val createdAt: Long = 0L,
    /** Cover entry resolved at scan time; lets the thumbnail pipeline skip a second pass. */
    val coverEntry: String? = null,
    /**
     * Bumped whenever the inspection logic changes so stale rows are re-inspected instead of
     * being trusted because their size and timestamp happen to match.
     */
    val ruleVersion: Int = 0
)
