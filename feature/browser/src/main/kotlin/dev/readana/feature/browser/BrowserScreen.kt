package dev.readana.feature.browser

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.component.WavyProgress

@Composable
internal fun BrowserRoute(
    key: BrowserKey,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrowserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BrowserScreen(
        key = key,
        uiState = uiState,
        onCapture = viewModel::onCapture,
        onProgress = viewModel::onProgress,
        onDismissResult = viewModel::consumeResult,
        onClose = onClose,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserScreen(
    key: BrowserKey,
    uiState: BrowserUiState,
    onCapture: (CapturedFile) -> Unit,
    onProgress: (Int) -> Unit,
    onDismissResult: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val resultText = uiState.result?.let { resultText(it) }
    val currentOnDismissResult by rememberUpdatedState(onDismissResult)
    val currentOnClose by rememberUpdatedState(onClose)
    var webView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(resultText) {
        if (resultText != null) {
            snackbarHostState.showSnackbar(resultText)
            currentOnDismissResult()
            // The download now lives in the queue; the browser has done its job.
            currentOnClose()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(key.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            ReadanaIcons.Back,
                            contentDescription = stringResource(R.string.feature_browser_close)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.loadProgress in 1..99) {
                WavyProgress(
                    progress = { uiState.loadProgress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            BrowserWebView(
                key = key,
                onCapture = onCapture,
                onProgress = onProgress,
                onAttachWebView = { webView = it },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    BackHandler(enabled = webView?.canGoBack() == true) { webView?.goBack() }
}

/**
 * A plain WebView pointed at the record page. Anna's Archive has no password: when a membership
 * key is configured it is posted to the account page first, which sets the session cookie the slow
 * download needs.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserWebView(
    key: BrowserKey,
    onCapture: (CapturedFile) -> Unit,
    onProgress: (Int) -> Unit,
    onAttachWebView: (WebView) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnCapture by rememberUpdatedState(onCapture)
    val currentOnProgress by rememberUpdatedState(onProgress)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                var signedIn = key.loginUrl == null
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        if (!signedIn) {
                            signedIn = true
                            view.loadUrl(key.url)
                        }
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        currentOnProgress(newProgress)
                    }
                }
                setDownloadListener { url, userAgent, _, _, contentLength ->
                    currentOnCapture(
                        key.toDownload(
                            capturedUrl = url,
                            headers = buildMap {
                                CookieManager.getInstance().getCookie(url)?.let { put("Cookie", it) }
                                userAgent?.takeIf { it.isNotBlank() }?.let { put("User-Agent", it) }
                            },
                            sizeBytes = contentLength
                        )
                    )
                }

                val loginUrl = key.loginUrl
                val loginBody = key.loginFormBody
                if (loginUrl != null && loginBody != null) {
                    postUrl(loginUrl, loginBody.toByteArray())
                } else {
                    loadUrl(key.url)
                }
                onAttachWebView(this)
            }
        },
        onRelease = { it.destroy() }
    )
}

@Composable
private fun resultText(result: BrowserResult): String = when (result) {
    is BrowserResult.Queued -> stringResource(R.string.feature_browser_queued, result.title)
    is BrowserResult.AlreadyInLibrary -> stringResource(R.string.feature_browser_in_library, result.title)
    is BrowserResult.AlreadyQueued -> stringResource(R.string.feature_browser_already_queued, result.title)
    is BrowserResult.Failed -> stringResource(R.string.feature_browser_failed, result.message)
}
