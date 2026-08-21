package dev.readana.core.data.browser

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.readana.source.api.BrowserVerifier
import dev.readana.source.api.VerifyOutcome
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Answers a site's "checking your browser" page in a WebView the user never sees.
 *
 * The page is a JavaScript puzzle: load it, let it run, and the site leaves a cookie behind. That
 * cookie lands in the system store the HTTP client also reads (see [WebViewCookieJar]), so the
 * request that hit the gate can simply be repeated. The WebView sends the same user agent as every
 * other request, which is what makes the cookie count for them.
 *
 * Only one check runs at a time — several searches meeting the same gate should wait for one
 * answer, not open a WebView each.
 */
@Singleton
class WebViewBrowserVerifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val identity: BrowserIdentity,
    private val cookieJar: WebViewCookieJar
) : BrowserVerifier,
    BrowserVerificationStatus {
    private val busy = MutableStateFlow(false)
    override val verifying: StateFlow<Boolean> = busy.asStateFlow()

    private val gate = Mutex()

    override suspend fun verify(url: String): VerifyOutcome = gate.withLock {
        busy.value = true
        try {
            withContext(Dispatchers.Main.immediate) {
                withTimeoutOrNull(TIMEOUT_MS) { load(url) } ?: VerifyOutcome.Failed("timed out")
            }
        } finally {
            busy.value = false
        }
    }

    private suspend fun load(url: String): VerifyOutcome {
        var webView: WebView? = null
        return try {
            suspendCancellableCoroutine { continuation ->
                val view = create(continuation) ?: return@suspendCancellableCoroutine
                webView = view
                view.webViewClient = VerifyingClient { outcome ->
                    if (outcome is VerifyOutcome.Verified) cookieJar.flush()
                    continuation.resumeOnce(outcome)
                }
                view.loadUrl(url)
            }
        } finally {
            // Both on success and when the timeout cancels us; we are still on the main thread.
            webView?.stopLoading()
            webView?.destroy()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(continuation: CancellableContinuation<VerifyOutcome>): WebView? {
        val webView = try {
            WebView(context)
        } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
            // No WebView on this device, or it is mid-update. Nothing to verify with.
            Timber.w(e, "No WebView to answer the browser check with")
            continuation.resume(VerifyOutcome.Unavailable)
            return null
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = identity.userAgent
            // The check needs scripts and cookies, never pictures.
            blockNetworkImage = true
            loadsImagesAutomatically = false
        }
        return webView
    }

    /**
     * The check passes when the site finally serves a real page: a main-frame load that finished
     * without an HTTP error and no longer shows the challenge. The site sends its answer back to
     * the same URL, so the address cannot be the signal — the title and the status are.
     */
    private class VerifyingClient(private val onSettled: (VerifyOutcome) -> Unit) : WebViewClient() {
        private var lastMainFrameError: Int? = null

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            if (request.isForMainFrame) lastMainFrameError = errorResponse.statusCode
        }

        override fun onPageFinished(view: WebView, url: String?) {
            val failure = lastMainFrameError
            lastMainFrameError = null
            val title = view.title.orEmpty()
            when {
                CHALLENGE_TITLES.any { title.contains(it, ignoreCase = true) } -> Unit
                failure != null -> Unit
                else -> onSettled(VerifyOutcome.Verified)
            }
        }

        private companion object {
            val CHALLENGE_TITLES = listOf("DDoS-Guard", "Just a moment", "Attention Required")
        }
    }

    private fun CancellableContinuation<VerifyOutcome>.resumeOnce(outcome: VerifyOutcome) {
        if (isActive) resume(outcome)
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
