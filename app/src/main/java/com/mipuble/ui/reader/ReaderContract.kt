package com.mipuble.ui.reader

/**
 * MVI contract for the Reader — the one screen built MVI-style as a contrast to
 * the MVVM screens elsewhere. The View renders [ReaderState] and sends user
 * intents back as [ReaderEvent]; the ViewModel is the single place that turns
 * events into new state.
 */
data class ReaderState(
    val isLoading: Boolean = true,
    val bookTitle: String = "",
    val chapterCount: Int = 0,
    val currentChapter: Int = 0,
    /** Fully-qualified URL the WebView should load for the current chapter. */
    val chapterUrl: String? = null,
    val error: String? = null,
) {
    val hasPrevious: Boolean get() = currentChapter > 0
    val hasNext: Boolean get() = currentChapter < chapterCount - 1
}

sealed interface ReaderEvent {
    data object NextChapter : ReaderEvent
    data object PreviousChapter : ReaderEvent
    data class GoToChapter(val index: Int) : ReaderEvent
}
