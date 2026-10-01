package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.SlideTransition
import com.example.data.model.SortBy
import com.example.data.model.ThemeMode
import com.example.data.model.ViewMode
import com.example.data.model.VolumeFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val viewMode: ViewMode = ViewMode.GRID_MEDIUM,
    val showFilenames: Boolean = true,
    val showBadges: Boolean = true,
    val sortBy: SortBy = SortBy.NAME,
    val ascending: Boolean = true,
    val naturalSort: Boolean = true,
    val volumeFilter: VolumeFilter = VolumeFilter.ALL,
    val customScanRoots: Set<String> = emptySet(),
    val includeSubfolders: Boolean = true,
    val showHidden: Boolean = false,
    val slideIntervalMs: Int = 3000,
    val slideShuffle: Boolean = false,
    val slideLoop: Boolean = true,
    val slideFitToScreen: Boolean = true,
    val slideKeepScreenOn: Boolean = true,
    val slideTransition: SlideTransition = SlideTransition.FADE,
    val slideTransitionMs: Int = 520,
    val slideTapZones: Boolean = true,
    val slideProgressLine: Boolean = true,
    val slideHaptics: Boolean = true,
    val slideKenBurns: Boolean = true,
    val slideHighQuality: Boolean = true,
    val slideImmersive: Boolean = true,
    /** Only surface archives whose cover resolves through the strict `1.x` rule. */
    val onlyNumberedCovers: Boolean = true,
    val thumbnailPx: Int = 512,
    val onboardingCompleted: Boolean = false
) {
    // Transitional properties keep the built UI shell source-compatible while all persisted
    // values use the strongly typed contract above.
    val sortAscending get() = ascending
    val storageSource get() = volumeFilter.name.lowercase()
    val customScanFolderUri get() = customScanRoots.firstOrNull()
    val slideshowInterval get() = slideIntervalMs / 1000f
    val slideshowShuffle get() = slideShuffle
    val slideshowLoop get() = slideLoop
    val slideshowFitToScreen get() = slideFitToScreen
    val slideshowKeepScreenOn get() = slideKeepScreenOn
}

class SettingsRepository(private val context: Context) {
    private object Key {
        val theme = stringPreferencesKey("theme")
        val viewMode = stringPreferencesKey("view_mode")
        val showFilenames = booleanPreferencesKey("show_filenames")
        val showBadges = booleanPreferencesKey("show_badges")
        val sortBy = stringPreferencesKey("sort_by")
        val ascending = booleanPreferencesKey("ascending")
        val naturalSort = booleanPreferencesKey("natural_sort")
        val volumeFilter = stringPreferencesKey("volume_filter")
        val customScanRoots = stringSetPreferencesKey("custom_scan_roots")
        val includeSubfolders = booleanPreferencesKey("include_subfolders")
        val showHidden = booleanPreferencesKey("show_hidden")
        val slideIntervalMs = intPreferencesKey("slide_interval_ms")
        val slideShuffle = booleanPreferencesKey("slide_shuffle")
        val slideLoop = booleanPreferencesKey("slide_loop")
        val slideFitToScreen = booleanPreferencesKey("slide_fit_to_screen")
        val slideKeepScreenOn = booleanPreferencesKey("slide_keep_screen_on")
        val slideTransition = stringPreferencesKey("slide_transition")
        val slideTransitionMs = intPreferencesKey("slide_transition_ms")
        val slideTapZones = booleanPreferencesKey("slide_tap_zones")
        val slideProgressLine = booleanPreferencesKey("slide_progress_line")
        val slideHaptics = booleanPreferencesKey("slide_haptics")
        val slideKenBurns = booleanPreferencesKey("slide_ken_burns")
        val slideHighQuality = booleanPreferencesKey("slide_high_quality")
        val slideImmersive = booleanPreferencesKey("slide_immersive")
        val onlyNumberedCovers = booleanPreferencesKey("only_numbered_covers")
        val thumbnailPx = intPreferencesKey("thumbnail_px")
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .map(::decode)
        .distinctUntilChanged()
    val settingsFlow: Flow<AppSettings> = settings

    private fun decode(prefs: Preferences) = AppSettings(
        theme = prefs.enum(Key.theme, ThemeMode.SYSTEM),
        viewMode = prefs.enum(Key.viewMode, ViewMode.GRID_MEDIUM),
        showFilenames = prefs[Key.showFilenames] ?: true,
        showBadges = prefs[Key.showBadges] ?: true,
        sortBy = prefs.enum(Key.sortBy, SortBy.NAME),
        ascending = prefs[Key.ascending] ?: true,
        naturalSort = prefs[Key.naturalSort] ?: true,
        volumeFilter = prefs.enum(Key.volumeFilter, VolumeFilter.ALL),
        customScanRoots = prefs[Key.customScanRoots] ?: emptySet(),
        includeSubfolders = prefs[Key.includeSubfolders] ?: true,
        showHidden = prefs[Key.showHidden] ?: false,
        slideIntervalMs = prefs[Key.slideIntervalMs] ?: 3000,
        slideShuffle = prefs[Key.slideShuffle] ?: false,
        slideLoop = prefs[Key.slideLoop] ?: true,
        slideFitToScreen = prefs[Key.slideFitToScreen] ?: true,
        slideKeepScreenOn = prefs[Key.slideKeepScreenOn] ?: true,
        slideTransition = prefs.enum(Key.slideTransition, SlideTransition.FADE),
        slideTransitionMs = (prefs[Key.slideTransitionMs] ?: 520).coerceIn(160, 1200),
        slideTapZones = prefs[Key.slideTapZones] ?: true,
        slideProgressLine = prefs[Key.slideProgressLine] ?: true,
        slideHaptics = prefs[Key.slideHaptics] ?: true,
        slideKenBurns = prefs[Key.slideKenBurns] ?: true,
        slideHighQuality = prefs[Key.slideHighQuality] ?: true,
        slideImmersive = prefs[Key.slideImmersive] ?: true,
        onlyNumberedCovers = prefs[Key.onlyNumberedCovers] ?: true,
        thumbnailPx = prefs[Key.thumbnailPx] ?: 512,
        onboardingCompleted = prefs[Key.onboardingCompleted] ?: false
    )

    private inline fun <reified T : Enum<T>> Preferences.enum(
        key: Preferences.Key<String>,
        default: T
    ): T = runCatching { enumValueOf<T>(this[key] ?: default.name) }.getOrDefault(default)

    /** Atomically persists a complete immutable state transformation. */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(settings.first())
        context.dataStore.edit { prefs -> write(prefs, next) }
    }

    private fun write(prefs: MutablePreferences, value: AppSettings) {
        prefs[Key.theme] = value.theme.name
        prefs[Key.viewMode] = value.viewMode.name
        prefs[Key.showFilenames] = value.showFilenames
        prefs[Key.showBadges] = value.showBadges
        prefs[Key.sortBy] = value.sortBy.name
        prefs[Key.ascending] = value.ascending
        prefs[Key.naturalSort] = value.naturalSort
        prefs[Key.volumeFilter] = value.volumeFilter.name
        prefs[Key.customScanRoots] = value.customScanRoots
        prefs[Key.includeSubfolders] = value.includeSubfolders
        prefs[Key.showHidden] = value.showHidden
        prefs[Key.slideIntervalMs] = value.slideIntervalMs
        prefs[Key.slideShuffle] = value.slideShuffle
        prefs[Key.slideLoop] = value.slideLoop
        prefs[Key.slideFitToScreen] = value.slideFitToScreen
        prefs[Key.slideKeepScreenOn] = value.slideKeepScreenOn
        prefs[Key.slideTransition] = value.slideTransition.name
        prefs[Key.slideTransitionMs] = value.slideTransitionMs
        prefs[Key.slideTapZones] = value.slideTapZones
        prefs[Key.slideProgressLine] = value.slideProgressLine
        prefs[Key.slideHaptics] = value.slideHaptics
        prefs[Key.slideKenBurns] = value.slideKenBurns
        prefs[Key.slideHighQuality] = value.slideHighQuality
        prefs[Key.slideImmersive] = value.slideImmersive
        prefs[Key.onlyNumberedCovers] = value.onlyNumberedCovers
        prefs[Key.thumbnailPx] = value.thumbnailPx
        prefs[Key.onboardingCompleted] = value.onboardingCompleted
    }

    suspend fun updateTheme(theme: String) = update { it.copy(theme = theme.toThemeMode()) }
    suspend fun updateViewMode(mode: ViewMode) = update { it.copy(viewMode = mode) }
    suspend fun updateShowFilenames(show: Boolean) = update { it.copy(showFilenames = show) }
    suspend fun updateShowBadges(show: Boolean) = update { it.copy(showBadges = show) }
    suspend fun updateSortBy(sortBy: SortBy) = update { it.copy(sortBy = sortBy) }
    suspend fun updateSortAscending(ascending: Boolean) = update { it.copy(ascending = ascending) }
    suspend fun updateNaturalSort(natural: Boolean) = update { it.copy(naturalSort = natural) }
    suspend fun updateStorageSource(source: String) = update { it.copy(volumeFilter = source.toVolumeFilter()) }
    suspend fun updateIncludeSubfolders(include: Boolean) = update { it.copy(includeSubfolders = include) }
    suspend fun updateShowHidden(show: Boolean) = update { it.copy(showHidden = show) }
    suspend fun updateSlideshowInterval(interval: Float) = update { it.copy(slideIntervalMs = (interval * 1000).toInt()) }
    suspend fun updateSlideshowShuffle(shuffle: Boolean) = update { it.copy(slideShuffle = shuffle) }
    suspend fun updateSlideshowLoop(loop: Boolean) = update { it.copy(slideLoop = loop) }
    suspend fun updateSlideshowFitToScreen(fit: Boolean) = update { it.copy(slideFitToScreen = fit) }
    suspend fun updateSlideshowKeepScreenOn(keep: Boolean) = update { it.copy(slideKeepScreenOn = keep) }
    suspend fun updateSlideTransition(transition: SlideTransition) = update { it.copy(slideTransition = transition) }
    suspend fun updateSlideTransitionMs(duration: Int) = update { it.copy(slideTransitionMs = duration.coerceIn(160, 1200)) }
    suspend fun updateSlideTapZones(enabled: Boolean) = update { it.copy(slideTapZones = enabled) }
    suspend fun updateSlideProgressLine(enabled: Boolean) = update { it.copy(slideProgressLine = enabled) }
    suspend fun updateSlideHaptics(enabled: Boolean) = update { it.copy(slideHaptics = enabled) }
    suspend fun updateSlideKenBurns(enabled: Boolean) = update { it.copy(slideKenBurns = enabled) }
    suspend fun updateSlideHighQuality(enabled: Boolean) = update { it.copy(slideHighQuality = enabled) }
    suspend fun updateSlideImmersive(enabled: Boolean) = update { it.copy(slideImmersive = enabled) }
    suspend fun updateOnlyNumberedCovers(enabled: Boolean) = update { it.copy(onlyNumberedCovers = enabled) }
    suspend fun updateThumbnailPx(px: Int) = update { it.copy(thumbnailPx = px.coerceIn(128, 2048)) }
    suspend fun setOnboardingCompleted(completed: Boolean) = update { it.copy(onboardingCompleted = completed) }

    suspend fun addCustomScanRoot(uri: String) = update { it.copy(customScanRoots = it.customScanRoots + uri) }
    suspend fun removeCustomScanRoot(uri: String) = update { it.copy(customScanRoots = it.customScanRoots - uri) }
    suspend fun updateCustomScanFolder(uriString: String?) = update {
        it.copy(customScanRoots = uriString?.let(::setOf) ?: emptySet())
    }

    private fun String.toThemeMode() = when (lowercase()) {
        "light" -> ThemeMode.LIGHT
        "dark" -> ThemeMode.DARK
        else -> ThemeMode.SYSTEM
    }

    private fun String.toVolumeFilter() = when (lowercase()) {
        "internal" -> VolumeFilter.INTERNAL
        "sd" -> VolumeFilter.SD
        "usb" -> VolumeFilter.USB
        else -> VolumeFilter.ALL
    }
}
