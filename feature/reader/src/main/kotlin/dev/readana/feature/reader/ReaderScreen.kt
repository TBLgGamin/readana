package dev.readana.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.readana.core.common.input.VolumeKey
import dev.readana.core.common.input.VolumeKeyRelay
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.component.EmptyState
import dev.readana.core.designsystem.component.LoadingState
import dev.readana.core.model.HighlightTint
import dev.readana.core.model.ReaderSettings
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.shared.publication.Locator

/** Reader chrome floats over the page, so it is only mostly opaque. */
internal const val CHROME_ALPHA = 0.95f

@Composable
internal fun ReaderRoute(
    bookId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
    assistantViewModel: ReaderAssistantViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val assistant by assistantViewModel.uiState.collectAsStateWithLifecycle()
    val systemInDarkTheme = isSystemInDarkTheme()
    LaunchedEffect(bookId) { viewModel.open(bookId, systemInDarkTheme) }
    var navigator by remember { mutableStateOf<Navigator?>(null) }
    val ready = uiState as? ReaderUiState.Ready
    val hasText = ready?.hasText == true
    LaunchedEffect(bookId, hasText) { assistantViewModel.attach(bookId, hasText) }

    ReaderScreen(
        uiState = uiState,
        onBack = onBack,
        onToggleChrome = viewModel::toggleChrome,
        onLocatorChange = viewModel::onLocatorChanged,
        onNavigator = { navigator = it },
        onSeek = { fraction -> viewModel.locatorFor(ReaderTarget.Progress(fraction))?.let { navigator?.go(it) } },
        onShowSheet = viewModel::showSheet,
        onDismissSheet = { viewModel.showSheet(ReaderSheet.None) },
        assistant = assistant,
        onOpenConversation = assistantViewModel::openThread,
        onDeleteConversation = assistantViewModel::deleteThread,
        onRecapAccept = {
            assistantViewModel.requestRecap(
                // The top of the page: a recap stops there, so it cannot mention what is below.
                anchor = { (navigator as? VisualNavigator)?.firstVisibleElementLocator() },
                progression = ready?.progression,
                chapterTitle = ready?.chapterTitle
            )
        },
        onRecapDismiss = assistantViewModel::dismissRecapOffer,
        onAssistantSend = assistantViewModel::send,
        onAssistantRetry = assistantViewModel::retry,
        onAssistantDismiss = assistantViewModel::dismiss,
        onSelectChapter = { item ->
            viewModel.locatorFor(ReaderTarget.Chapter(item.index))?.let { navigator?.go(it) }
            viewModel.showSheet(ReaderSheet.None)
        },
        onSettingsChange = viewModel::updateSettings,
        onToggleBookmark = viewModel::toggleBookmark,
        onSelectBookmark = { bookmark ->
            viewModel.locatorFor(ReaderTarget.Bookmark(bookmark.id))?.let { navigator?.go(it) }
            viewModel.showSheet(ReaderSheet.None)
        },
        onDeleteBookmark = { id -> viewModel.delete(ReaderTarget.Bookmark(id)) },
        onSearch = viewModel::runSearch,
        onLoadMoreResults = viewModel::loadMoreResults,
        onSelectSearchHit = { hit ->
            viewModel.locatorFor(ReaderTarget.Hit(hit.index))?.let { navigator?.go(it) }
            viewModel.showSheet(ReaderSheet.None)
        },
        onEditHighlight = viewModel::editHighlight,
        onUpdateHighlight = viewModel::updateHighlight,
        onDeleteHighlight = { id -> viewModel.delete(ReaderTarget.Highlight(id)) },
        modifier = modifier
    )

    HighlightDecorations(
        navigator = navigator,
        highlights = ready?.highlights.orEmpty(),
        locateHighlight = { id -> viewModel.locatorFor(ReaderTarget.Highlight(id)) },
        onActivate = viewModel::editHighlight
    )

    SelectionActions(
        relay = viewModel.selectionRelay,
        navigator = navigator,
        assistantEnabled = assistant.enabled,
        onHighlight = viewModel::addHighlight,
        onAsk = { selection ->
            assistantViewModel.ask(selection, ready?.progression, ready?.chapterTitle)
        },
        onExplain = { selection ->
            assistantViewModel.explain(selection, ready?.progression, ready?.chapterTitle)
        }
    )

    VolumeKeyPaging(
        relay = viewModel.volumeKeyRelay,
        navigator = navigator as? OverflowableNavigator,
        enabled = ready?.settings?.volumeKeysTurnPages == true
    )
}

@Composable
internal fun ReaderScreen(
    uiState: ReaderUiState,
    onBack: () -> Unit,
    onToggleChrome: () -> Unit,
    onLocatorChange: (Locator) -> Unit,
    onNavigator: (Navigator?) -> Unit,
    onSeek: (Float) -> Unit,
    onShowSheet: (ReaderSheet) -> Unit,
    onDismissSheet: () -> Unit,
    assistant: AssistantUiState,
    onOpenConversation: (Long) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onRecapAccept: () -> Unit,
    onRecapDismiss: () -> Unit,
    onAssistantSend: (String) -> Unit,
    onAssistantRetry: () -> Unit,
    onAssistantDismiss: () -> Unit,
    onSelectChapter: (TocItem) -> Unit,
    onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onToggleBookmark: () -> Unit,
    onSelectBookmark: (BookmarkItem) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onSearch: (String) -> Unit,
    onLoadMoreResults: () -> Unit,
    onSelectSearchHit: (SearchHit) -> Unit,
    onEditHighlight: (Long?) -> Unit,
    onUpdateHighlight: (Long, HighlightTint, String?) -> Unit,
    onDeleteHighlight: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            ReaderUiState.Loading -> LoadingState()

            ReaderUiState.NotFound -> EmptyState(
                icon = ReadanaIcons.Library,
                title = stringResource(R.string.feature_reader_not_found)
            )

            is ReaderUiState.Failed -> EmptyState(
                icon = ReadanaIcons.Library,
                title = stringResource(R.string.feature_reader_open_failed, uiState.error.message)
            )

            is ReaderUiState.Ready -> ReadingSurface(
                state = uiState,
                onBack = onBack,
                onToggleChrome = onToggleChrome,
                onLocatorChange = onLocatorChange,
                onNavigator = onNavigator,
                onSeek = onSeek,
                onShowSheet = onShowSheet,
                onDismissSheet = onDismissSheet,
                assistant = assistant,
                onOpenConversation = onOpenConversation,
                onDeleteConversation = onDeleteConversation,
                onRecapAccept = onRecapAccept,
                onRecapDismiss = onRecapDismiss,
                onAssistantSend = onAssistantSend,
                onAssistantRetry = onAssistantRetry,
                onAssistantDismiss = onAssistantDismiss,
                onSelectChapter = onSelectChapter,
                onSettingsChange = onSettingsChange,
                onToggleBookmark = onToggleBookmark,
                onSelectBookmark = onSelectBookmark,
                onDeleteBookmark = onDeleteBookmark,
                onSearch = onSearch,
                onLoadMoreResults = onLoadMoreResults,
                onSelectSearchHit = onSelectSearchHit,
                onEditHighlight = onEditHighlight,
                onUpdateHighlight = onUpdateHighlight,
                onDeleteHighlight = onDeleteHighlight
            )
        }
    }
}

@Composable
private fun ReadingSurface(
    state: ReaderUiState.Ready,
    onBack: () -> Unit,
    onToggleChrome: () -> Unit,
    onLocatorChange: (Locator) -> Unit,
    onNavigator: (Navigator?) -> Unit,
    onSeek: (Float) -> Unit,
    onShowSheet: (ReaderSheet) -> Unit,
    onDismissSheet: () -> Unit,
    assistant: AssistantUiState,
    onOpenConversation: (Long) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onRecapAccept: () -> Unit,
    onRecapDismiss: () -> Unit,
    onAssistantSend: (String) -> Unit,
    onAssistantRetry: () -> Unit,
    onAssistantDismiss: () -> Unit,
    onSelectChapter: (TocItem) -> Unit,
    onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onToggleBookmark: () -> Unit,
    onSelectBookmark: (BookmarkItem) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onSearch: (String) -> Unit,
    onLoadMoreResults: () -> Unit,
    onSelectSearchHit: (SearchHit) -> Unit,
    onEditHighlight: (Long?) -> Unit,
    onUpdateHighlight: (Long, HighlightTint, String?) -> Unit,
    onDeleteHighlight: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    KeepScreenOnEffect(state.settings.keepScreenOn)
    ImmersiveModeEffect(immersive = !state.chromeVisible)

    Box(modifier = modifier.fillMaxSize()) {
        ReaderNavigatorHost(
            sessionId = state.sessionId,
            settings = state.settings,
            onLocatorChange = onLocatorChange,
            onCenterTap = onToggleChrome,
            onNavigator = onNavigator,
            modifier = Modifier.fillMaxSize()
        )
        ReaderTopBar(
            title = state.title,
            visible = state.chromeVisible,
            bookmarked = state.currentIsBookmarked,
            searchable = state.isSearchable,
            onBack = onBack,
            onToggleBookmark = onToggleBookmark,
            onSearch = { onShowSheet(ReaderSheet.Search) },
            modifier = Modifier.align(Alignment.TopCenter)
        )
        ReaderBottomBar(
            state = state,
            onSeek = onSeek,
            onShowContents = { onShowSheet(ReaderSheet.Contents) },
            onShowSettings = { onShowSheet(ReaderSheet.Settings) },
            onShowConversations = { onShowSheet(ReaderSheet.Conversations) },
            conversationsAvailable = assistant.enabled || assistant.threads.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        AnimatedVisibility(
            visible = assistant.recapOffered && !assistant.visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            RecapOffer(
                daysAway = assistant.daysAway,
                onAccept = onRecapAccept,
                onDismiss = onRecapDismiss
            )
        }
    }

    when (state.sheet) {
        ReaderSheet.None -> Unit

        ReaderSheet.Contents -> ReaderContentsSheet(
            toc = state.toc,
            bookmarks = state.bookmarks,
            onSelectChapter = onSelectChapter,
            onSelectBookmark = onSelectBookmark,
            onDeleteBookmark = onDeleteBookmark,
            onDismiss = onDismissSheet
        )

        ReaderSheet.Conversations -> ReaderConversationsSheet(
            threads = assistant.threads,
            onOpen = { id ->
                onDismissSheet()
                onOpenConversation(id)
            },
            onDelete = onDeleteConversation,
            onDismiss = onDismissSheet
        )

        ReaderSheet.Settings -> ReaderSettingsSheet(
            settings = state.settings,
            isFixedLayout = state.isFixedLayout,
            onChange = onSettingsChange,
            onDismiss = onDismissSheet
        )

        ReaderSheet.Search -> ReaderSearchSheet(
            state = state.search,
            onSearch = onSearch,
            onLoadMore = onLoadMoreResults,
            onSelect = onSelectSearchHit,
            onDismiss = onDismissSheet
        )
    }

    if (assistant.visible) {
        ReaderAssistantSheet(
            state = assistant,
            onSend = onAssistantSend,
            onRetry = onAssistantRetry,
            onDismiss = onAssistantDismiss
        )
    }

    state.editingHighlight?.let { highlight ->
        HighlightEditorDialog(
            highlight = highlight,
            onSave = onUpdateHighlight,
            onDelete = onDeleteHighlight,
            onDismiss = { onEditHighlight(null) }
        )
    }
}

/** Claims the volume keys for page turns while the reader is open and the setting is on. */
@Composable
private fun VolumeKeyPaging(relay: VolumeKeyRelay, navigator: OverflowableNavigator?, enabled: Boolean) {
    DisposableEffect(relay, navigator, enabled) {
        if (enabled && navigator != null) {
            relay.setHandler { key ->
                when (key) {
                    VolumeKey.Up -> navigator.goBackward(animated = true)
                    VolumeKey.Down -> navigator.goForward(animated = true)
                }
            }
        }
        onDispose { relay.setHandler(null) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    title: String,
    visible: Boolean,
    bookmarked: Boolean,
    searchable: Boolean,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it }
    ) {
        TopAppBar(
            title = {
                Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        ReadanaIcons.Back,
                        contentDescription = stringResource(R.string.feature_reader_back)
                    )
                }
            },
            actions = {
                if (searchable) {
                    IconButton(onClick = onSearch) {
                        Icon(
                            ReadanaIcons.Discover,
                            contentDescription = stringResource(R.string.feature_reader_search)
                        )
                    }
                }
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        if (bookmarked) ReadanaIcons.BookmarkOn else ReadanaIcons.BookmarkOff,
                        contentDescription = stringResource(R.string.feature_reader_bookmark)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = CHROME_ALPHA)
            )
        )
    }
}
