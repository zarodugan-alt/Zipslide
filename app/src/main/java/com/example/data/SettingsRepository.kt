package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.SortBy
import com.example.data.model.ViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zipslide_settings")

data class AppSettings(
    val theme: String = "dark",
    val viewMode: ViewMode = ViewMode.MEDIUM,
    val showFilenames: Boolean = true,
    val showBadges: Boolean = true,
    val sortBy: SortBy = SortBy.NAME,
    val sortAscending: Boolean = true,
    val naturalSort: Boolean = true,
    val storageSource: String = "all", // "all", "internal", "sd"
    val customScanFolderUri: String? = null,
    val includeSubfolders: Boolean = true,
    val showHidden: Boolean = false,
    val slideshowInterval: Float = 3.0f,
    val slideshowShuffle: Boolean = false,
    val slideshowLoop: Boolean = true,
    val slideshowFitToScreen: Boolean = true,
    val slideshowKeepScreenOn: Boolean = true,
    val thumbnailPx: Int = 512,
    val onboardingCompleted: Boolean = false
)

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME = stringPreferencesKey("theme")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val SHOW_FILENAMES = booleanPreferencesKey("show_filenames")
        val SHOW_BADGES = booleanPreferencesKey("show_badges")
        val SORT_BY = stringPreferencesKey("sort_by")
        val SORT_ASCENDING = booleanPreferencesKey("sort_ascending")
        val NATURAL_SORT = booleanPreferencesKey("natural_sort")
        val STORAGE_SOURCE = stringPreferencesKey("storage_source")
        val CUSTOM_SCAN_FOLDER = stringPreferencesKey("custom_scan_folder")
        val INCLUDE_SUBFOLDERS = booleanPreferencesKey("include_subfolders")
        val SHOW_HIDDEN = booleanPreferencesKey("show_hidden")
        val SLIDESHOW_INTERVAL = floatPreferencesKey("slideshow_interval")
        val SLIDESHOW_SHUFFLE = booleanPreferencesKey("slideshow_shuffle")
        val SLIDESHOW_LOOP = booleanPreferencesKey("slideshow_loop")
        val SLIDESHOW_FIT_TO_SCREEN = booleanPreferencesKey("slideshow_fit_to_screen")
        val SLIDESHOW_KEEP_SCREEN_ON = booleanPreferencesKey("slideshow_keep_screen_on")
        val THUMBNAIL_PX = intPreferencesKey("thumbnail_px")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            theme = prefs[PreferencesKeys.THEME] ?: "dark",
            viewMode = runCatching {
                ViewMode.valueOf(prefs[PreferencesKeys.VIEW_MODE] ?: ViewMode.MEDIUM.name)
            }.getOrDefault(ViewMode.MEDIUM),
            showFilenames = prefs[PreferencesKeys.SHOW_FILENAMES] ?: true,
            showBadges = prefs[PreferencesKeys.SHOW_BADGES] ?: true,
            sortBy = runCatching {
                SortBy.valueOf(prefs[PreferencesKeys.SORT_BY] ?: SortBy.NAME.name)
            }.getOrDefault(SortBy.NAME),
            sortAscending = prefs[PreferencesKeys.SORT_ASCENDING] ?: true,
            naturalSort = prefs[PreferencesKeys.NATURAL_SORT] ?: true,
            storageSource = prefs[PreferencesKeys.STORAGE_SOURCE] ?: "all",
            customScanFolderUri = prefs[PreferencesKeys.CUSTOM_SCAN_FOLDER],
            includeSubfolders = prefs[PreferencesKeys.INCLUDE_SUBFOLDERS] ?: true,
            showHidden = prefs[PreferencesKeys.SHOW_HIDDEN] ?: false,
            slideshowInterval = prefs[PreferencesKeys.SLIDESHOW_INTERVAL] ?: 3.0f,
            slideshowShuffle = prefs[PreferencesKeys.SLIDESHOW_SHUFFLE] ?: false,
            slideshowLoop = prefs[PreferencesKeys.SLIDESHOW_LOOP] ?: true,
            slideshowFitToScreen = prefs[PreferencesKeys.SLIDESHOW_FIT_TO_SCREEN] ?: true,
            slideshowKeepScreenOn = prefs[PreferencesKeys.SLIDESHOW_KEEP_SCREEN_ON] ?: true,
            thumbnailPx = prefs[PreferencesKeys.THUMBNAIL_PX] ?: 512,
            onboardingCompleted = prefs[PreferencesKeys.ONBOARDING_COMPLETED] ?: false
        )
    }

    suspend fun updateTheme(theme: String) {
        context.dataStore.edit { it[PreferencesKeys.THEME] = theme }
    }

    suspend fun updateViewMode(mode: ViewMode) {
        context.dataStore.edit { it[PreferencesKeys.VIEW_MODE] = mode.name }
    }

    suspend fun updateShowFilenames(show: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SHOW_FILENAMES] = show }
    }

    suspend fun updateShowBadges(show: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SHOW_BADGES] = show }
    }

    suspend fun updateSortBy(sortBy: SortBy) {
        context.dataStore.edit { it[PreferencesKeys.SORT_BY] = sortBy.name }
    }

    suspend fun updateSortAscending(ascending: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SORT_ASCENDING] = ascending }
    }

    suspend fun updateNaturalSort(natural: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.NATURAL_SORT] = natural }
    }

    suspend fun updateStorageSource(source: String) {
        context.dataStore.edit { it[PreferencesKeys.STORAGE_SOURCE] = source }
    }

    suspend fun updateCustomScanFolder(uriString: String?) {
        context.dataStore.edit {
            if (uriString != null) {
                it[PreferencesKeys.CUSTOM_SCAN_FOLDER] = uriString
            } else {
                it.remove(PreferencesKeys.CUSTOM_SCAN_FOLDER)
            }
        }
    }

    suspend fun updateIncludeSubfolders(include: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.INCLUDE_SUBFOLDERS] = include }
    }

    suspend fun updateShowHidden(show: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SHOW_HIDDEN] = show }
    }

    suspend fun updateSlideshowInterval(interval: Float) {
        context.dataStore.edit { it[PreferencesKeys.SLIDESHOW_INTERVAL] = interval }
    }

    suspend fun updateSlideshowShuffle(shuffle: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SLIDESHOW_SHUFFLE] = shuffle }
    }

    suspend fun updateSlideshowLoop(loop: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SLIDESHOW_LOOP] = loop }
    }

    suspend fun updateSlideshowFitToScreen(fit: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SLIDESHOW_FIT_TO_SCREEN] = fit }
    }

    suspend fun updateSlideshowKeepScreenOn(keep: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SLIDESHOW_KEEP_SCREEN_ON] = keep }
    }

    suspend fun updateThumbnailPx(px: Int) {
        context.dataStore.edit { it[PreferencesKeys.THUMBNAIL_PX] = px }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.ONBOARDING_COMPLETED] = completed }
    }
}
