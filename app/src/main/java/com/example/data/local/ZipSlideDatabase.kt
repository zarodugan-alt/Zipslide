package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ZipMetaEntity::class, ScanCacheEntry::class],
    version = 2,
    exportSchema = false
)
abstract class ZipSlideDatabase : RoomDatabase() {
    abstract fun zipMetaDao(): ZipMetaDao
    abstract fun scanCacheDao(): ScanCacheDao

    companion object {
        @Volatile private var instance: ZipSlideDatabase? = null

        fun getInstance(context: Context): ZipSlideDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ZipSlideDatabase::class.java,
                    "zipslide.db"
                // The previous pre-release schema held scan data in zip metadata.
                // It is intentionally disposable: revalidation recreates scan_cache.
                ).fallbackToDestructiveMigration(dropAllTables = true).build().also { instance = it }
            }
    }
}
