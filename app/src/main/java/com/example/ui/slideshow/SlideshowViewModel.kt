package com.example.ui.slideshow

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ZipSlideApplication
import com.example.data.AppSettings
import com.example.data.model.ZipEntryItem
import com.example.data.model.ZipItem
import com.example.data.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class SlideshowState(
    val zip: ZipItem? = null,
    val images: List<ZipEntryItem> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val loading: Boolean = true,
    val error: String? = null
)
sealed interface SlideshowEvent {
    data class Load(val path: String) : SlideshowEvent
    data class FrameChanged(val path: String, val frame: Int) : SlideshowEvent
    data class Finish(val path: String, val frame: Int) : SlideshowEvent
}

/** Owns the archive playback session and closes the direct ZipFile when playback ends. */
class SlideshowViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ZipSlideApplication).container
    private val local = MutableStateFlow(SlideshowState())
    val state: StateFlow<SlideshowState> = combine(container.settings.settings, local) { settings, local ->
        local.copy(settings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SlideshowState())

    fun onEvent(event: SlideshowEvent) = when (event) {
        is SlideshowEvent.Load -> viewModelScope.launch {
            local.value = SlideshowState(loading = true)
            runCatching {
                val zip = container.zips.findZipItem(event.path) ?: fallback(event.path)
                container.zips.beginPlayback(zip)
                zip to container.zips.getZipEntries(zip).filter { it.isImage }
            }.onSuccess { (zip, images) -> local.value = SlideshowState(zip = zip, images = images, loading = false) }
                .onFailure { local.value = SlideshowState(loading = false, error = it.toUserMessage()) }
        }
        is SlideshowEvent.FrameChanged -> viewModelScope.launch {
            container.zips.updateLastFrame(event.path, event.frame)
        }
        is SlideshowEvent.Finish -> viewModelScope.launch {
            container.zips.finishWatching(event.path, event.frame)
            container.zips.endPlayback(event.path)
        }
    }

    private fun fallback(path: String): ZipItem {
        val file = File(path)
        return ZipItem(
            path = path, name = file.name.ifBlank { path.substringAfterLast('/') },
            size = file.length(), lastModified = file.lastModified(),
            volumeId = "primary", volumeName = "Internal storage", isSaf = path.startsWith("content://")
        )
    }
}
