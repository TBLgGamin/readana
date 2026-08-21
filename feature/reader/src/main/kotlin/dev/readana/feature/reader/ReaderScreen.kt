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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.component.EmptyState
import dev.readana.core.designsystem.component.LoadingState
import org.readium.r2.shared.publication.Locator

@Composable
internal fun ReaderRoute(
    bookId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val systemInDarkTheme = isSystemInDarkTheme()
    LaunchedEffect(bookId) { viewModel.open(bookId, systemInDarkTheme) }
    ReaderScreen(
        uiState = uiState,
        onBack = onBack,
        onToggleChrome = viewModel::toggleChrome,
        onLocatorChange = viewModel::onLocatorChanged,
        modifier = modifier
    )
}

@Composable
internal fun ReaderScreen(
    uiState: ReaderUiState,
    onBack: () -> Unit,
    onToggleChrome: () -> Unit,
    onLocatorChange: (Locator) -> Unit,
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
                title = stringResource(
                    R.string.feature_reader_open_failed,
                    uiState.error.message
                )
            )

            is ReaderUiState.Ready -> ReadingSurface(
                state = uiState,
                onBack = onBack,
                onToggleChrome = onToggleChrome,
                onLocatorChange = onLocatorChange
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
            onNavigator = {},
            modifier = Modifier.fillMaxSize()
        )
        ReaderTopBar(
            title = state.title,
            visible = state.chromeVisible,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(title: String, visible: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier) {
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
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = CHROME_ALPHA)
            )
        )
    }
}

/** Reader chrome floats over the page, so it is only mostly opaque. */
private const val CHROME_ALPHA = 0.95f
