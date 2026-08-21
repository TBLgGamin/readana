package dev.readana.core.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.readana.core.data.conversion.toExtractionOptions
import dev.readana.core.data.repository.SettingsRepository
import dev.readana.engine.pdf2epub.PdfBoxContentExtractor
import dev.readana.engine.pdf2epub.PdfToEpubConverter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@Module
@InstallIn(SingletonComponent::class)
object ConversionModule {
    /**
     * Built per conversion, because the extractor is configured from the current settings and
     * remembers the warnings from its last run. `PDFBoxResourceLoader.init` is idempotent, so
     * constructing one is cheap.
     */
    @Provides
    fun providesConverter(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): PdfToEpubConverter {
        // Already on the IO dispatcher: DefaultBookConverter resolves this inside withContext.
        val settings = runBlocking { settingsRepository.conversionSettings.first() }
        return PdfToEpubConverter(PdfBoxContentExtractor(context, settings.toExtractionOptions()))
    }
}
