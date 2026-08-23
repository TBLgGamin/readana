package dev.readana.source.opds

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads an OPDS 2.0 feed. Same model as the Atom flavour, but the wire format is the Readium Web
 * Publication Manifest: `publications`, `navigation` and `groups` instead of one `entry` list.
 */
internal object OpdsJsonParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(body: String, baseUrl: String): OpdsFeed {
        val feed = json.decodeFromString<FeedDto>(body)
        val links = feed.links
        // Groups hold the same two kinds of thing, one shelf deeper; flattening them keeps the
        // page readable without inventing a second level of navigation.
        val grouped = feed.groups.orEmpty()
        val publications = feed.publications.orEmpty() + grouped.flatMap { it.publications.orEmpty() }
        val navigation = feed.navigation.orEmpty() + grouped.flatMap { it.navigation.orEmpty() }

        return OpdsFeed(
            title = feed.metadata?.title.orEmpty(),
            entries = publications.mapNotNull { it.toEntry(baseUrl) } +
                navigation.mapNotNull { it.toNavigationEntry(baseUrl) },
            nextHref = links.firstOrNull { OpdsLinks.isNext(it.rel) }
                ?.let { AtomXml.resolve(baseUrl, it.href) },
            searchTemplate = links.firstOrNull { OpdsLinks.isSearch(it.rel) }
                ?.let { AtomXml.resolve(baseUrl, it.href) }
        )
    }

    private fun PublicationDto.toEntry(baseUrl: String): OpdsEntry? {
        val title = metadata?.title?.takeIf { it.isNotBlank() } ?: return null
        val acquisition = links.orEmpty()
            .firstOrNull { OpdsLinks.isAcquisition(it.rel, it.type) }
            ?.let { link ->
                val href = AtomXml.resolve(baseUrl, link.href) ?: return@let null
                OpdsAcquisition(href, link.type.orEmpty())
            }
        return OpdsEntry(
            id = metadata.identifier ?: title,
            title = title,
            authors = metadata.authorNames(),
            summary = metadata.description,
            publisher = metadata.publisher?.nameOrNull(),
            language = metadata.language?.firstStringOrNull(),
            year = AtomXml.yearOf(metadata.published ?: metadata.modified),
            coverUrl = images.orEmpty().firstOrNull()?.let { AtomXml.resolve(baseUrl, it.href) }
                ?: links.orEmpty().firstOrNull { OpdsLinks.isCover(it.rel) }
                    ?.let { AtomXml.resolve(baseUrl, it.href) },
            acquisition = acquisition
        )
    }

    private fun LinkDto.toNavigationEntry(baseUrl: String): OpdsEntry? {
        val target = AtomXml.resolve(baseUrl, href) ?: return null
        val name = title?.takeIf { it.isNotBlank() } ?: return null
        return OpdsEntry(id = target, title = name, navigationHref = target)
    }

    @Serializable
    private data class FeedDto(
        val metadata: FeedMetadataDto? = null,
        val links: List<LinkDto> = emptyList(),
        val publications: List<PublicationDto>? = null,
        val navigation: List<LinkDto>? = null,
        val groups: List<GroupDto>? = null
    )

    @Serializable
    private data class GroupDto(val publications: List<PublicationDto>? = null, val navigation: List<LinkDto>? = null)

    @Serializable
    private data class FeedMetadataDto(val title: String? = null)

    @Serializable
    private data class PublicationDto(
        val metadata: PublicationMetadataDto? = null,
        val links: List<LinkDto>? = null,
        val images: List<LinkDto>? = null
    )

    @Serializable
    private data class PublicationMetadataDto(
        val identifier: String? = null,
        val title: String? = null,
        val author: JsonElement? = null,
        val publisher: JsonElement? = null,
        val language: JsonElement? = null,
        val description: String? = null,
        val published: String? = null,
        val modified: String? = null
    ) {
        /** `author` may be a string, an object with a `name`, or a list of either. */
        fun authorNames(): List<String> = when (val value = author) {
            null -> emptyList()
            is JsonArray -> value.mapNotNull { it.nameOrNull() }
            else -> listOfNotNull(value.nameOrNull())
        }
    }

    @Serializable
    private data class LinkDto(
        val href: String,
        @SerialName("rel") private val relRaw: JsonElement? = null,
        val type: String? = null,
        val title: String? = null
    ) {
        /**
         * `rel` is a string in most feeds and an array in some. Joining an array with spaces
         * matches Atom's own convention, which is what [OpdsLinks] already splits on.
         */
        val rel: String?
            get() = when (val value = relRaw) {
                null -> null
                is JsonArray -> value.mapNotNull { it.jsonPrimitive.contentOrNull() }.joinToString(" ")
                else -> value.jsonPrimitive.contentOrNull()
            }
    }

    private fun JsonElement.nameOrNull(): String? = when (this) {
        is JsonPrimitive -> contentOrNull()

        else -> (this as? kotlinx.serialization.json.JsonObject)
            ?.get("name")
            ?.jsonPrimitive
            ?.contentOrNull()
    }

    private fun JsonElement.firstStringOrNull(): String? = when (this) {
        is JsonArray -> firstOrNull()?.jsonPrimitive?.contentOrNull()
        else -> jsonPrimitive.contentOrNull()
    }

    private fun JsonPrimitive.contentOrNull(): String? = content.takeIf { it.isNotBlank() && isString }
}
