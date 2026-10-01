package com.example.ui.browser

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ZipSlideApplication
import com.example.data.AppSettings
import com.example.data.ExtractProgress
import com.example.data.ScanProgress
import com.example.data.ZipRepository
import com.example.data.toUserMessage
import com.example.data.model.BrowserFilter
import com.example.data.model.SortBy
import com.example.data.model.ZipItem
import com.example.util.naturalKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class BrowserState(
    val items: List<ZipItem> = emptyList(),
    val filtered: List<ZipItem> = emptyList(),
    val scanProgress: ScanProgress = ScanProgress.Idle,
    val settings: AppSettings = AppSettings(),
    val selectedPaths: Set<String> = emptySet(),
    val selectionMode: Boolean = false,
    /** Current field value; [appliedSearchQuery] changes after the 200 ms debounce. */
    val searchQuery: String = "",
    val appliedSearchQuery: String = "",
    val activeFilter: BrowserFilter = BrowserFilter.ALL,
    val menuTarget: ZipItem? = null,
    val sortSeed: Long = System.currentTimeMillis(),
    val operationProgress: ExtractProgress? = null,
    val error: String? = null
)

sealed interface BrowserEvent {
    data class CardTap(val item: ZipItem) : BrowserEvent
    data class CardLongPress(val item: ZipItem) : BrowserEvent
    data object SheetDismiss : BrowserEvent
    data class ToggleFavorite(val item: ZipItem) : BrowserEvent
    data class Rename(val item: ZipItem, val newName: String) : BrowserEvent
    data class Delete(val item: ZipItem) : BrowserEvent
    data class Copy(val item: ZipItem, val destination: Uri) : BrowserEvent
    data class Move(val item: ZipItem, val destination: Uri) : BrowserEvent
    data class Extract(val item: ZipItem, val destination: Uri) : BrowserEvent
    data class SearchChange(val query: String) : BrowserEvent
    data class FilterChange(val filter: BrowserFilter) : BrowserEvent
    data class SortChange(val sortBy: SortBy) : BrowserEvent
    data class SelectToggle(val path: String) : BrowserEvent
    data object SelectAll : BrowserEvent
    data object ClearSelection : BrowserEvent
    data object Rescan : BrowserEvent
}

/** Browser state owner; composables only render this immutable state and dispatch events. */
class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ZipSlideApplication).container
    private val zips: ZipRepository = container.zips
    private val settingsRepository = container.settings
    private val mutable = MutableStateFlow(BrowserState())
    private var searchJob: Job? = null

    private val repositoryState = combine(zips.observeCached(), settingsRepository.settings) { items, settings ->
        items to settings
    }.combine(mutable) { (items, settings), local ->
        val filtered = items
            .filter { item ->
                when (local.activeFilter) {
                    BrowserFilter.ALL -> true
                    BrowserFilter.FAVORITES -> item.isFavorite
                    BrowserFilter.MISSING_COVER -> !item.hasCover && item.imageCount > 0
                }
            }
            .filter { item ->
                local.appliedSearchQuery.isBlank() || item.name.contains(local.appliedSearchQuery, true) || item.path.contains(local.appliedSearchQuery, true)
            }
            .sortedFor(settings, local.sortSeed)
        local.copy(items = items, filtered = filtered, settings = settings)
    }

    val state: StateFlow<BrowserState> = combine(repositoryState, zips.scanProgress) { data, progress ->
        data.copy(scanProgress = progress)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowserState())

    fun onEvent(event: BrowserEvent) {
        when (event) {
            is BrowserEvent.CardLongPress -> mutable.update { it.copy(menuTarget = event.item) }
            BrowserEvent.SheetDismiss -> mutable.update { it.copy(menuTarget = null) }
            is BrowserEvent.ToggleFavorite -> viewModelScope.launch { zips.toggleFavorite(event.item.path) }
            is BrowserEvent.Rename -> viewModelScope.launch { zips.rename(event.item, event.newName).onFailure(::setError) }
            is BrowserEvent.Delete -> viewModelScope.launch { zips.delete(event.item).onFailure(::setError) }
            is BrowserEvent.Copy -> viewModelScope.launch { zips.copyTo(event.item, event.destination).onFailure(::setError) }
            is BrowserEvent.Move -> viewModelScope.launch { zips.moveTo(event.item, event.destination).onFailure(::setError) }
            is BrowserEvent.Extract -> viewModelScope.launch {
                zips.extractAll(event.item, event.destination).collect { progress ->
                    mutable.update { it.copy(operationProgress = progress) }
                }
                mutable.update { it.copy(operationProgress = null) }
            }
            is BrowserEvent.SearchChange -> {
                // Keep typing responsive while applying results only after the debounce interval.
                mutable.update { it.copy(searchQuery = event.query) }
                searchJob?.cancel()
                searchJob = viewModelScope.launch {
                    delay(200)
                    mutable.update { it.copy(appliedSearchQuery = event.query) }
                }
            }
            is BrowserEvent.FilterChange -> mutable.update { it.copy(activeFilter = event.filter) }
            is BrowserEvent.SortChange -> viewModelScope.launch {
                settingsRepository.update { it.copy(sortBy = event.sortBy) }
                // Tapping Random intentionally gives a new stable session order.
                if (event.sortBy == SortBy.RANDOM) mutable.update { it.copy(sortSeed = Random.nextLong()) }
            }
            is BrowserEvent.SelectToggle -> mutable.update { old ->
                val next = old.selectedPaths.toMutableSet()
                if (!next.add(event.path)) next.remove(event.path)
                old.copy(selectedPaths = next, selectionMode = next.isNotEmpty())
            }
            BrowserEvent.SelectAll -> mutable.update { old -> old.copy(selectedPaths = old.filtered.mapTo(linkedSetOf()) { it.path }, selectionMode = old.filtered.isNotEmpty()) }
            BrowserEvent.ClearSelection -> mutable.update { it.copy(selectedPaths = emptySet(), selectionMode = false) }
            BrowserEvent.Rescan -> {
                mutable.update { it.copy(sortSeed = Random.nextLong()) }
                zips.triggerRescan()
            }
            is BrowserEvent.CardTap -> Unit // Navigation remains a UI concern.
        }
    }

    private fun setError(error: Throwable) = mutable.update { it.copy(error = error.toUserMessage()) }

    private fun List<ZipItem>.sortedFor(settings: AppSettings, seed: Long): List<ZipItem> {
        if (settings.sortBy == SortBy.RANDOM) return sortedBy { item -> item.path.hashCode().toLong() xor seed }
        val comparator = compareBy<ZipItem> { item ->
            when (settings.sortBy) {
                SortBy.NAME -> if (settings.naturalSort) naturalKey(item.name) else item.name.lowercase()
                SortBy.DATE_MODIFIED, SortBy.DATE_CREATED -> item.lastModified.toString().padStart(20, '0')
                SortBy.SIZE -> item.size.toString().padStart(20, '0')
                SortBy.IMAGE_COUNT -> item.imageCount.toString().padStart(10, '0')
                SortBy.RANDOM -> item.path
            }
        }
        return if (settings.ascending) sortedWith(comparator) else sortedWith(comparator.reversed())
    }
}
