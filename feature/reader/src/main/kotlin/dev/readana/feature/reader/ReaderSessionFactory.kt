package dev.readana.feature.reader

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asSuccess
import dev.readana.core.data.readium.ReadiumPublicationOpener
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import dev.readana.core.model.ReaderSettings
import javax.inject.Inject
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.search.isSearchable
import org.readium.r2.shared.publication.services.search.search

/** Opens a book and builds the navigator that will render it. */
internal interface ReaderSessionFactory {
    suspend fun create(
        book: Book,
        initialLocator: Locator?,
        settings: ReaderSettings,
        systemInDarkTheme: Boolean
    ): AppResult<ReaderSession>
}

internal class ReadiumReaderSessionFactory @Inject constructor(
    private val publicationOpener: ReadiumPublicationOpener
) : ReaderSessionFactory {
    override suspend fun create(
        book: Book,
        initialLocator: Locator?,
        settings: ReaderSettings,
        systemInDarkTheme: Boolean
    ): AppResult<ReaderSession> {
        val publication = when (val opened = publicationOpener.open(book.fileUri)) {
            is AppResult.Failure -> return opened
            is AppResult.Success -> opened.value
        }
        val session = when (book.format) {
            BookFormat.EPUB -> epubSession(book, publication, initialLocator, settings, systemInDarkTheme)
            BookFormat.PDF -> pdfSession(book, publication, initialLocator, settings)
        }
        return session.asSuccess()
    }

    private suspend fun epubSession(
        book: Book,
        publication: Publication,
        initialLocator: Locator?,
        settings: ReaderSettings,
        systemInDarkTheme: Boolean
    ): ReaderSession {
        val factory = EpubNavigatorFactory(publication).createFragmentFactory(
            initialLocator = initialLocator,
            initialPreferences = settings.toEpubPreferences(systemInDarkTheme)
        )
        return session(book, publication, EpubNavigatorFragment::class.java, factory)
    }

    private suspend fun pdfSession(
        book: Book,
        publication: Publication,
        initialLocator: Locator?,
        settings: ReaderSettings
    ): ReaderSession {
        val factory = PdfNavigatorFactory(publication, PdfiumEngineProvider())
            .createFragmentFactory(
                initialLocator = initialLocator,
                initialPreferences = settings.toPdfiumPreferences()
            )
        return session(book, publication, PdfNavigatorFragment::class.java, factory)
    }

    private suspend fun session(
        book: Book,
        publication: Publication,
        navigatorClass: Class<out androidx.fragment.app.Fragment>,
        factory: androidx.fragment.app.FragmentFactory
    ) = ReaderSession(
        bookId = book.id,
        title = publication.metadata.title ?: book.title,
        navigatorClass = navigatorClass,
        fragmentFactory = factory,
        toc = publication.tableOfContents.flatten(depth = 0),
        positions = publication.positions(),
        isSearchable = publication.isSearchable,
        search = { query -> publication.search(query) },
        onClose = { publication.close() }
    )

    private fun List<Link>.flatten(depth: Int): List<TocEntry> = flatMap { link ->
        val entry = link.title?.trim()?.takeIf { it.isNotEmpty() }?.let { title ->
            listOf(TocEntry(title = title, depth = depth, link = link))
        } ?: emptyList()
        entry + link.children.flatten(depth + 1)
    }
}

@Module
@InstallIn(ViewModelComponent::class)
internal abstract class ReaderModule {
    @Binds
    abstract fun bindsReaderSessionFactory(impl: ReadiumReaderSessionFactory): ReaderSessionFactory
}
