package com.mipuble.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipuble.domain.model.Book
import com.mipuble.domain.sort.BookSortOption
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

data class LibraryUiState(
    val isLoading: Boolean = true,
    val books: List<Book> = emptyList(),
    val sortOption: BookSortOption = BookSortOption.TITLE_NATURAL,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    observeLibrary: ObserveLibraryUseCase,
) : ViewModel() {

    private val sortOption = MutableStateFlow(BookSortOption.TITLE_NATURAL)

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
}
