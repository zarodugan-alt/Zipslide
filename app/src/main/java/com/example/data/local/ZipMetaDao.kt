package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ZipMetaDao {

    @Query("SELECT * FROM zip_metadata")
    fun getAllMeta(): Flow<List<ZipMetaEntity>>

    @Query("SELECT * FROM zip_metadata")
    suspend fun getAllMetaList(): List<ZipMetaEntity>

    @Query("SELECT * FROM zip_metadata WHERE path = :path LIMIT 1")
    fun getMeta(path: String): Flow<ZipMetaEntity?>

    @Query("SELECT * FROM zip_metadata WHERE path = :path LIMIT 1")
    suspend fun getMetaSync(path: String): ZipMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: ZipMetaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ZipMetaEntity>)

    @Query("UPDATE zip_metadata SET isFavorite = :isFavorite WHERE path = :path")
    suspend fun updateFavorite(path: String, isFavorite: Boolean)

    @Query("UPDATE zip_metadata SET lastFrameIndex = :frameIndex WHERE path = :path")
    suspend fun updateLastFrame(path: String, frameIndex: Int)

    @Query("DELETE FROM zip_metadata WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM zip_metadata WHERE path NOT IN (:existingPaths)")
    suspend fun deleteMissingPaths(existingPaths: List<String>)
}
