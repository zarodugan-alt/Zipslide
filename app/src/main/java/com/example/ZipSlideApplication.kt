package com.example

import android.app.Application
import com.example.data.SettingsRepository
import com.example.data.VolumeRepository
import com.example.data.ZipRepository
import com.example.data.local.ZipSlideDatabase

class ZipSlideApplication : Application() {

    lateinit var database: ZipSlideDatabase
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var volumeRepository: VolumeRepository
        private set

    lateinit var zipRepository: ZipRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = ZipSlideDatabase.getInstance(this)
        settingsRepository = SettingsRepository(this)
        volumeRepository = VolumeRepository(this)
        zipRepository = ZipRepository(
            context = this,
            zipMetaDao = database.zipMetaDao(),
            volumeRepository = volumeRepository,
            settingsRepository = settingsRepository
        )
    }

    companion object {
        lateinit var instance: ZipSlideApplication
            private set
    }
}
