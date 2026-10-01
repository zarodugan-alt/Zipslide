package com.example

import android.app.Application
import com.example.data.SettingsRepository
import com.example.data.VolumeRepository
import com.example.data.ZipRepository
import com.example.data.local.ScanCacheDao
import com.example.data.local.ZipMetaDao
import com.example.data.local.ZipSlideDatabase

/** Manual DI root. Repositories are application singletons and never owned by a Composable. */
class AppContainer(app: Application) {
    val database: ZipSlideDatabase by lazy { ZipSlideDatabase.getInstance(app) }
    val settings: SettingsRepository by lazy { SettingsRepository(app) }
    val metaDao: ZipMetaDao by lazy { database.zipMetaDao() }
    val scanCache: ScanCacheDao by lazy { database.scanCacheDao() }
    val volumes: VolumeRepository by lazy { VolumeRepository(app) }
    val zips: ZipRepository by lazy { ZipRepository(app, settings, metaDao, scanCache, volumes) }
}

class ZipSlideApplication : Application() {
    lateinit var container: AppContainer
        private set

    // These aliases preserve the existing UI's public seam while all construction is centralized.
    val database get() = container.database
    val settingsRepository get() = container.settings
    val volumeRepository get() = container.volumes
    val zipRepository get() = container.zips

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
    }

    companion object {
        lateinit var instance: ZipSlideApplication
            private set
    }
}
