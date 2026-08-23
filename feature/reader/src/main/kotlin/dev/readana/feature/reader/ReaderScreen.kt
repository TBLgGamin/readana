package dev.readana.feature.reader

import android.graphics.PointF
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
import dev.readana.core.designsystem.component.color
import dev.readana.core.model.HighlightTint
import dev.readana.core.model.PenMode
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.shared.publication.Locator

/** Reader chrome floats over the page, so it is only mostly opaque. */
internal const val CHROME_ALPHA = 0.95f

/** Notes are exported as Markdown; every notes app and mail client understands it. */
private const val NOTES_MIME_TYPE = "text/markdown"

@Composable
internal fun ReaderRoute(
    bookId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
    assistantViewModel: ReaderAssistantViewModel = hiltViewModel(),
    ttsViewModel: ReaderTtsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val assistant by assistantViewModel.uiState.collectAsStateWithLifecycle()
    val tts by ttsViewModel.uiState.collectAsStateWithLifecycle()
    val systemInDarkTheme = isSystemInDarkTheme()
    LaunchedEffect(bookId) { viewModel.open(bookId, systemInDarkTheme) }
    var navigator by remember { mutableStateOf<Navigator?>(null) }
    var surface by remember { mutableStateOf<ReaderSurfaceView?>(null) }
    // The stroke as it is being drawn, so the line appears under the nib rather than on lift.
    var inkInProgress by remember { mutableStateOf<List<PointF>>(emptyList()) }
    // Kept because the navigator clears the selection the moment an action runs, and the
    // definition sheet's way out needs the passage it was opened for.
    var definedSelection by remember { mutableStateOf<ReaderSelection?>(null) }
    val ready = uiState as? ReaderUiState.Ready
    val hasText = ready?.hasText == true
    LaunchedEffect(bookId, hasText) { assistantViewModel.attach(bookId, hasText) }

    /** Jumping is the Route's job: only it holds the navigator. */
    fun goTo(target: ReaderTarget, closeSheet: Boolean = true) {
        viewModel.locatorFor(target)?.let { navigator?.go(it) }
        if (closeSheet) viewModel.showSheet(ReaderSheet.None)
    }

    val actions = ReaderActions(
        onBack = onBack,
        onToggleChrome = viewModel::toggleChrome,
        onLocatorChange = viewModel::onLocatorChanged,
        onNavigator = { navigator = it },
        onSurface = { surface = it },
        onSeek = { fraction -> goTo(ReaderTarget.Progress(fraction), closeSheet = false) },
        onShowSheet = viewModel::showSheet,
        onDismissSheet = { viewModel.showSheet(ReaderSheet.None) },
        onSelectChapter = { item -> goTo(ReaderTarget.Chapter(item.index)) },
        onSettingsChange = viewModel::updateSettings,
        onResetSettings = viewModel::resetSettings,
        onToggleBookmark = viewModel::toggleBookmark,
        onSelectBookmark = { bookmark -> goTo(ReaderTarget.Bookmark(bookmark.id)) },
        onDeleteBookmark = { id -> viewModel.delete(ReaderTarget.Bookmark(id)) },
        onSearch = viewModel::runSearch,
        onLoadMoreResults = viewModel::loadMoreResults,
        onSelectSearchHit = { hit -> goTo(ReaderTarget.Hit(hit.index)) },
        onSelectHighlight = { id -> goTo(ReaderTarget.Highlight(id)) },
        onEditHighlight = viewModel::editHighlight,
        onUpdateHighlight = viewModel::updateHighlight,
        onDeleteHighlight = { id -> viewModel.delete(ReaderTarget.Highlight(id)) },
        onShareNotes = viewModel::shareNotes,
        onConsumeNotesExport = viewModel::consumeNotesExport
    )

    val ttsActions = TtsActions(
        // Reading aloud starts where the reader is looking, not where the book was last saved.
        onListen = { ttsViewModel.start((navigator as? VisualNavigator)?.currentLocator?.value) },
        onPlayPause = { if (tts.playing) ttsViewModel.pause() else ttsViewModel.resume() },
        onPrevious = ttsViewModel::previous,
        onNext = ttsViewModel::next,
        onCycleSpeed = ttsViewModel::cycleSpeed,
        onStop = ttsViewModel::stop
    )

    val assistantActions = AssistantActions(
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
        onAskBook = {
            assistantViewModel.askBook(
                anchor = { (navigator as? VisualNavigator)?.firstVisibleElementLocator() },
                progression = ready?.progression,
                chapterTitle = ready?.chapterTitle
            )
        },
        onRecapDismiss = assistantViewModel::dismissRecapOffer,
        // The passage the dictionary could not settle, still with its place in the book, so the
        // assistant is given the same spoiler-safe context an Explain from the page would get.
        onExplainSelection = {
            definedSelection?.let { assistantViewModel.explain(it, ready?.progression, ready?.chapterTitle) }
        },
        onSend = assistantViewModel::send,
        onRetry = assistantViewModel::retry,
        onDismiss = assistantViewModel::dismiss
    )

    ReaderScreen(
        uiState = uiState,
        assistant = assistant,
        tts = tts,
        actions = actions,
        assistantActions = assistantActions,
        ttsActions = ttsActions,
        modifier = modifier,
        inkInProgress = inkInProgress
    )

    PenGestures(
        surface = surface,
        navigator = navigator,
        penMode = ready?.settings?.penMode ?: PenMode.Off,
        palmRejection = ready?.settings?.palmRejection ?: true,
        onHighlight = { locator, text -> viewModel.addHighlight(locator, text) },
        onInkStroke = viewModel::addInkStroke,
        onInkInProgress = { inkInProgress = it }
    )

    SpokenUtteranceEffect(navigator = navigator, utterance = tts.utterance)

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
        onDefine = { selection ->
            definedSelection = selection
            viewModel.define(selection.text)
        },
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
    assistant: AssistantUiState,
    tts: TtsUiState,
    actions: ReaderActions,
    assistantActions: AssistantActions,
    ttsActions: TtsActions,
    modifier: Modifier = Modifier,
    /** The pen stroke being drawn right now, so the line follows the nib. */
    inkInProgress: List<PointF> = emptyList()
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
                assistant = assistant,
                tts = tts,
                actions = actions,
                assistantActions = assistantActions,
                ttsActions = ttsActions,
                inkInProgress = inkInProgress
            )
        }
    }
}

@Composable
private fun ReadingSurface(
    state: ReaderUiState.Ready,
    assistant: AssistantUiState,
    tts: TtsUiState,
    actions: ReaderActions,
    assistantActions: AssistantActions,
    ttsActions: TtsActions,
    inkInProgress: List<PointF>,
    modifier: Modifier = Modifier
) {
    KeepScreenOnEffect(state.settings.keepScreenOn)
    ImmersiveModeEffect(immersive = !state.chromeVisible)
    ShareFileEffect(
        uri = state.notesExportUri,
        title = state.title,
        mimeType = NOTES_MIME_TYPE,
        onConsume = actions.onConsumeNotesExport
    )

    Box(modifier = modifier.fillMaxSize()) {
        ReaderNavigatorHost(
            sessionId = state.sessionId,
            settings = state.settings,
            onLocatorChange = actions.onLocatorChange,
            onCenterTap = actions.onToggleChrome,
            onNavigator = actions.onNavigator,
            onSurface = actions.onSurface,
            modifier = Modifier.fillMaxSize()
        )
        // Above the page, but never in the way: strokes are captured below, in the surface view,
        // where a pen can still be told from a finger.
        ReaderInkOverlay(
            strokes = state.ink,
            inProgress = inkInProgress,
            inProgressTint = HighlightTint.Yellow.color(),
            modifier = Modifier.fillMaxSize()
        )
        ReaderTopBar(
            title = state.title,
            visible = state.chromeVisible,
            bookmarked = state.currentIsBookmarked,
            searchable = state.isSearchable,
            onBack = actions.onBack,
            onToggleBookmark = actions.onToggleBookmark,
            onSearch = { actions.onShowSheet(ReaderSheet.Search) },
            modifier = Modifier.align(Alignment.TopCenter)
        )
        ReaderBottomBar(
            state = state,
            onSeek = actions.onSeek,
            onShowContents = { actions.onShowSheet(ReaderSheet.Contents) },
            onShowSettings = { actions.onShowSheet(ReaderSheet.Settings) },
            onShowConversations = { actions.onShowSheet(ReaderSheet.Conversations) },
            onListen = ttsActions.onListen,
            conversationsAvailable = assistant.enabled || assistant.threads.isNotEmpty(),
            listenAvailable = state.hasText,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        ReaderTtsBar(
            state = tts,
            onPlayPause = ttsActions.onPlayPause,
            onPrevious = ttsActions.onPrevious,
            onNext = ttsActions.onNext,
            onCycleSpeed = ttsActions.onCycleSpeed,
            onStop = ttsActions.onStop,
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
                onAccept = assistantActions.onRecapAccept,
                onDismiss = assistantActions.onRecapDismiss
            )
        }
    }

    ReaderSheets(state = state, assistant = assistant, actions = actions, assistantActions = assistantActions)

    if (assistant.visible) {
        ReaderAssistantSheet(
            state = assistant,
            onSend = assistantActions.onSend,
            onRetry = assistantActions.onRetry,
            onDismiss = assistantActions.onDismiss
        )
    }

    state.editingHighlight?.let { highlight ->
        HighlightEditorDialog(
            highlight = highlight,
            onSave = actions.onUpdateHighlight,
            onDelete = actions.onDeleteHighlight,
            onDismiss = { actions.onEditHighlight(null) }
        )
    }
}

/** Whichever sheet is open, or nothing. Sheets sit outside the reading [Box] on purpose. */
@Composable
private fun ReaderSheets(
    state: ReaderUiState.Ready,
    assistant: AssistantUiState,
    actions: ReaderActions,
    assistantActions: AssistantActions
) {
    when (state.sheet) {
        ReaderSheet.None -> Unit

        ReaderSheet.Contents -> ReaderContentsSheet(
            state = state,
            actions = actions
        )

        ReaderSheet.Conversations -> ReaderConversationsSheet(
            threads = assistant.threads,
            assistantEnabled = assistant.enabled,
            onAskBook = {
                actions.onDismissSheet()
                assistantActions.onAskBook()
            },
            onRecap = {
                actions.onDismissSheet()
                assistantActions.onRecapAccept()
            },
            onOpen = { id ->
                actions.onDismissSheet()
                assistantActions.onOpenConversation(id)
            },
            onDelete = assistantActions.onDeleteConversation,
            onDismiss = actions.onDismissSheet
        )

        ReaderSheet.Settings -> ReaderSettingsSheet(
            settings = state.settings,
            isFixedLayout = state.isFixedLayout,
            hasOwnSettings = state.hasOwnSettings,
            onChange = actions.onSettingsChange,
            onReset = actions.onResetSettings,
            onDismiss = actions.onDismissSheet
        )

        ReaderSheet.Definition -> state.definition?.let { definition ->
            ReaderDefinitionSheet(
                state = definition,
                assistantEnabled = assistant.enabled,
                onAskAssistant = {
                    actions.onDismissSheet()
                    assistantActions.onExplainSelection()
                },
                onDismiss = actions.onDismissSheet
            )
        }

        ReaderSheet.Search -> ReaderSearchSheet(
            state = state.search,
            onSearch = actions.onSearch,
            onLoadMore = actions.onLoadMoreResults,
            onSelect = actions.onSelectSearchHit,
            onDismiss = actions.onDismissSheet
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
