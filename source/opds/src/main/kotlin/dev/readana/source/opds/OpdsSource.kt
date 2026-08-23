package dev.readana.source.opds

import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.common.result.asSuccess
import dev.readana.source.api.CatalogEntry
import dev.readana.source.api.CatalogPage
import dev.readana.source.api.CatalogSource
import dev.readana.source.api.DownloadRequest
import dev.readana.source.api.FileNames
import dev.readana.source.api.SearchPage
import dev.readana.source.api.SearchQuery
import dev.readana.source.api.SearchResult
import dev.readana.source.api.SourceConfigField
import dev.readana.source.api.SourceId
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlinx.serialization.SerializationException

/**
 * One OPDS catalog, in either flavour: 1.2 (Atom) or 2.0 (JSON). Which one a feed is need not be
 * configured — it is decided from the response's content type, falling back to sniffing the first
 * non-space character, because plenty of servers label an OPDS 2.0 feed `application/json` and
 * some label an Atom feed `text/xml`.
 *
 * A catalog is data rather than code: the user adds one and the registry builds a source for it,
 * so there is no module and no DI provider per catalog.
 */
class OpdsSource(
    private val client: HttpClient,
    private val catalogId: String,
    private val catalogName: String,
    private val feedUrl: String,
    private val config: suspend () -> Map<String, String> = { emptyMap() }
) : CatalogSource {
    override val id: SourceId = SourceId(catalogId)
    override val displayName: String = catalogName
    override val summary: String = feedUrl

    /** A catalog the user went to the trouble of adding is on unless they say otherwise. */
    override val enabledByDefault: Boolean = true

    override val configFields: List<SourceConfigField> = CONFIG_FIELDS

    override suspend fun search(query: SearchQuery): AppResult<SearchPage> {
        val root = when (val feed = fetchFeed(feedUrl)) {
            is AppResult.Failure -> return feed
            is AppResult.Success -> feed.value
        }
        // Feeds that publish no search link cannot be queried; browsing is all they offer, and
        // saying so beats returning an empty page that looks like "no matches".
        val template = root.searchTemplate
            ?: return AppError.Unsupported("$catalogName cannot be searched, only browsed").asFailure()

        val url = template.fillSearchTemplate(query.text)
        return when (val feed = fetchFeed(url)) {
            is AppResult.Failure -> feed
            is AppResult.Success -> feed.value.toSearchPage(query).asSuccess()
        }
    }

    override suspend fun browse(feedUrl: String?): AppResult<CatalogPage> =
        when (val feed = fetchFeed(feedUrl ?: this.feedUrl)) {
            is AppResult.Failure -> feed

            is AppResult.Success -> {
                val value = feed.value
                CatalogPage(
                    title = value.title.ifEmpty { catalogName },
                    entries = value.entries.mapNotNull { it.toCatalogEntry() },
                    nextHref = value.nextHref
                ).asSuccess()
            }
        }

    override suspend fun resolveDownload(result: SearchResult): AppResult<DownloadRequest> {
        val href = result.extras[EXTRA_HREF]
            ?: return AppError.NotFound("No download link for ${result.title}").asFailure()
        val format = result.format
            ?: return AppError.Unsupported("Unsupported format for ${result.title}").asFailure()
        return DownloadRequest(
            url = href,
            format = format,
            suggestedFileName = FileNames.suggest(result.title, result.authors, format),
            headers = authHeader()?.let { mapOf(HttpHeaders.Authorization to it) }.orEmpty(),
            expectedSizeBytes = result.sizeBytes
        ).asSuccess()
    }

    private suspend fun fetchFeed(url: String): AppResult<OpdsFeed> {
        val response = try {
            client.get(url) { authHeader()?.let { header(HttpHeaders.Authorization, it) } }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            return AppError.Network("Could not reach $catalogName", error).asFailure()
        }
        if (!response.status.isSuccess()) return response.status.toError().asFailure()

        val body = response.bodyAsText()
        return try {
            parse(body, response, url).asSuccess()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: SerializationException) {
            AppError.Parse("$catalogName returned a feed that could not be read", error).asFailure()
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            AppError.Parse("$catalogName returned a feed that could not be read", error).asFailure()
        }
    }

    private fun parse(body: String, response: HttpResponse, url: String): OpdsFeed {
        val contentType = response.headers[HttpHeaders.ContentType].orEmpty().lowercase()
        val looksJson = contentType.contains("json") ||
            (!contentType.contains("xml") && body.trimStart().startsWith("{"))
        return if (looksJson) OpdsJsonParser.parse(body, url) else OpdsAtomParser.parse(body, url)
    }

    private fun HttpStatusCode.toError(): AppError = when (value) {
        HttpStatusCode.Unauthorized.value, HttpStatusCode.Forbidden.value ->
            AppError.Unauthorized("$catalogName needs a username and password")

        HttpStatusCode.NotFound.value -> AppError.NotFound("$catalogName has no feed at that address")

        else -> AppError.Http(value)
    }

    private suspend fun authHeader(): String? {
        val values = config()
        val user = values[KEY_USERNAME]?.takeIf { it.isNotBlank() } ?: return null
        val password = values[KEY_PASSWORD].orEmpty()
        return "Basic " + Base64.Default.encode("$user:$password".toByteArray())
    }

    private fun OpdsFeed.toSearchPage(query: SearchQuery): SearchPage {
        val results = entries
            .mapNotNull { it.toSearchResult() }
            .filter { it.format in query.formats }
        return SearchPage(
            results = results,
            page = query.page,
            hasMore = nextHref != null,
            nextHref = nextHref
        )
    }

    private fun OpdsEntry.toCatalogEntry(): CatalogEntry? = when {
        navigationHref != null -> CatalogEntry.Section(title, navigationHref, summary)
        else -> toSearchResult()?.let(CatalogEntry::Publication)
    }

    /**
     * Entries whose acquisition link is missing, or points at something this app cannot open
     * (mobi, cbz, an audiobook), are dropped rather than listed as a download that would fail.
     */
    private fun OpdsEntry.toSearchResult(): SearchResult? {
        val link = acquisition ?: return null
        val format = OpdsLinks.formatOf(link.mimeType) ?: return null
        return SearchResult(
            sourceId = this@OpdsSource.id,
            recordId = id,
            title = title,
            authors = authors,
            year = year,
            language = language,
            format = format,
            sizeBytes = link.sizeBytes,
            coverUrl = coverUrl,
            publisher = publisher,
            description = summary,
            detailUrl = detailUrl,
            // The download worker rebuilds the result from its database row, so the href has to
            // travel in extras or the retry has nothing to fetch.
            extras = mapOf(EXTRA_HREF to link.href)
        )
    }

    private fun String.fillSearchTemplate(text: String): String {
        val encoded = URLEncoder.encode(text, Charsets.UTF_8.name())
        return if (contains(SEARCH_PLACEHOLDER)) {
            // Templates carry optional parameters too (`{startIndex?}`); anything left over would
            // be sent literally, so unfilled placeholders are stripped.
            replace(SEARCH_PLACEHOLDER, encoded).replace(LEFTOVER_PLACEHOLDER, "")
        } else {
            val separator = if (contains('?')) "&" else "?"
            "$this${separator}q=$encoded"
        }
    }

    private companion object {
        const val EXTRA_HREF = "href"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val SEARCH_PLACEHOLDER = "{searchTerms}"
        val LEFTOVER_PLACEHOLDER = Regex("""\{[^}]*}""")

        val CONFIG_FIELDS = listOf(
            SourceConfigField(
                key = KEY_USERNAME,
                label = "Username",
                kind = SourceConfigField.Kind.Text,
                help = "Only needed by catalogs that ask for a sign-in, such as Calibre-Web."
            ),
            SourceConfigField(
                key = KEY_PASSWORD,
                label = "Password",
                kind = SourceConfigField.Kind.Secret
            )
        )
    }
}
