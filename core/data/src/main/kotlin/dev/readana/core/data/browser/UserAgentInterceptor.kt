package dev.readana.core.data.browser

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Stamps every request with the app's single [BrowserIdentity] user agent, replacing whatever was
 * set before. Callers must not set their own: a bot check that was cleared by the WebView is only
 * honoured for the client that cleared it, and a per-request agent would quietly break it.
 */
class UserAgentInterceptor(private val identity: BrowserIdentity) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(
        chain.request().newBuilder().header(HEADER, identity.userAgent).build()
    )

    private companion object {
        const val HEADER = "User-Agent"
    }
}
