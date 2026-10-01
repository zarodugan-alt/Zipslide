package com.example.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ZipSlideApplication

class ScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? ZipSlideApplication
            app?.zipRepository?.triggerRescan()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
