package com.mipuble.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipuble.domain.model.Book
import com.mipuble.domain.model.Category
import com.mipuble.domain.sort.BookSortOption
import com.mipuble.domain.usecase.AssignBookCategoryUseCase
import com.mipuble.domain.usecase.CreateCategoryUseCase
import com.mipuble.domain.usecase.DeleteCategoryUseCase
import com.mipuble.domain.usecase.ImportEpubUseCase
import com.mipuble.domain.usecase.ObserveCategoriesUseCase
import com.mipuble.domain.usecase.ObserveLibraryUseCase
import com.mipuble.domain.usecase.SaveCustomOrderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val isLoading: Boolean = true,
    val books: List<Book> = emptyList(),
    val sortOption: BookSortOption = BookSortOption.TITLE_NATURAL,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null,
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
    private val importEpub: ImportEpubUseCase,
    private val createCategory: CreateCategoryUseCase,
    private val deleteCategory: DeleteCategoryUseCase,
    private val assignBookCategory: AssignBookCategoryUseCase,
    private val saveCustomOrder: SaveCustomOrderUseCase,
) : ViewModel() {

    private val sortOption = MutableStateFlow(BookSortOption.TITLE_NATURAL)
    private val selectedCategoryId = MutableStateFlow<Long?>(null)

    /** One-off user-facing messages (import results, unavailable books). */
    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LibraryUiState> =
        combine(sortOption, selectedCategoryId) { sort, category -> sort to category }
            // flatMapLatest: changing sort/filter cancels the previous stream
            // and re-subscribes — no stale emissions.
            .flatMapLatest { (sort, category) ->
                combine(
                    observeLibrary(sort, category),
                    observeCategories(),
                ) { books, categories ->
                    LibraryUiState(
                        isLoading = false,
                        books = books,
                        sortOption = sort,
                        categories = categories,
                        selectedCategoryId = category,
                    )
                }
            }
            .stateIn(
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

    fun onUnavailableBook() {
        _messages.update { "This book isn't downloaded yet." }
    }

    fun onMessageShown() {
        _messages.update { null }
    }
}
