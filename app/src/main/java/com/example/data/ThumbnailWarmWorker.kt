package com.example.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ZipSlideApplication
import kotlinx.coroutines.flow.first

/** Opportunistically fills disk thumbnails after a completed scan without touching the UI thread. */
class ThumbnailWarmWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val repository = (applicationContext as ZipSlideApplication).container.zips
        repository.observeCached().first()
            .asSequence()
            .filter { it.hasCover && it.isMounted && !it.isCorrupt && !it.isEncrypted }
            .forEach { archive -> repository.thumbnail(archive, 512) }
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}
