package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ZipMetaEntity::class], version = 1, exportSchema = false)
abstract class ZipSlideDatabase : RoomDatabase() {
    abstract fun zipMetaDao(): ZipMetaDao

    companion object {
        @Volatile
        private var INSTANCE: ZipSlideDatabase? = null

        fun getInstance(context: Context): ZipSlideDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ZipSlideDatabase::class.java,
                    "zipslide.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
