package dev.readana.source.gutenberg

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire format of https://gutendex.com/books/ (only the fields Readana uses). */
@Serializable
internal data class GutendexResponse(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<GutendexBook> = emptyList()
)

@Serializable
internal data class GutendexBook(
    val id: Int,
    val title: String,
    val authors: List<GutendexPerson> = emptyList(),
    val languages: List<String> = emptyList(),
    val summaries: List<String> = emptyList(),
    /** Media type → URL, e.g. `"application/epub+zip": "https://www.gutenberg.org/ebooks/1342.epub3.images"`. */
    val formats: Map<String, String> = emptyMap(),
    @SerialName("download_count") val downloadCount: Int = 0
)

@Serializable
internal data class GutendexPerson(
    val name: String,
    @SerialName("birth_year") val birthYear: Int? = null,
    @SerialName("death_year") val deathYear: Int? = null
) {
    /** Gutendex lists names as "Austen, Jane"; readers expect "Jane Austen". */
    val displayName: String
        get() {
            val parts = name.split(",", limit = 2).map { it.trim() }
            return if (parts.size == 2 && parts[1].isNotEmpty()) "${parts[1]} ${parts[0]}" else name
        }
}
