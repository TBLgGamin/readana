package dev.readana.core.data.importing

import android.graphics.Bitmap
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asSuccess
import dev.readana.core.data.readium.ReadiumPublicationOpener
import dev.readana.core.model.BookSeries
import javax.inject.Inject
import javax.inject.Singleton
import org.readium.r2.shared.publication.services.cover

/** What an EPUB or PDF tells us about itself. Every field may be missing. */
data class ExtractedMetadata(
    val title: String?,
    val authors: List<String>,
    val language: String?,
    val cover: Bitmap?,
    /** The series the publisher put the book in, when it says so. */
    val series: BookSeries? = null
)

/** Reads metadata out of a book file. Separate from the importer so imports are testable. */
fun interface BookMetadataExtractor {
    suspend fun extract(fileUri: String): AppResult<ExtractedMetadata>
}

@Singleton
class ReadiumMetadataExtractor @Inject constructor(private val publicationOpener: ReadiumPublicationOpener) :
    BookMetadataExtractor {
    override suspend fun extract(fileUri: String): AppResult<ExtractedMetadata> =
        when (val opened = publicationOpener.open(fileUri)) {
            is AppResult.Failure -> opened

            is AppResult.Success -> {
                val publication = opened.value
                try {
                    ExtractedMetadata(
                        title = publication.metadata.title?.trim()?.ifEmpty { null },
                        authors = publication.metadata.authors
                            .map { it.name.trim() }
                            .filter { it.isNotEmpty() },
                        language = publication.metadata.languages.firstOrNull(),
                        cover = publication.cover(),
                        series = publication.metadata.belongsToSeries.firstOrNull()?.let { series ->
                            series.name.trim().takeIf { it.isNotEmpty() }?.let { name ->
                                BookSeries(name = name, index = series.position?.toFloat())
                            }
                        }
                    ).asSuccess()
                } finally {
                    publication.close()
                }
            }
        }
}
