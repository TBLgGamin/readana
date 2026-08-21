package dev.readana.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.readana.core.data.browser.BrowserVerificationStatus
import dev.readana.core.data.browser.WebViewBrowserVerifier
import dev.readana.source.api.BrowserVerifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BrowserModule {
    @Binds
    @Singleton
    abstract fun bindsBrowserVerifier(verifier: WebViewBrowserVerifier): BrowserVerifier

    @Binds
    @Singleton
    abstract fun bindsVerificationStatus(verifier: WebViewBrowserVerifier): BrowserVerificationStatus
}
