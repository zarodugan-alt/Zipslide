package com.example.ui.onboarding

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ZipSlideApplication
import com.example.data.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OnboardingState(val settings: AppSettings = AppSettings())
sealed interface OnboardingEvent {
    data object AccessGranted : OnboardingEvent
    data class FolderChosen(val uri: Uri) : OnboardingEvent
}

/** Persists completed onboarding and SAF roots; permission intents remain in the UI boundary. */
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as ZipSlideApplication).container
    val state: StateFlow<OnboardingState> = container.settings.settings
        .map(::OnboardingState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingState())

    fun onEvent(event: OnboardingEvent) = viewModelScope.launch {
        when (event) {
            OnboardingEvent.AccessGranted -> container.settings.setOnboardingCompleted(true)
            is OnboardingEvent.FolderChosen -> {
                container.settings.updateCustomScanFolder(event.uri.toString())
                container.settings.setOnboardingCompleted(true)
            }
        }
        container.zips.triggerRescan()
    }
}
