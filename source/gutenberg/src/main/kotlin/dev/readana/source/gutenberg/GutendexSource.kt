package dev.readana.source.gutenberg

import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.common.result.asSuccess
import dev.readana.core.model.BookFormat
import dev.readana.source.api.BookSource
import dev.readana.source.api.DownloadRequest
import dev.readana.source.api.FileNames
import dev.readana.source.api.SearchPage
import dev.readana.source.api.SearchQuery
import dev.readana.source.api.SearchResult
import dev.readana.source.api.SourceId
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Project Gutenberg via the Gutendex JSON API (https://gutendex.com). Public-domain books only,
 * which makes it the reference implementation and the default source in tests and demos.
 */
class GutendexSource(private val client: HttpClient, private val baseUrl: String = DEFAULT_BASE_URL) : BookSource {
    override val id: SourceId = ID
    override val displayName: String = "Project Gutenberg"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: SearchQuery): AppResult<SearchPage> {
        val response = fetch {
            client.get("$baseUrl/books/") {
                parameter("search", query.text)
                query.language?.let { parameter("languages", it) }
                parameter("page", query.page)
            }
        } ?: return AppError.Network("Project Gutenberg is unreachable").asFailure()

        // Gutendex answers 404 for a page past the end; treat that as an empty page, not an error.
        if (response.status == HttpStatusCode.NotFound) {
            return SearchPage(emptyList(), query.page, hasMore = false).asSuccess()
        }
        if (!response.status.isSuccess()) return AppError.Http(response.status.value).asFailure()

        val payload = try {
            json.decodeFromString<GutendexResponse>(response.bodyAsText())
        } catch (e: SerializationException) {
            return AppError.Parse("Unexpected Gutendex response", e).asFailure()
        } catch (e: IllegalArgumentException) {
            return AppError.Parse("Unexpected Gutendex response", e).asFailure()
        }

        val results = payload.results.mapNotNull { it.toSearchResult(query.formats) }
        return SearchPage(results, query.page, hasMore = payload.next != null).asSuccess()
    }

    override suspend fun resolveDownload(result: SearchResult): AppResult<DownloadRequest> {
        val format = result.format
            ?: return AppError.Unsupported(
                "No EPUB or PDF available for \"${result.title}\""
            ).asFailure()
        val url = result.extras[EXTRA_DOWNLOAD_URL]
            ?: return AppError.NotFound(
                "No download URL recorded for Gutenberg #${result.recordId}"
            ).asFailure()
        return DownloadRequest(
            url = url,
            format = format,
            suggestedFileName = FileNames.suggest(result.title, result.authors, format)
        ).asSuccess()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend inline fun fetch(request: () -> HttpResponse): HttpResponse? = try {
        request()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    private fun GutendexBook.toSearchResult(wanted: Set<BookFormat>): SearchResult? {
        val (format, url) = FORMAT_PREFERENCE
            .filter { (format, _) -> format in wanted }
            .firstNotNullOfOrNull { (format, mediaType) ->
                formats[mediaType]?.let { format to it }
            }
            ?: return null
        return SearchResult(
            sourceId = ID,
            recordId = id.toString(),
            title = title,
            authors = authors.map { it.displayName },
            language = languages.firstOrNull(),
            format = format,
            coverUrl = formats[COVER_MEDIA_TYPE],
            description = summaries.firstOrNull(),
            detailUrl = "https://www.gutenberg.org/ebooks/$id",
            extras = mapOf(EXTRA_DOWNLOAD_URL to url)
        )
    }

    companion object {
        val ID = SourceId("gutenberg")
        const val DEFAULT_BASE_URL = "https://gutendex.com"
        internal const val EXTRA_DOWNLOAD_URL = "downloadUrl"
        private const val COVER_MEDIA_TYPE = "image/jpeg"
        private val FORMAT_PREFERENCE = listOf(
            BookFormat.EPUB to "application/epub+zip",
            BookFormat.PDF to "application/pdf"
        )
    }
}
