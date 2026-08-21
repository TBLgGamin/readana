package dev.readana.core.data.browser

import android.content.Context
import android.webkit.WebSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * The one User-Agent the app speaks with.
 *
 * Shadow-library front-ends tie the cookie that clears their bot check to the client that earned
 * it, so the HTTP client and every WebView must be indistinguishable. Taking the string from the
 * system WebView guarantees that: whatever solves a check in a WebView is exactly what later
 * downloads the file.
 */
@Singleton
class BrowserIdentity @Inject constructor(@ApplicationContext private val context: Context) {
    val userAgent: String by lazy { resolve() }

    @Suppress("TooGenericExceptionCaught")
    private fun resolve(): String = try {
        WebSettings.getDefaultUserAgent(context)
    } catch (e: RuntimeException) {
        // A device whose WebView is missing or mid-update throws; a plausible string beats none,
        // and the in-app browser is unusable on such a device anyway.
        Timber.w(e, "No system WebView user agent; falling back to a generic one")
        FALLBACK
    }

    private companion object {
        const val FALLBACK =
            "Mozilla/5.0 (Linux; Android 16; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/139.0.0.0 Mobile Safari/537.36"
    }
}
