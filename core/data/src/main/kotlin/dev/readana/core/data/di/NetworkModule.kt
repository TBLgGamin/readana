package dev.readana.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.readana.core.data.browser.BrowserIdentity
import dev.readana.core.data.browser.UserAgentInterceptor
import dev.readana.core.data.browser.WebViewCookieJar
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val CONNECT_TIMEOUT_MS = 15_000L
    private const val REQUEST_TIMEOUT_MS = 60_000L

    /** A thinking model can be quiet for a while before the first token; OkHttp defaults to 10 s. */
    private const val AI_SOCKET_TIMEOUT_MS = 120_000L

    /**
     * One client for search, downloads and cover art. It carries the system WebView's cookies and
     * user agent, so a bot check the user (or the hidden verifier) has cleared in a WebView also
     * counts for the plain requests that follow — see [WebViewCookieJar].
     */
    @Provides
    @Singleton
    fun providesHttpClient(cookieJar: WebViewCookieJar, identity: BrowserIdentity): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        followRedirects = true
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
        }
        engine {
            // Ktor follows redirects itself, one engine call per hop, so both of these apply
            // to every hop of a redirect chain.
            addInterceptor(UserAgentInterceptor(identity))
            config { cookieJar(cookieJar) }
        }
    }

    /**
     * A second client, for the reading assistant only. It deliberately shares nothing with the one
     * above: no WebView cookies and no browser user agent go to an AI vendor, and there is no
     * overall request timeout, because an answer streams for as long as it takes to write.
     */
    @Provides
    @Singleton
    @AiHttp
    fun providesAiHttpClient(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = AI_SOCKET_TIMEOUT_MS
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        }
    }
}
