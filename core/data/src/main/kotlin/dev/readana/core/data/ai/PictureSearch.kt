package dev.readana.core.data.ai

import dev.readana.core.common.coroutines.suspendRunCatching
import dev.readana.core.data.di.AiHttp
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/** A picture found for a subject the answer mentioned, with where it came from. */
data class FoundPicture(val subject: String, val imageUrl: String, val pageUrl: String, val title: String)

/**
 * Finds a picture of something an answer mentions.
 *
 * This deliberately does not go through the model's own web search. Those tools return text and
 * citations rather than pictures, they cost money per search, two of the supported providers do
 * not have one at all — and, decisively, anything they read comes back into the conversation,
 * which is exactly how a search for "Moby-Dick whale" tells the reader how the book ends. Here the
 * model only ever writes down a subject; Readana does the looking, and the result is shown to the
 * reader without ever being sent back. The assistant learns nothing it did not already know.
 *
 * Wikipedia is the source: no key, no quota to manage, and freely licensed images with a page to
 * credit. It works identically on every provider.
 */
interface PictureSearch {
    suspend fun find(subject: String): FoundPicture?
}

@Singleton
class WikipediaPictureSearch @Inject constructor(@AiHttp private val httpClient: HttpClient) : PictureSearch {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun find(subject: String): FoundPicture? {
        val query = subject.trim().take(MAX_SUBJECT_CHARS)
        if (query.isEmpty()) return null
        val body = suspendRunCatching { fetch(query) }.getOrNull() ?: return null
        // The API returns pages in whatever order it pleases; "index" is the search ranking.
        val page = decode(body)?.query?.pages
            ?.filter { it.thumbnail?.source != null }
            ?.minByOrNull { it.index }
            ?: return null
        val image = page.thumbnail?.source ?: return null
        return FoundPicture(
            subject = subject,
            imageUrl = image,
            pageUrl = ARTICLE_BASE + page.title.replace(' ', '_'),
            title = page.title
        )
    }

    private suspend fun fetch(query: String): String? {
        val response = httpClient.get(API) {
            // Wikimedia refuses requests without a user agent that says who is calling.
            header(HttpHeaders.UserAgent, USER_AGENT)
            parameter("action", "query")
            parameter("format", "json")
            parameter("formatversion", "2")
            parameter("generator", "search")
            parameter("gsrsearch", query)
            parameter("gsrlimit", SEARCH_LIMIT)
            parameter("prop", "pageimages")
            parameter("piprop", "thumbnail")
            parameter("pithumbsize", THUMB_SIZE)
        }
        return if (response.status.isSuccess()) response.bodyAsText() else null
    }

    private fun decode(body: String): SearchResponse? = try {
        json.decodeFromString<SearchResponse>(body)
    } catch (failure: IllegalArgumentException) {
        Timber.w(failure, "Could not read the picture search response")
        null
    }

    private companion object {
        const val API = "https://en.wikipedia.org/w/api.php"
        const val ARTICLE_BASE = "https://en.wikipedia.org/wiki/"
        const val USER_AGENT = "Readana/1.0 (https://github.com/TBLgGamin/readana)"
        const val SEARCH_LIMIT = 3
        const val THUMB_SIZE = 800
        const val MAX_SUBJECT_CHARS = 120
    }
}

@Serializable
private data class SearchResponse(val query: QueryBody? = null)

@Serializable
private data class QueryBody(val pages: List<PageBody> = emptyList())

@Serializable
private data class PageBody(
    val title: String = "",
    /** Rank among the search results; lower is a better match. */
    val index: Int = Int.MAX_VALUE,
    @SerialName("thumbnail") val thumbnail: Thumb? = null
)

@Serializable
private data class Thumb(val source: String? = null)
