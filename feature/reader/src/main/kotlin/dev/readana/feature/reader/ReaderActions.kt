package dev.readana.feature.reader

import dev.readana.core.model.HighlightTint
import dev.readana.core.model.ReaderSettings
import org.readium.r2.navigator.Navigator
import org.readium.r2.shared.publication.Locator

/**
 * Everything the reading surface can ask for, in one parameter.
 *
 * The screen is a single scene with a lot of small controls — chrome, three sheets, a dialog — and
 * threading each one through as its own lambda had already pushed the signatures past thirty
 * parameters. Grouping them follows [dev.readana.feature.settings]'s `AiSettingsActions`: the
 * composables keep taking state and actions, and adding a control no longer edits three signatures.
 */
internal data class ReaderActions(
    val onBack: () -> Unit,
    val onToggleChrome: () -> Unit,
    val onLocatorChange: (Locator) -> Unit,
    /** The live navigator, which is the only thing that can actually move the page. */
    val onNavigator: (Navigator?) -> Unit,
    val onSeek: (Float) -> Unit,
    val onShowSheet: (ReaderSheet) -> Unit,
    val onDismissSheet: () -> Unit,
    val onSelectChapter: (TocItem) -> Unit,
    val onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    val onResetSettings: () -> Unit,
    val onToggleBookmark: () -> Unit,
    val onSelectBookmark: (BookmarkItem) -> Unit,
    val onDeleteBookmark: (Long) -> Unit,
    val onSearch: (String) -> Unit,
    val onLoadMoreResults: () -> Unit,
    val onSelectSearchHit: (SearchHit) -> Unit,
    val onSelectHighlight: (Long) -> Unit,
    val onEditHighlight: (Long?) -> Unit,
    val onUpdateHighlight: (Long, HighlightTint, String?) -> Unit,
    val onDeleteHighlight: (Long) -> Unit,
    val onShareNotes: () -> Unit,
    val onConsumeNotesExport: () -> Unit
)

/** The assistant's half of the same idea; it is driven by its own ViewModel. */
internal data class AssistantActions(
    val onOpenConversation: (Long) -> Unit,
    val onDeleteConversation: (Long) -> Unit,
    val onRecapAccept: () -> Unit,
    val onRecapDismiss: () -> Unit,
    /** Starts a conversation about the book with no passage selected. */
    val onAskBook: () -> Unit,
    /** Hands the passage the dictionary could not settle to the assistant instead. */
    val onExplainSelection: () -> Unit,
    val onSend: (String) -> Unit,
    val onRetry: () -> Unit,
    val onDismiss: () -> Unit
)

/** Read-aloud's controls, from the button that starts it to the toolbar that runs it. */
internal data class TtsActions(
    val onListen: () -> Unit,
    val onPlayPause: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onCycleSpeed: () -> Unit,
    val onStop: () -> Unit
)
