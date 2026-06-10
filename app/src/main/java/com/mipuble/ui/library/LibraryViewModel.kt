package com.mipuble.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipuble.domain.model.Book
import com.mipuble.domain.sort.BookSortOption
import com.mipuble.domain.usecase.ImportEpubUseCase
import com.mipuble.domain.usecase.ObserveLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val isLoading: Boolean = true,
    val books: List<Book> = emptyList(),
    val sortOption: BookSortOption = BookSortOption.TITLE_NATURAL,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    observeLibrary: ObserveLibraryUseCase,
    private val importEpub: ImportEpubUseCase,
) : ViewModel() {

    private val sortOption = MutableStateFlow(BookSortOption.TITLE_NATURAL)

    /** One-off user-facing messages (import results, unavailable books). */
    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LibraryUiState> = sortOption
        // flatMapLatest: changing the sort cancels the previous stream and
        // re-subscribes with the new comparator — no stale emissions.
        .flatMapLatest { option ->
            observeLibrary(option).map { books ->
                LibraryUiState(isLoading = false, books = books, sortOption = option)
            }
        }
        .stateIn(
            scope = viewModelScope,
            // Keep the upstream (and Room's observer) alive across rotations,
            // but release it 5s after the UI is gone.
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState(),
        )

    fun onSortSelected(option: BookSortOption) {
        sortOption.value = option
    }

    fun onImport(uriString: String) {
        viewModelScope.launch {
            importEpub(uriString)
                .onSuccess { _messages.update { "Book added to your library." } }
                .onFailure { _messages.update { "Couldn't import that file — is it a valid EPUB?" } }
        }
    }

    fun onUnavailableBook() {
        _messages.update { "This book isn't downloaded yet." }
    }

    fun onMessageShown() {
        _messages.update { null }
    }
}
