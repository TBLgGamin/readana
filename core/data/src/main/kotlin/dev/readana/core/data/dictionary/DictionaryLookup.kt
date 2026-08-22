package dev.readana.core.data.dictionary

import dev.readana.core.common.coroutines.suspendRunCatching
import dev.readana.core.data.di.AiHttp
import dev.readana.core.model.DictionaryEntry
import dev.readana.core.model.DictionarySense
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/** Looks a word up while the reader is in the middle of a sentence. */
interface DictionaryLookup {
    /** What [word] means, or null when the dictionary has no entry for it. */
    suspend fun define(word: String): DictionaryEntry?
}

/**
 * Wiktionary's definition endpoint: no key, no quota, and the same answer on every device.
 *
 * Deliberately not the reading assistant. A definition is a fact about a language, not about this
 * book — it costs nothing, needs no provider configured, and works on the first page of a book the
 * assistant would refuse to discuss because nothing has been read yet. The assistant is offered
 * underneath, for the cases a dictionary genuinely cannot answer.
 */
@Singleton
class WiktionaryDictionary @Inject constructor(@AiHttp private val httpClient: HttpClient) : DictionaryLookup {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun define(word: String): DictionaryEntry? {
        val query = word.trim().trim(*TRIMMED_PUNCTUATION).lowercase()
        // A phrase is not a dictionary headword; asking anyway just spends a request on a 404.
        if (query.isEmpty() || query.length > MAX_WORD_CHARS || query.any { it.isWhitespace() }) return null

        val body = suspendRunCatching {
            val response = httpClient.get(API + query.encodeURLPathPart()) {
                // Wikimedia refuses requests without a user agent that says who is calling.
                header(HttpHeaders.UserAgent, USER_AGENT)
            }
            if (response.status.isSuccess()) response.bodyAsText() else null
        }.getOrNull() ?: return null

        val senses = decode(body)?.get(LANGUAGE).orEmpty()
            .flatMap { part -> part.senses() }
            .take(MAX_SENSES)
        return if (senses.isEmpty()) null else DictionaryEntry(word = query, senses = senses)
    }

    private fun PartOfSpeechBody.senses(): List<DictionarySense> {
        val label = partOfSpeech?.trim().orEmpty()
        return definitions.mapNotNull { entry ->
            val meaning = entry.definition?.stripMarkup()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            DictionarySense(
                partOfSpeech = label,
                definition = meaning,
                example = entry.examples.firstOrNull()?.stripMarkup()?.takeIf { it.isNotBlank() }
            )
        }
    }

    private fun decode(body: String): Map<String, List<PartOfSpeechBody>>? = try {
        json.decodeFromString<Map<String, List<PartOfSpeechBody>>>(body)
    } catch (failure: IllegalArgumentException) {
        Timber.w(failure, "Could not read the dictionary response")
        null
    }

    /** Definitions come back as HTML fragments; the sheet shows plain prose. */
    private fun String.stripMarkup(): String = replace(TAG, "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace(WHITESPACE, " ")
        .trim()

    private companion object {
        const val API = "https://en.wiktionary.org/api/rest_v1/page/definition/"
        const val USER_AGENT = "Readana/1.0 (https://github.com/TBLgGamin/readana)"

        /** English only: the reader is reading in it, and other sections are noise. */
        const val LANGUAGE = "en"

        /** Enough to settle a word without turning the sheet into an article. */
        const val MAX_SENSES = 6
        const val MAX_WORD_CHARS = 40

        val TAG = Regex("<[^>]*>")
        val WHITESPACE = Regex("\\s+")
        val TRIMMED_PUNCTUATION = charArrayOf(
            '.', ',', ';', ':', '!', '?', '"', '\'', '(', ')', '[', ']', '“', '”', '‘', '’', '—', '–'
        )
    }
}

@Serializable
private data class PartOfSpeechBody(
    val partOfSpeech: String? = null,
    val definitions: List<DefinitionBody> = emptyList()
)

@Serializable
private data class DefinitionBody(val definition: String? = null, val examples: List<String> = emptyList())
