package com.mipuble.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipuble.data.epub.EpubParser
import com.mipuble.data.epub.EpubResourceReader
import com.mipuble.domain.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BookRepository,
    private val parser: EpubParser,
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle["bookId"]) { "bookId is required" }

    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private var resourceReader: EpubResourceReader? = null
    private var spine: List<String> = emptyList()

    init {
        load()
    }

    private fun load() = viewModelScope.launch {
        val book = repository.getBook(bookId)
        val path = book?.filePath
        if (book == null || path == null) {
            _state.update { it.copy(isLoading = false, error = "This book isn't available on the device.") }
            return@launch
        }

        val file = File(path)
        runCatching {
            val epub = withContext(Dispatchers.IO) { parser.parse(file) }
            val reader = withContext(Dispatchers.IO) { EpubResourceReader(file) }
            resourceReader = reader
            spine = epub.spineHrefs

            val start = book.lastChapterIndex.coerceIn(0, lastIndex())
            _state.update {
                it.copy(
                    isLoading = false,
                    bookTitle = epub.title.ifBlank { book.title },
                    chapterCount = spine.size,
                    currentChapter = start,
                    chapterUrl = urlForChapter(start),
                    error = if (spine.isEmpty()) "This book has no readable content." else null,
                )
            }
        }.onFailure { e ->
            _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to open this book.") }
        }
    }

    fun onEvent(event: ReaderEvent) {
        when (event) {
            ReaderEvent.NextChapter -> goToChapter(_state.value.currentChapter + 1)
            ReaderEvent.PreviousChapter -> goToChapter(_state.value.currentChapter - 1)
            is ReaderEvent.GoToChapter -> goToChapter(event.index)
        }
    }

    private fun goToChapter(index: Int) {
        if (spine.isEmpty()) return
        val clamped = index.coerceIn(0, lastIndex())
        if (clamped == _state.value.currentChapter) return

        _state.update { it.copy(currentChapter = clamped, chapterUrl = urlForChapter(clamped)) }
        persistPosition(clamped)
    }

    private fun persistPosition(chapter: Int) = viewModelScope.launch {
        val progress = if (spine.isEmpty()) 0f else (chapter + 1).toFloat() / spine.size
        repository.updateReadingPosition(bookId, chapter, progress)
    }

    /** Called by the WebView client to stream a resource from the open EPUB. */
    fun readResource(entryPath: String): ByteArray? = resourceReader?.read(entryPath)

    private fun lastIndex() = (spine.size - 1).coerceAtLeast(0)

    private fun urlForChapter(index: Int): String? =
        spine.getOrNull(index)?.let { EpubWebViewBridge.BASE + it }

    override fun onCleared() {
        resourceReader?.close()
    }
}
