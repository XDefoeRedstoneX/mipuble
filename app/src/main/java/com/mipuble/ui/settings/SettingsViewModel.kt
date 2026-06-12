package com.mipuble.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipuble.domain.model.ReaderPreferences
import com.mipuble.domain.model.ReaderSettingsBounds
import com.mipuble.domain.model.ReaderTheme
import com.mipuble.domain.repository.ReaderPreferencesRepository
import com.mipuble.domain.sort.BookSortOption
import com.mipuble.domain.usecase.CheckRemoteAvailabilityUseCase
import com.mipuble.domain.usecase.EvictBookUseCase
import com.mipuble.domain.usecase.ObserveLibraryUseCase
import com.mipuble.domain.usecase.SyncRemoteLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: ReaderPreferences = ReaderPreferences(),
    /** Books whose bytes are on the device. */
    val downloadedCount: Int = 0,
    /** Downloaded books that can be evicted (remote copies exist). */
    val evictableIds: List<Long> = emptyList(),
    /** Whether a remote library source is reachable/configured. */
    val remoteAvailable: Boolean = false,
    val isSyncing: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeLibrary: ObserveLibraryUseCase,
    private val preferencesRepository: ReaderPreferencesRepository,
    private val checkRemoteAvailability: CheckRemoteAvailabilityUseCase,
    private val syncRemoteLibrary: SyncRemoteLibraryUseCase,
    private val evictBook: EvictBookUseCase,
) : ViewModel() {

    private val remoteAvailable = MutableStateFlow(false)
    private val isSyncing = MutableStateFlow(false)

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages

    init {
        viewModelScope.launch { remoteAvailable.value = checkRemoteAvailability() }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferences,
        observeLibrary(BookSortOption.TITLE_NATURAL),
        remoteAvailable,
        isSyncing,
    ) { prefs, books, remote, syncing ->
        SettingsUiState(
            preferences = prefs,
            downloadedCount = books.count { it.isDownloaded },
            evictableIds = books.filter { it.canEvict }.map { it.id },
            remoteAvailable = remote,
            isSyncing = syncing,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun onThemeSelected(theme: ReaderTheme) {
        viewModelScope.launch { preferencesRepository.setTheme(theme) }
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
                .onFailure { e -> _messages.update { e.message ?: "Sync failed." } }
            isSyncing.value = false
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

    fun onMessageShown() {
        _messages.update { null }
    }
}
