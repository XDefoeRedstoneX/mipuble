package com.mipuble.ui.library

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipuble.data.remote.AuthResult
import com.mipuble.data.remote.DriveAuthProvider
import com.mipuble.data.remote.NeedConsentException
import com.mipuble.domain.model.Book
import com.mipuble.domain.model.Category
import com.mipuble.domain.model.DownloadStatus
import com.mipuble.domain.sort.BookSortOption
import com.mipuble.domain.usecase.AssignBookCategoryUseCase
import com.mipuble.domain.usecase.CreateCategoryUseCase
import com.mipuble.domain.usecase.DeleteCategoryUseCase
import com.mipuble.domain.usecase.DownloadBookUseCase
import com.mipuble.domain.usecase.EvictBookUseCase
import com.mipuble.domain.usecase.ImportEpubUseCase
import com.mipuble.domain.usecase.ObserveCategoriesUseCase
import com.mipuble.domain.usecase.ObserveDownloadsUseCase
import com.mipuble.domain.usecase.ObserveLibraryUseCase
import com.mipuble.domain.usecase.SaveCustomOrderUseCase
import com.mipuble.domain.usecase.SyncRemoteLibraryUseCase
import com.mipuble.domain.usecase.UpdateCategoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val isLoading: Boolean = true,
    val books: List<Book> = emptyList(),
    val sortOption: BookSortOption = BookSortOption.TITLE_NATURAL,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null,
    val downloads: Map<Long, DownloadStatus> = emptyMap(),
    val isSyncing: Boolean = false,
    val pendingConsent: PendingIntent? = null,
) {
    /**
     * Drag-and-drop only makes sense when looking at the full library in the
     * user's own order — a filtered or differently-sorted view would silently
     * rewrite positions the user can't see.
     */
    val isReorderingEnabled: Boolean
        get() = sortOption == BookSortOption.CUSTOM && selectedCategoryId == null
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    observeLibrary: ObserveLibraryUseCase,
    observeCategories: ObserveCategoriesUseCase,
    observeDownloads: ObserveDownloadsUseCase,
    private val importEpub: ImportEpubUseCase,
    private val createCategory: CreateCategoryUseCase,
    private val updateCategory: UpdateCategoryUseCase,
    private val deleteCategory: DeleteCategoryUseCase,
    private val assignBookCategory: AssignBookCategoryUseCase,
    private val saveCustomOrder: SaveCustomOrderUseCase,
    private val syncRemoteLibrary: SyncRemoteLibraryUseCase,
    private val downloadBook: DownloadBookUseCase,
    private val evictBook: EvictBookUseCase,
    private val driveAuthProvider: DriveAuthProvider,
) : ViewModel() {

    private val sortOption = MutableStateFlow(BookSortOption.TITLE_NATURAL)
    private val selectedCategoryId = MutableStateFlow<Long?>(null)
    private val isSyncing = MutableStateFlow(false)
    private val pendingConsent = MutableStateFlow<PendingIntent?>(null)

    /** One-off user-facing messages (import/sync results, unavailable books). */
    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages

    @OptIn(ExperimentalCoroutinesApi::class)
    private val libraryAndCategories =
        combine(sortOption, selectedCategoryId) { sort, category -> sort to category }
            // flatMapLatest: changing sort/filter cancels the previous stream
            // and re-subscribes — no stale emissions.
            .flatMapLatest { (sort, category) ->
                combine(
                    observeLibrary(sort, category),
                    observeCategories(),
                ) { books, categories -> Triple(sort, category, books to categories) }
            }

    val uiState: StateFlow<LibraryUiState> =
        combine(
            libraryAndCategories,
            observeDownloads(),
            isSyncing,
            pendingConsent,
        ) { (sort, category, data), downloads, syncing, consent ->
            val (books, categories) = data
            LibraryUiState(
                isLoading = false,
                books = books,
                sortOption = sort,
                categories = categories,
                selectedCategoryId = category,
                downloads = downloads,
                isSyncing = syncing,
                pendingConsent = consent,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState(),
        )

    fun onSortSelected(option: BookSortOption) {
        sortOption.value = option
    }

    fun onCategorySelected(categoryId: Long?) {
        selectedCategoryId.value = categoryId
    }

    fun onImport(uriString: String) {
        viewModelScope.launch {
            importEpub(uriString)
                .onSuccess { _messages.update { "Book added to your library." } }
                .onFailure { _messages.update { "Couldn't import that file — is it a valid EPUB?" } }
        }
    }

    fun onCreateCategory(name: String, colorArgb: Int) {
        viewModelScope.launch {
            createCategory(name, colorArgb)
                .onFailure { e -> _messages.update { e.message ?: "Couldn't create category." } }
        }
    }

    fun onUpdateCategory(categoryId: Long, name: String, colorArgb: Int) {
        viewModelScope.launch {
            updateCategory(categoryId, name, colorArgb)
                .onFailure { e -> _messages.update { e.message ?: "Couldn't update category." } }
        }
    }

    fun onDeleteCategory(categoryId: Long) {
        viewModelScope.launch {
            deleteCategory(categoryId)
            if (selectedCategoryId.value == categoryId) selectedCategoryId.value = null
        }
    }

    fun onAssignCategory(bookId: Long, categoryId: Long?) {
        viewModelScope.launch { assignBookCategory(bookId, categoryId) }
    }

    /** Persists the arrangement produced by a completed drag session. */
    fun onReorder(orderedBookIds: List<Long>) {
        viewModelScope.launch { saveCustomOrder(orderedBookIds) }
    }

    fun onSync() {
        if (isSyncing.value) return
        viewModelScope.launch {
            isSyncing.value = true
            syncRemoteLibrary()
                .onSuccess { added ->
                    _messages.update {
                        if (added > 0) "Synced — $added new book(s) available." else "Library is up to date."
                    }
                }
                .onFailure { e ->
                    if (e is NeedConsentException) {
                        pendingConsent.value = e.intent
                    } else {
                        // Fallback: check if it's wrapped or has the specific message
                        val consentException = e.cause as? NeedConsentException
                        if (consentException != null) {
                            pendingConsent.value = consentException.intent
                        } else if (e.message == "Consent required") {
                            // This shouldn't happen if types match, but helps diagnostics
                            _messages.update { "Please grant permissions to access Google Drive." }
                        } else {
                            _messages.update { e.message ?: "Sync failed." }
                        }
                    }
                }
            isSyncing.value = false
        }
    }

    fun onConsentShown() {
        pendingConsent.value = null
    }

    /**
     * Called with the consent Activity's result. We read the granted token
     * straight from the returned Intent (instead of re-authorizing blind, which
     * looped), cache it in the provider, then sync — which now succeeds.
     */
    fun onConsentResult(data: Intent?) {
        viewModelScope.launch {
            when (driveAuthProvider.completeConsent(data)) {
                is AuthResult.Success -> onSync()
                else -> _messages.update { "Google Drive permission wasn't granted." }
            }
        }
    }

    fun onDownload(bookId: Long) {
        viewModelScope.launch {
            downloadBook(bookId).onFailure { _messages.update { "Download failed." } }
        }
    }

    fun onEvict(bookId: Long) {
        viewModelScope.launch {
            evictBook(bookId)
                .onSuccess { _messages.update { "Download removed — metadata kept." } }
                .onFailure { _messages.update { "Couldn't remove the download." } }
        }
    }

    fun onUnavailableBook() {
        _messages.update { "This book isn't downloaded yet." }
    }

    fun onMessageShown() {
        _messages.update { null }
    }
}
