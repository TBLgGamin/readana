package dev.readana.core.data.browser

import android.webkit.CookieManager
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import timber.log.Timber

/**
 * Backs the HTTP client's cookie jar with the system WebView's cookie store.
 *
 * Sites that gate access behind a JavaScript check hand out a cookie only a browser can earn.
 * Sharing one store means the in-app browser, the hidden verifier and every plain HTTP request
 * present the same session, so a check solved once counts for all of them — and the system
 * persists it across restarts. Cookies stay scoped to the host that set them, as they would in
 * a browser.
 */
@Singleton
class WebViewCookieJar @Inject constructor() : CookieJar {
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val stored = manager()?.getCookie(url.toString()) ?: return emptyList()
        return stored.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val manager = manager() ?: return
        val target = url.toString()
        // Cookie.toString() renders the attributes back verbatim, so signed values survive intact.
        cookies.forEach { manager.setCookie(target, it.toString()) }
    }

    /** Writes the store to disk; worth doing after something expensive, such as a bot check. */
    fun flush() {
        manager()?.flush()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun manager(): CookieManager? = try {
        CookieManager.getInstance()
    } catch (e: RuntimeException) {
        // No WebView on this device. Cookie-less requests still work for sources that need none,
        // so this must not take the rest of the app's networking down with it.
        Timber.w(e, "No system cookie store available")
        null
    }
}
