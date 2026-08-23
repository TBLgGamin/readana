package dev.readana.source.openlibrary

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
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Open Library search, with the file coming from the Internet Archive scan behind it.
 *
 * Most of what the Internet Archive holds is lending-only: borrowing hands back an ACSM licence
 * that needs Adobe DRM, which this app cannot open. Rather than list those and fail at download
 * time, the source only returns works whose `ebook_access` is `public` — the scans anyone may
 * simply download.
 */
class OpenLibrarySource(
    private val client: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val archiveUrl: String = DEFAULT_ARCHIVE_URL,
    private val coversUrl: String = DEFAULT_COVERS_URL
) : BookSource {
    override val id: SourceId = ID
    override val displayName: String = "Open Library"

    override val summary: String = "Public-domain scans from the Internet Archive."

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: SearchQuery): AppResult<SearchPage> {
        val response = fetch {
            client.get("$baseUrl/search.json") {
                parameter("q", query.text)
                parameter("page", query.page)
                parameter("limit", PAGE_SIZE)
                // Asking for only the fields used keeps a search of a common word from pulling
                // several megabytes of unrelated metadata over a tablet's connection.
                parameter("fields", REQUESTED_FIELDS)
            }
        } ?: return AppError.Network("Could not reach Open Library").asFailure()

        if (!response.status.isSuccess()) return AppError.Http(response.status.value).asFailure()

        val payload = try {
            json.decodeFromString<SearchResponse>(response.bodyAsText())
        } catch (error: SerializationException) {
            return AppError.Parse("Open Library returned something unreadable", error).asFailure()
        } catch (error: IllegalArgumentException) {
            return AppError.Parse("Open Library returned something unreadable", error).asFailure()
        }

        val results = payload.docs.mapNotNull { it.toSearchResult() }
        return SearchPage(
            results = results,
            page = query.page,
            // Counted against everything the query matched, not against what survived filtering:
            // a page of entirely lending-only works still has pages after it worth fetching.
            hasMore = payload.start + payload.docs.size < payload.numFound
        ).asSuccess()
    }

    /**
     * Reads the archive item's file list and picks the best copy. Archive derivatives are named
     * inconsistently (`x.epub`, `x_text.pdf`, `x_djvu.txt`), so the list is the only reliable way
     * to know what is actually there. EPUB beats PDF: it reflows, and it skips conversion.
     */
    override suspend fun resolveDownload(result: SearchResult): AppResult<DownloadRequest> {
        val itemId = result.extras[EXTRA_ARCHIVE_ID]
            ?: return AppError.NotFound("No Internet Archive scan for ${result.title}").asFailure()

        val metadata = when (val listing = archiveMetadata(itemId)) {
            is AppResult.Failure -> return listing
            is AppResult.Success -> listing.value
        }

        val file = metadata.files.pickBest()
            ?: return AppError.NotFound("No downloadable file for ${result.title}").asFailure()
        val format = BookFormat.fromExtension(file.name.orEmpty().substringAfterLast('.'))
            ?: return AppError.Unsupported("Unsupported file for ${result.title}").asFailure()

        return DownloadRequest(
            url = "$archiveUrl/download/$itemId/${file.name}",
            format = format,
            suggestedFileName = FileNames.suggest(result.title, result.authors, format),
            expectedSizeBytes = file.size?.toLongOrNull()
        ).asSuccess()
    }

    private suspend fun archiveMetadata(itemId: String): AppResult<ArchiveMetadata> {
        val response = fetch { client.get("$archiveUrl/metadata/$itemId") }
            ?: return AppError.Network("Could not reach the Internet Archive").asFailure()
        if (!response.status.isSuccess()) return AppError.Http(response.status.value).asFailure()
        return try {
            json.decodeFromString<ArchiveMetadata>(response.bodyAsText()).asSuccess()
        } catch (error: SerializationException) {
            AppError.Parse("The Internet Archive listing could not be read", error).asFailure()
        } catch (error: IllegalArgumentException) {
            AppError.Parse("The Internet Archive listing could not be read", error).asFailure()
        }
    }

    private fun List<ArchiveFile>.pickBest(): ArchiveFile? {
        val candidates = filter { it.name?.substringAfterLast('.')?.lowercase() in DOWNLOADABLE }
        return candidates.firstOrNull { it.name?.endsWith(".epub", ignoreCase = true) == true }
            ?: candidates.firstOrNull()
    }

    private fun SearchDoc.toSearchResult(): SearchResult? {
        val name = title?.takeIf { it.isNotBlank() } ?: return null
        val work = key?.takeIf { it.isNotBlank() } ?: return null
        // Lending-only and print-disabled scans cannot be downloaded, so they are not offered.
        if (ebookAccess != ACCESS_PUBLIC) return null
        val item = ia.firstOrNull() ?: return null
        return SearchResult(
            sourceId = id,
            recordId = work.removePrefix("/works/"),
            title = name,
            authors = authorName,
            year = firstPublishYear,
            language = language.firstOrNull(),
            // The archive item may hold either; which one is only known once its file list is
            // read, so the format is settled at download time.
            format = BookFormat.PDF,
            coverUrl = coverId?.let { "$coversUrl/b/id/$it-M.jpg" },
            publisher = publisher.firstOrNull(),
            detailUrl = "$baseUrl$work",
            // The download worker rebuilds the result from its database row, so the archive id
            // has to travel in extras or a retry has nothing to fetch.
            extras = mapOf(EXTRA_ARCHIVE_ID to item)
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend inline fun fetch(request: () -> HttpResponse): HttpResponse? = try {
        request()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    companion object {
        val ID = SourceId("openlibrary")
        const val DEFAULT_BASE_URL = "https://openlibrary.org"
        const val DEFAULT_ARCHIVE_URL = "https://archive.org"
        const val DEFAULT_COVERS_URL = "https://covers.openlibrary.org"

        internal const val EXTRA_ARCHIVE_ID = "archiveId"
        private const val ACCESS_PUBLIC = "public"
        private const val PAGE_SIZE = 20
        private const val REQUESTED_FIELDS =
            "key,title,author_name,first_publish_year,cover_i,language,publisher,ia,ebook_access"
        private val DOWNLOADABLE = setOf("epub", "pdf")
    }
}
