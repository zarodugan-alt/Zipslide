package com.example.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ZipMetaDao {
    @Query("SELECT * FROM zip_meta WHERE path = :path")
    suspend fun get(path: String): ZipMetaEntity?

    @Query("SELECT * FROM zip_meta")
    fun observeAll(): Flow<List<ZipMetaEntity>>

    @Query("SELECT * FROM zip_meta")
    suspend fun all(): List<ZipMetaEntity>

    @Upsert
    suspend fun upsert(meta: ZipMetaEntity)

    @Query("DELETE FROM zip_meta WHERE path = :path")
    suspend fun delete(path: String)

    @Query("SELECT path FROM zip_meta")
    suspend fun allPaths(): List<String>
}

@Dao
interface ScanCacheDao {
    @Query("SELECT * FROM scan_cache ORDER BY fileName COLLATE NOCASE")
    fun observeAll(): Flow<List<ScanCacheEntry>>

    @Query("SELECT * FROM scan_cache")
    suspend fun all(): List<ScanCacheEntry>

    @Query("SELECT * FROM scan_cache WHERE path = :path LIMIT 1")
    suspend fun get(path: String): ScanCacheEntry?

    @Upsert
    suspend fun upsertAll(entries: List<ScanCacheEntry>)

    @Query("DELETE FROM scan_cache WHERE path NOT IN (:live)")
    suspend fun pruneMissing(live: List<String>)

    @Query("DELETE FROM scan_cache")
    suspend fun clear()

    @Query("DELETE FROM scan_cache WHERE path = :path")
    suspend fun delete(path: String)

    @Query("DELETE FROM scan_cache WHERE volumeId = :volumeId")
    suspend fun clearVolume(volumeId: String)

    @Query("SELECT path FROM scan_cache WHERE volumeId = :volumeId")
    suspend fun pathsForVolume(volumeId: String): List<String>
}
