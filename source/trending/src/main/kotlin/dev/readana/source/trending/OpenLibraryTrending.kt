package dev.readana.source.trending

import dev.readana.core.common.coroutines.suspendRunCatching
import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.common.result.asSuccess
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Open Library's trending feed.
 *
 * Deliberately not Anna's Archive: it throttles scraping hard (see [dev.readana.source.annas]), so
 * spending that budget on a decorative shelf every time Discover opens would break the thing the
 * user actually came for. Open Library is free, needs no key, and ships cover art.
 */
class OpenLibraryTrending(private val client: HttpClient, private val baseUrl: String = BASE_URL) : TrendingBooks {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun trending(limit: Int): AppResult<List<TrendingBook>> = suspendRunCatching {
        val response = client.get("$baseUrl/trending/weekly.json") {
            parameter("limit", limit)
        }
        if (!response.status.isSuccess()) {
            return@suspendRunCatching AppError.Http(response.status.value).asFailure()
        }
        val payload = json.decodeFromString<TrendingResponse>(response.bodyAsText())
        payload.works.mapNotNull { it.toBook() }.asSuccess()
    }.getOrElse { failure ->
        AppError.Network("Could not load trending books: ${failure.message}", failure).asFailure()
    }

    private fun TrendingWork.toBook(): TrendingBook? {
        val cleanTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return TrendingBook(
            title = cleanTitle,
            authors = authors.map { it.trim() }.filter { it.isNotEmpty() },
            coverUrl = coverId?.let { "$COVERS_URL/b/id/$it-M.jpg" }
        )
    }

    @Serializable
    private data class TrendingResponse(val works: List<TrendingWork> = emptyList())

    @Serializable
    private data class TrendingWork(
        val title: String? = null,
        @SerialName("author_name") val authors: List<String> = emptyList(),
        @SerialName("cover_i") val coverId: Long? = null
    )

    companion object {
        const val BASE_URL = "https://openlibrary.org"
        const val COVERS_URL = "https://covers.openlibrary.org"
    }
}
