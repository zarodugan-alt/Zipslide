package com.example.ui.contents

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ZipSlideApplication
import com.example.data.ZipSlideError
import com.example.data.model.ZipEntryItem
import com.example.data.model.ZipItem
import com.example.data.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ZipContentsState(
    val zip: ZipItem? = null,
    val entries: List<ZipEntryItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null
)
sealed interface ZipContentsEvent {
    data class Load(val path: String) : ZipContentsEvent
}

/** Loads one archive's real entry metadata through the repository. */
class ZipContentsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as ZipSlideApplication).container.zips
    private val mutable = MutableStateFlow(ZipContentsState())
    val state: StateFlow<ZipContentsState> = mutable.asStateFlow()

    fun onEvent(event: ZipContentsEvent) = when (event) {
        is ZipContentsEvent.Load -> viewModelScope.launch {
            mutable.value = ZipContentsState(loading = true)
            runCatching {
                val zip = repository.findZipItem(event.path) ?: fallback(event.path)
                zip to repository.getZipEntries(zip)
            }.onSuccess { (zip, entries) ->
                mutable.value = ZipContentsState(zip = zip, entries = entries, loading = false)
            }.onFailure { error ->
                mutable.value = ZipContentsState(loading = false, error = error.toUserMessage())
            }
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
