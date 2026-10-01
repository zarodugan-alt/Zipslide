package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "zip_metadata")
data class ZipMetaEntity(
    @PrimaryKey
    val path: String,
    val isFavorite: Boolean = false,
    val lastFrameIndex: Int = 0,
    val customTitle: String? = null,
    val cachedImageCount: Int = 0,
    val cachedTotalSize: Long = 0L,
    val hasCover: Boolean = true,
    val coverEntryName: String? = null,
    val lastModified: Long = 0L,
    val volumeId: String = "internal",
    val isSaf: Boolean = false
)
