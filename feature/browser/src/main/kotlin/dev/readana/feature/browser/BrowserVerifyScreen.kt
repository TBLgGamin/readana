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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.component.WavyProgress

/**
 * Shows the site's "checking your browser" page so the user can answer it themselves.
 *
 * This is the fallback for when the check could not be cleared out of sight — the device has no
 * WebView to spare, or the page wants a tap. Whatever the page hands out lands in the shared
 * cookie store, so going back and searching again simply works.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserVerifyScreen(key: BrowserVerifyKey, onDone: () -> Unit, modifier: Modifier = Modifier) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    val currentOnDone by rememberUpdatedState(onDone)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(key.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            ReadanaIcons.Back,
                            contentDescription = stringResource(R.string.feature_browser_close)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { currentOnDone() }) {
                        Text(stringResource(R.string.feature_browser_verify_done))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (progress in 1..99) {
                WavyProgress(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
            Text(
                text = stringResource(R.string.feature_browser_verify_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            VerifyWebView(
                url = key.url,
                onProgress = { progress = it },
                onAttachWebView = { webView = it },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    BackHandler(enabled = webView?.canGoBack() == true) { webView?.goBack() }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun VerifyWebView(
    url: String,
    onProgress: (Int) -> Unit,
    onAttachWebView: (WebView) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnProgress by rememberUpdatedState(onProgress)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        // Whatever the page granted is in the shared store from here on.
                        CookieManager.getInstance().flush()
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        currentOnProgress(newProgress)
                    }
                }
                loadUrl(url)
                onAttachWebView(this)
            }
        },
        onRelease = { it.destroy() }
    )
}
