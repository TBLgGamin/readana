package dev.readana.core.data.ai

import dev.readana.core.common.coroutines.suspendRunCatching
import dev.readana.core.data.di.AiHttp
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
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

/**
 * Looks the subject up as an article title first and only falls back to full-text search.
 *
 * The order matters: searching for "Hestia" happens to rank the goddess first, but searching for a
 * description of her — which is what a model writes if you let it — lands on whichever fire deity
 * the index liked that day. An exact title is either right or absent, and an answer with no
 * picture is much better than an answer with the wrong one, so a result that looks nothing like
 * what was asked for is discarded.
 */
@Singleton
class WikipediaPictureSearch @Inject constructor(@AiHttp private val httpClient: HttpClient) : PictureSearch {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun find(subject: String): FoundPicture? {
        val query = subject.trim().take(MAX_SUBJECT_CHARS)
        if (query.isEmpty()) return null
        val page = byTitle(query) ?: bySearch(query) ?: return null
        val image = page.thumbnail?.source ?: return null
        if (!page.title.isAbout(query)) {
            Timber.d("Ignoring %s: nothing to do with %s", page.title, query)
            return null
        }
        return FoundPicture(
            subject = subject,
            imageUrl = image,
            pageUrl = ARTICLE_BASE + page.title.replace(' ', '_'),
            title = page.title
        )
    }

    /** The article of that exact name, following redirects. Wrong titles simply have no page. */
    private suspend fun byTitle(query: String): PageBody? = request {
        parameter("titles", query)
        parameter("redirects", "1")
    }?.query?.pages?.firstOrNull { it.missing != true && it.thumbnail?.source != null }

    /** Whatever the index likes, best match first — minus the disambiguation pages. */
    private suspend fun bySearch(query: String): PageBody? = request {
        parameter("generator", "search")
        parameter("gsrsearch", query)
        parameter("gsrlimit", SEARCH_LIMIT)
    }?.query?.pages
        ?.filter { it.thumbnail?.source != null && !it.title.contains(DISAMBIGUATION) }
        // The API answers in whatever order it pleases; "index" is the search ranking.
        ?.minByOrNull { it.index }

    private suspend fun request(configure: HttpRequestBuilder.() -> Unit): SearchResponse? {
        val body = suspendRunCatching {
            val response = httpClient.get(API) {
                // Wikimedia refuses requests without a user agent that says who is calling.
                header(HttpHeaders.UserAgent, USER_AGENT)
                parameter("action", "query")
                parameter("format", "json")
                parameter("formatversion", "2")
                parameter("prop", "pageimages")
                parameter("piprop", "thumbnail")
                parameter("pithumbsize", THUMB_SIZE)
                configure()
            }
            if (response.status.isSuccess()) response.bodyAsText() else null
        }.getOrNull() ?: return null
        return decode(body)
    }

    /**
     * Guards against the index handing back something unrelated: the article's name and the
     * subject asked for must have a real word in common.
     */
    private fun String.isAbout(subject: String): Boolean {
        val wanted = subject.words()
        if (wanted.isEmpty()) return true
        val found = words()
        return wanted.any { it in found } || found.any { it in wanted }
    }

    private fun String.words(): Set<String> = lowercase()
        .split(NON_WORD)
        .filter { it.length > SHORT_WORD }
        .toSet()

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
        const val DISAMBIGUATION = "(disambiguation)"
        const val SEARCH_LIMIT = 3
        const val THUMB_SIZE = 800
        const val MAX_SUBJECT_CHARS = 120

        /** "of", "the", "a" say nothing about whether two titles are the same thing. */
        const val SHORT_WORD = 3
        val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    }
}

@Serializable
private data class SearchResponse(val query: QueryBody? = null)

@Serializable
private data class QueryBody(val pages: List<PageBody> = emptyList())

@Serializable
private data class PageBody(
    val title: String = "",
    /** Rank among the search results; lower is a better match. Absent from a title lookup. */
    val index: Int = 0,
    val missing: Boolean? = null,
    @SerialName("thumbnail") val thumbnail: Thumb? = null
)

@Serializable
private data class Thumb(val source: String? = null)
