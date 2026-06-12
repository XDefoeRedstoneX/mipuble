package com.mipuble.ui.reader

import com.mipuble.domain.model.PageTurnMode
import com.mipuble.domain.model.ReaderFont
import com.mipuble.domain.model.ReaderPreferences
import com.mipuble.domain.model.ReaderTheme

/**
 * MVI contract for the Reader — the one screen built MVI-style as a contrast to
 * the MVVM screens elsewhere. The View renders [ReaderState] and sends user
 * intents back as [ReaderEvent]; the ViewModel is the only place that turns
 * events into new state. Chapter content, chrome visibility, and persisted
 * preferences are merged into a single immutable state.
 */
data class ReaderState(
    val isLoading: Boolean = true,
    val bookTitle: String = "",
    val chapterCount: Int = 0,
    val currentChapter: Int = 0,
    /** Fully-qualified URL the WebView should load for the current chapter. */
    val chapterUrl: String? = null,
    val error: String? = null,
    /** Reader chrome (bars) visibility; toggled by tapping the page. */
    val showControls: Boolean = true,
    val showSettings: Boolean = false,
    val preferences: ReaderPreferences = ReaderPreferences(),
) {
    val hasPrevious: Boolean get() = currentChapter > 0
    val hasNext: Boolean get() = currentChapter < chapterCount - 1
}

sealed interface ReaderEvent {
    data object NextChapter : ReaderEvent
    data object PreviousChapter : ReaderEvent
    data class GoToChapter(val index: Int) : ReaderEvent

    data object ToggleControls : ReaderEvent
    data object OpenSettings : ReaderEvent
    data object CloseSettings : ReaderEvent

    data class SetTheme(val theme: ReaderTheme) : ReaderEvent
    data class SetFont(val font: ReaderFont) : ReaderEvent
    data class SetPageTurnMode(val mode: PageTurnMode) : ReaderEvent
    data object IncreaseFont : ReaderEvent
    data object DecreaseFont : ReaderEvent
    data object IncreaseLineSpacing : ReaderEvent
    data object DecreaseLineSpacing : ReaderEvent

    /** The headliner: precise ±1% brightness. */
    data object IncreaseBrightness : ReaderEvent
    data object DecreaseBrightness : ReaderEvent
    data class SetBrightness(val percent: Int) : ReaderEvent
    data class SetFollowSystemBrightness(val enabled: Boolean) : ReaderEvent
}
