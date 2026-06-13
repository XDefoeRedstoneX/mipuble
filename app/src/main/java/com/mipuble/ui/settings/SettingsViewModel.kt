package com.mipuble.ui.settings

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipuble.data.remote.AuthResult
import com.mipuble.data.remote.DriveAuthProvider
import com.mipuble.data.remote.NeedConsentException
import com.mipuble.domain.model.PageTurnMode
import com.mipuble.domain.model.ReaderFont
import com.mipuble.domain.model.ReaderPreferences
import com.mipuble.domain.model.ReaderSettingsBounds
import com.mipuble.domain.model.ReaderTheme
import com.mipuble.domain.model.UploadProgress
import com.mipuble.domain.repository.ReaderPreferencesRepository
import com.mipuble.domain.sort.BookSortOption
import com.mipuble.domain.usecase.CheckRemoteAvailabilityUseCase
import com.mipuble.domain.usecase.EvictBookUseCase
import com.mipuble.domain.usecase.ObserveLibraryUseCase
import com.mipuble.domain.usecase.ObserveUploadsUseCase
import com.mipuble.domain.usecase.ResetLibraryToDriveUseCase
import com.mipuble.domain.usecase.SyncRemoteLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: ReaderPreferences = ReaderPreferences(),
    val downloadedCount: Int = 0,
    val evictableIds: List<Long> = emptyList(),
    val localOnlyCount: Int = 0,
    val remoteAvailable: Boolean = false,
    val isSyncing: Boolean = false,
    val isResetting: Boolean = false,
    val upload: UploadProgress? = null,
    val pendingConsent: PendingIntent? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeLibrary: ObserveLibraryUseCase,
    observeUploads: ObserveUploadsUseCase,
    private val preferencesRepository: ReaderPreferencesRepository,
    private val checkRemoteAvailability: CheckRemoteAvailabilityUseCase,
    private val syncRemoteLibrary: SyncRemoteLibraryUseCase,
    private val resetLibraryToDrive: ResetLibraryToDriveUseCase,
    private val evictBook: EvictBookUseCase,
    private val driveAuthProvider: DriveAuthProvider,
) : ViewModel() {

    private val remoteAvailable = MutableStateFlow(false)
    private val isSyncing = MutableStateFlow(false)
    private val isResetting = MutableStateFlow(false)
    private val pendingConsent = MutableStateFlow<PendingIntent?>(null)

    /** Re-run after the user grants Drive consent (sync or reset). */
    private var afterConsent: (() -> Unit)? = null

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages

    init {
        viewModelScope.launch { remoteAvailable.value = checkRemoteAvailability() }
    }

    private val library = observeLibrary(BookSortOption.TITLE_NATURAL)
    private val status = combine(
        remoteAvailable,
        isSyncing,
        isResetting,
        observeUploads(),
        pendingConsent,
    ) { available, syncing, resetting, upload, consent ->
        Status(available, syncing, resetting, upload, consent)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferences,
        library,
        status,
    ) { prefs, books, s ->
        SettingsUiState(
            preferences = prefs,
            downloadedCount = books.count { it.isDownloaded },
            evictableIds = books.filter { it.canEvict }.map { it.id },
            localOnlyCount = books.count { !it.isRemote && it.isDownloaded },
            remoteAvailable = s.available,
            isSyncing = s.syncing,
            isResetting = s.resetting,
            upload = s.upload,
            pendingConsent = s.consent,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    private data class Status(
        val available: Boolean,
        val syncing: Boolean,
        val resetting: Boolean,
        val upload: UploadProgress?,
        val consent: PendingIntent?,
    )

    fun onThemeSelected(theme: ReaderTheme) {
        viewModelScope.launch { preferencesRepository.setTheme(theme) }
    }

    fun onFontSelected(font: ReaderFont) {
        viewModelScope.launch { preferencesRepository.setFont(font) }
    }

    fun onPageTurnModeSelected(mode: PageTurnMode) {
        viewModelScope.launch { preferencesRepository.setPageTurnMode(mode) }
    }

    fun onStepFont(direction: Int) {
        viewModelScope.launch {
            preferencesRepository.setFontScalePercent(
                ReaderSettingsBounds.stepFont(uiState.value.preferences.fontScalePercent, direction),
            )
        }
    }

    fun onStepLineSpacing(direction: Int) {
        viewModelScope.launch {
            preferencesRepository.setLineSpacingPercent(
                ReaderSettingsBounds.stepLineSpacing(uiState.value.preferences.lineSpacingPercent, direction),
            )
        }
    }

    fun onSyncNow() {
        if (isSyncing.value) return
        viewModelScope.launch {
            isSyncing.value = true
            syncRemoteLibrary()
                .onSuccess { added ->
                    _messages.update {
                        if (added > 0) "Synced — $added new book(s)." else "Library is up to date."
                    }
                }
                .onFailure { handleFailure(it, retry = ::onSyncNow, action = "Sync") }
            isSyncing.value = false
        }
    }

    fun onResetToDrive(uploadLocalFirst: Boolean) {
        if (isResetting.value) return
        viewModelScope.launch {
            isResetting.value = true
            resetLibraryToDrive(uploadLocalFirst)
                .onSuccess { count -> _messages.update { "Library now mirrors Drive ($count book(s))." } }
                .onFailure { handleFailure(it, retry = { onResetToDrive(uploadLocalFirst) }, action = "Reset") }
            isResetting.value = false
        }
    }

    /** Frees space by evicting every downloaded book that has a remote copy. */
    fun onRemoveAllDownloads() {
        viewModelScope.launch {
            val ids = uiState.value.evictableIds
            ids.forEach { evictBook(it) }
            _messages.update {
                if (ids.isEmpty()) "Nothing to remove." else "Removed ${ids.size} download(s)."
            }
        }
    }

    private fun handleFailure(e: Throwable, retry: () -> Unit, action: String) {
        val consent = e as? NeedConsentException ?: e.cause as? NeedConsentException
        if (consent != null) {
            afterConsent = retry
            pendingConsent.value = consent.intent
        } else {
            _messages.update { e.message ?: "$action failed." }
        }
    }

    fun onConsentShown() {
        pendingConsent.value = null
    }

    fun onConsentResult(data: Intent?) {
        viewModelScope.launch {
            if (driveAuthProvider.completeConsent(data) is AuthResult.Success) {
                remoteAvailable.value = true
                afterConsent?.invoke()
            } else {
                _messages.update { "Google Drive permission wasn't granted." }
            }
            afterConsent = null
        }
    }

    fun onConsentCanceled(resultCode: Int) {
        afterConsent = null
        _messages.update { "Consent screen closed without granting (code $resultCode)." }
    }

    fun onConsentLaunchFailed(message: String?) {
        afterConsent = null
        _messages.update { "Couldn't open the Google consent screen: ${message ?: "unknown error"}" }
    }

    fun onMessageShown() {
        _messages.update { null }
    }
}
