package dev.readana.feature.reader

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.common.result.asSuccess
import javax.inject.Inject
import javax.inject.Singleton
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/**
 * Opens EPUB/PDF files into Readium [Publication]s. The navigator (the actual reading view)
 * builds on top of this in milestone M2.
 */
@Singleton
class ReadiumPublicationOpener @Inject constructor(@ApplicationContext private val context: Context) {
    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context = context,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = PdfiumDocumentFactory(context)
        )
    )

    suspend fun open(fileUri: String): AppResult<Publication> {
        val url = AbsoluteUrl(fileUri)
            ?: return AppError.Unsupported("Not an absolute URL: $fileUri").asFailure()
        val asset = assetRetriever.retrieve(url)
            .getOrElse { return AppError.Parse("Cannot read $fileUri: ${it.message}").asFailure() }
        val publication = publicationOpener.open(asset, allowUserInteraction = false)
            .getOrElse {
                return AppError.Parse("Cannot open publication: ${it.message}").asFailure()
            }
        return publication.asSuccess()
    }
}
