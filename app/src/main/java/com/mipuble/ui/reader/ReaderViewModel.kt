package com.mipuble.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mipuble.data.epub.EpubParser
import com.mipuble.data.epub.EpubResourceReader
import com.mipuble.domain.model.ReaderPreferences
import com.mipuble.domain.model.ReaderSettingsBounds
import com.mipuble.domain.model.ReaderTheme
import com.mipuble.domain.repository.BookRepository
import com.mipuble.domain.repository.ReaderPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BookRepository,
    private val preferencesRepository: ReaderPreferencesRepository,
    private val parser: EpubParser,
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle["bookId"]) { "bookId is required" }

    /** Chapter + chrome state owned by the ViewModel; merged with persisted prefs. */
    private data class ContentState(
        val isLoading: Boolean = true,
        val bookTitle: String = "",
        val chapterCount: Int = 0,
        val currentChapter: Int = 0,
        val chapterUrl: String? = null,
        val error: String? = null,
        val showControls: Boolean = true,
        val showSettings: Boolean = false,
    )

    private val content = MutableStateFlow(ContentState())

    val state: StateFlow<ReaderState> =
        combine(content, preferencesRepository.preferences) { c, prefs ->
            ReaderState(
                isLoading = c.isLoading,
                bookTitle = c.bookTitle,
                chapterCount = c.chapterCount,
                currentChapter = c.currentChapter,
                chapterUrl = c.chapterUrl,
                error = c.error,
                showControls = c.showControls,
                showSettings = c.showSettings,
                preferences = prefs,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReaderState(),
        )

    private var resourceReader: EpubResourceReader? = null
    private var spine: List<String> = emptyList()

    init {
        load()
    }

    private fun load() = viewModelScope.launch {
        val book = repository.getBook(bookId)
        val path = book?.filePath
        if (book == null || path == null) {
            content.update { it.copy(isLoading = false, error = "This book isn't available on the device.") }
            return@launch
        }

        val file = File(path)
        runCatching {
            val epub = withContext(Dispatchers.IO) { parser.parse(file) }
            resourceReader = withContext(Dispatchers.IO) { EpubResourceReader(file) }
            spine = epub.spineHrefs

            val start = book.lastChapterIndex.coerceIn(0, lastIndex())
            content.update {
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
            content.update { it.copy(isLoading = false, error = e.message ?: "Failed to open this book.") }
        }
    }

    fun onEvent(event: ReaderEvent) {
        when (event) {
            ReaderEvent.NextChapter -> goToChapter(content.value.currentChapter + 1)
            ReaderEvent.PreviousChapter -> goToChapter(content.value.currentChapter - 1)
            is ReaderEvent.GoToChapter -> goToChapter(event.index)

            ReaderEvent.ToggleControls -> content.update { it.copy(showControls = !it.showControls) }
            ReaderEvent.OpenSettings -> content.update { it.copy(showSettings = true, showControls = true) }
            ReaderEvent.CloseSettings -> content.update { it.copy(showSettings = false) }

            is ReaderEvent.SetTheme -> updatePrefs { preferencesRepository.setTheme(event.theme) }
            ReaderEvent.IncreaseFont -> stepFont(+1)
            ReaderEvent.DecreaseFont -> stepFont(-1)
            ReaderEvent.IncreaseLineSpacing -> stepLineSpacing(+1)
            ReaderEvent.DecreaseLineSpacing -> stepLineSpacing(-1)

            ReaderEvent.IncreaseBrightness -> stepBrightness(+1)
            ReaderEvent.DecreaseBrightness -> stepBrightness(-1)
            is ReaderEvent.SetBrightness -> updatePrefs {
                preferencesRepository.setFollowSystemBrightness(false)
                preferencesRepository.setBrightnessPercent(event.percent)
            }
            is ReaderEvent.SetFollowSystemBrightness -> updatePrefs {
                preferencesRepository.setFollowSystemBrightness(event.enabled)
            }
        }
    }

    private fun goToChapter(index: Int) {
        if (spine.isEmpty()) return
        val clamped = index.coerceIn(0, lastIndex())
        if (clamped == content.value.currentChapter) return

        content.update { it.copy(currentChapter = clamped, chapterUrl = urlForChapter(clamped)) }
        persistPosition(clamped)
    }

    private fun currentPrefs(): ReaderPreferences = state.value.preferences

    private fun stepFont(direction: Int) = updatePrefs {
        preferencesRepository.setFontScalePercent(
            ReaderSettingsBounds.stepFont(currentPrefs().fontScalePercent, direction),
        )
    }

    private fun stepLineSpacing(direction: Int) = updatePrefs {
        preferencesRepository.setLineSpacingPercent(
            ReaderSettingsBounds.stepLineSpacing(currentPrefs().lineSpacingPercent, direction),
        )
    }

    private fun stepBrightness(direction: Int) = updatePrefs {
        preferencesRepository.setFollowSystemBrightness(false)
        preferencesRepository.setBrightnessPercent(
            ReaderSettingsBounds.stepBrightness(currentPrefs().brightnessPercent, direction),
        )
    }

    private fun updatePrefs(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
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
