package dev.readana.core.testing

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

/** In-memory [BookSource] for ViewModel and repository tests. */
class FakeBookSource(
    override val id: SourceId = SourceId("fake"),
    override val displayName: String = "Fake Source",
    var results: List<SearchResult> = emptyList(),
    var error: AppError? = null
) : BookSource {
    val queries = mutableListOf<SearchQuery>()

    override suspend fun search(query: SearchQuery): AppResult<SearchPage> {
        queries += query
        error?.let { return it.asFailure() }
        val matching = results.filter { it.title.contains(query.text, ignoreCase = true) }
        return SearchPage(results = matching, page = query.page, hasMore = false).asSuccess()
    }

    override suspend fun resolveDownload(result: SearchResult): AppResult<DownloadRequest> {
        error?.let { return it.asFailure() }
        val format = result.format ?: BookFormat.EPUB
        return DownloadRequest(
            url = "https://example.test/${result.recordId}.${format.extension}",
            format = format,
            suggestedFileName = FileNames.suggest(result.title, result.authors, format)
        ).asSuccess()
    }

    companion object {
        fun result(
            id: String,
            title: String,
            vararg authors: String,
            sourceId: SourceId = SourceId("fake")
        ): SearchResult = SearchResult(
            sourceId = sourceId,
            recordId = id,
            title = title,
            authors = authors.toList(),
            format = BookFormat.EPUB
        )
    }
}
