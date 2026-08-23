package dev.readana.source.arxiv

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
import dev.readana.source.api.SourceConfigField
import dev.readana.source.api.SourceId
import dev.readana.source.opds.AtomXml
import dev.readana.source.opds.AtomXml.childText
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException

/**
 * arXiv preprints. Every paper is a PDF, which the app's own PDF → EPUB conversion turns into
 * something that reflows on a tablet, so a paper reads like anything else in the library.
 *
 * Off by default: a paper search is noise in a search for books unless it was asked for.
 */
class ArxivSource(
    private val client: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val config: suspend () -> Map<String, String> = { emptyMap() }
) : BookSource {
    override val id: SourceId = ID
    override val displayName: String = "arXiv"

    override val summary: String = "Open-access preprints, converted from PDF."

    override val enabledByDefault: Boolean = false

    override val configFields: List<SourceConfigField> = CONFIG_FIELDS

    override suspend fun search(query: SearchQuery): AppResult<SearchPage> {
        // A paper is only ever a PDF; a search that rules PDFs out has nothing to ask arXiv for.
        if (BookFormat.PDF !in query.formats) {
            return SearchPage(emptyList(), query.page, hasMore = false).asSuccess()
        }

        val response = fetch {
            client.get("$baseUrl/api/query") {
                parameter("search_query", searchExpression(query.text, categories()))
                parameter("start", (query.page - 1) * PAGE_SIZE)
                parameter("max_results", PAGE_SIZE)
                parameter("sortBy", "relevance")
            }
        } ?: return AppError.Network("Could not reach arXiv").asFailure()

        if (!response.status.isSuccess()) return AppError.Http(response.status.value).asFailure()

        return try {
            parse(response.bodyAsText(), query).asSuccess()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            AppError.Parse("arXiv returned a feed that could not be read", error).asFailure()
        }
    }

    override suspend fun resolveDownload(result: SearchResult): AppResult<DownloadRequest> {
        val pdf = result.extras[EXTRA_PDF_URL]
            ?: return AppError.NotFound("No PDF for ${result.title}").asFailure()
        return DownloadRequest(
            url = pdf,
            format = BookFormat.PDF,
            suggestedFileName = FileNames.suggest(result.title, result.authors, BookFormat.PDF)
        ).asSuccess()
    }

    private fun parse(xml: String, query: SearchQuery): SearchPage {
        val document = AtomXml.parse(xml, baseUrl)
        val total = document.selectFirst("opensearch|totalResults")?.text()?.trim()?.toIntOrNull() ?: 0
        val entries = document.select("entry").mapNotNull { entry ->
            val title = entry.childText("title") ?: return@mapNotNull null
            val absoluteId = entry.childText("id") ?: return@mapNotNull null
            val pdf = entry.select("link")
                .firstOrNull { it.attr("title") == "pdf" || it.attr("type") == PDF_MEDIA_TYPE }
                ?.let { AtomXml.resolve(baseUrl, it.attr("href")) }
                // arXiv always serves /pdf/<id>, whether or not the entry links it.
                ?: absoluteId.replace("/abs/", "/pdf/")

            SearchResult(
                sourceId = id,
                recordId = absoluteId.substringAfterLast('/'),
                // Abstract titles are wrapped across lines in the feed; a book list wants one line.
                title = title.collapseWhitespace(),
                authors = entry.select("author > name").map { it.text().trim() },
                year = AtomXml.yearOf(entry.childText("published")),
                format = BookFormat.PDF,
                description = entry.childText("summary")?.collapseWhitespace(),
                detailUrl = absoluteId,
                extras = mapOf(EXTRA_PDF_URL to pdf)
            )
        }
        return SearchPage(
            results = entries,
            page = query.page,
            hasMore = (query.page - 1) * PAGE_SIZE + entries.size < total
        )
    }

    /**
     * arXiv's query language, not a plain string: `all:<terms>`, narrowed to the categories the
     * user configured. Without the narrowing a general word returns papers from every field.
     */
    private fun searchExpression(text: String, categories: List<String>): String {
        val terms = "all:${text.trim()}"
        if (categories.isEmpty()) return terms
        val scope = categories.joinToString(" OR ") { "cat:$it" }
        return "$terms AND ($scope)"
    }

    private suspend fun categories(): List<String> = config()[KEY_CATEGORIES]
        .orEmpty()
        .split(',', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    private fun String.collapseWhitespace(): String = replace(WHITESPACE, " ").trim()

    @Suppress("TooGenericExceptionCaught")
    private suspend inline fun fetch(request: () -> HttpResponse): HttpResponse? = try {
        request()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    companion object {
        val ID = SourceId("arxiv")
        const val DEFAULT_BASE_URL = "https://export.arxiv.org"

        internal const val EXTRA_PDF_URL = "pdfUrl"
        internal const val KEY_CATEGORIES = "categories"
        private const val PAGE_SIZE = 20
        private const val PDF_MEDIA_TYPE = "application/pdf"
        private val WHITESPACE = Regex("\\s+")

        val CONFIG_FIELDS: List<SourceConfigField> = listOf(
            SourceConfigField(
                key = KEY_CATEGORIES,
                label = "Categories",
                kind = SourceConfigField.Kind.Text,
                help = "Comma-separated arXiv categories to search. Leave empty to search all of it.",
                placeholder = "cs.AI, cs.LG"
            )
        )
    }
}
