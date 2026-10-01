package com.example.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.ZipSlideApplication

/** Unique WorkManager entry point for scans scheduled while the app is backgrounded. */
class ScanWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as ZipSlideApplication).container.zips.rescanNow()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "warm-thumbs",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ThumbnailWarmWorker>().build()
        )
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}
