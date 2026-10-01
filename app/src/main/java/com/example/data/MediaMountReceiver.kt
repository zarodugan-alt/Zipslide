package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ZipSlideApplication

class MediaMountReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        val path = intent.data?.path
        val app = (context?.applicationContext as? ZipSlideApplication) ?: return

        when (action) {
            Intent.ACTION_MEDIA_MOUNTED -> {
                app.volumeRepository.onMediaMounted(path)
                app.zipRepository.triggerRescan()
            }
            Intent.ACTION_MEDIA_UNMOUNTED,
            Intent.ACTION_MEDIA_EJECT -> {
                app.volumeRepository.onMediaUnmounted(path)
                app.zipRepository.updateMountStates()
            }
        }
    }
}
