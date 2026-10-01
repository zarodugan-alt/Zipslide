package com.example.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ZipSlideApplication
import com.example.data.AppSettings
import com.example.data.model.SortBy
import com.example.data.model.ViewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val settings: AppSettings = AppSettings(),
    val thumbnailCacheBytes: Long = 0L,
    val working: Boolean = false
)
sealed interface SettingsEvent {
    data class SetViewMode(val mode: ViewMode) : SettingsEvent
    data class SetSort(val sortBy: SortBy) : SettingsEvent
    data class SetShowFilenames(val value: Boolean) : SettingsEvent
    data class SetShowBadges(val value: Boolean) : SettingsEvent
    data class SetNaturalSort(val value: Boolean) : SettingsEvent
    data class SetIncludeSubfolders(val value: Boolean) : SettingsEvent
    data class SetShowHidden(val value: Boolean) : SettingsEvent
    data class SetSlideInterval(val seconds: Float) : SettingsEvent
    data class SetShuffle(val value: Boolean) : SettingsEvent
    data class SetLoop(val value: Boolean) : SettingsEvent
    data class SetFitToScreen(val value: Boolean) : SettingsEvent
    data class SetKeepScreenOn(val value: Boolean) : SettingsEvent
    data object ClearThumbnailCache : SettingsEvent
    data object RegenerateThumbnails : SettingsEvent
    data object Rescan : SettingsEvent
}

/** Repository-backed settings state; cache work and scans never execute in composables. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ZipSlideApplication).container
    private val local = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = combine(container.settings.settings, local) { settings, local ->
        local.copy(settings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    init { refreshCacheSize() }

    fun onEvent(event: SettingsEvent) = viewModelScope.launch {
        when (event) {
            is SettingsEvent.SetViewMode -> container.settings.updateViewMode(event.mode)
            is SettingsEvent.SetSort -> container.settings.updateSortBy(event.sortBy)
            is SettingsEvent.SetShowFilenames -> container.settings.updateShowFilenames(event.value)
            is SettingsEvent.SetShowBadges -> container.settings.updateShowBadges(event.value)
            is SettingsEvent.SetNaturalSort -> container.settings.updateNaturalSort(event.value)
            is SettingsEvent.SetIncludeSubfolders -> container.settings.updateIncludeSubfolders(event.value)
            is SettingsEvent.SetShowHidden -> container.settings.updateShowHidden(event.value)
            is SettingsEvent.SetSlideInterval -> container.settings.updateSlideshowInterval(event.seconds)
            is SettingsEvent.SetShuffle -> container.settings.updateSlideshowShuffle(event.value)
            is SettingsEvent.SetLoop -> container.settings.updateSlideshowLoop(event.value)
            is SettingsEvent.SetFitToScreen -> container.settings.updateSlideshowFitToScreen(event.value)
            is SettingsEvent.SetKeepScreenOn -> container.settings.updateSlideshowKeepScreenOn(event.value)
            SettingsEvent.ClearThumbnailCache -> runWorking { container.zips.clearThumbnailCache() }
            SettingsEvent.RegenerateThumbnails -> runWorking { container.zips.regenerateAll() }
            SettingsEvent.Rescan -> container.zips.triggerRescan()
        }
        refreshCacheSize()
    }

    private suspend fun runWorking(block: suspend () -> Unit) {
        local.update { it.copy(working = true) }
        try { block() } finally { local.update { it.copy(working = false) } }
    }

    private fun refreshCacheSize() = viewModelScope.launch {
        local.update { it.copy(thumbnailCacheBytes = container.zips.cacheSizeBytes()) }
    }
}
