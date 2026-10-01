package com.example.ui.storage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ZipSlideApplication
import com.example.data.model.StorageVolumeInfo
import com.example.data.model.ZipItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StorageOverviewState(
    val volumes: List<StorageVolumeInfo> = emptyList(),
    val zips: List<ZipItem> = emptyList()
)
data class VolumeDiagnosticsState(
    val volumes: List<StorageVolumeInfo> = emptyList(),
    val zips: List<ZipItem> = emptyList()
)

/** Aggregates mounted-volume and archive cache flows for the storage overview. */
class StorageOverviewViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ZipSlideApplication).container
    val state: StateFlow<StorageOverviewState> = combine(
        container.volumes.volumes,
        container.zips.observeCached()
    ) { volumes, zips -> StorageOverviewState(volumes, zips) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StorageOverviewState())
}

/** Same repository data, intentionally separate state owner for diagnostics navigation. */
class VolumeDiagnosticsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ZipSlideApplication).container
    val state: StateFlow<VolumeDiagnosticsState> = combine(
        container.volumes.volumes,
        container.zips.observeCached()
    ) { volumes, zips -> VolumeDiagnosticsState(volumes, zips) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VolumeDiagnosticsState())
}
