package dev.readana.engine.epub

import java.time.Instant
import java.util.UUID

/** Dublin Core metadata written into the EPUB package document. */
data class EpubMetadata(
    val title: String,
    val identifier: String = newIdentifier(),
    /** BCP 47 language tag, e.g. `en` or `nl-NL`. */
    val language: String = "en",
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val description: String? = null,
    val modified: Instant = Instant.now()
) {
    init {
        require(title.isNotBlank()) { "EPUB title must not be blank" }
        require(identifier.isNotBlank()) { "EPUB identifier must not be blank" }
        require(language.isNotBlank()) { "EPUB language must not be blank" }
    }

    companion object {
        fun newIdentifier(): String = "urn:uuid:${UUID.randomUUID()}"
    }
}

/** One spine item. [bodyXhtml] is the inner content of `<body>` and must be well-formed XHTML. */
data class EpubChapter(val id: String, val title: String, val bodyXhtml: String) {
    init {
        require(ID_PATTERN.matches(id)) { "Chapter id '$id' must match ${ID_PATTERN.pattern}" }
        require(id !in EpubWriter.RESERVED_IDS) { "Chapter id '$id' is reserved" }
    }

    private companion object {
        val ID_PATTERN = Regex("[A-Za-z][A-Za-z0-9_-]*")
    }
}

/** A binary resource (image, font) stored under `OEBPS/` at [href]. */
class EpubResource(val href: String, val mediaType: String, val bytes: ByteArray) {
    init {
        require(href.isNotBlank() && !href.startsWith("/") && !href.contains("..")) {
            "Resource href must be a relative path inside the package: '$href'"
        }
        require(mediaType.contains('/')) { "Invalid media type '$mediaType'" }
    }
}

/**
 * One entry in the navigation document. [href] is relative to `OEBPS/` and may carry a fragment,
 * e.g. `text/ch-0001.xhtml#h-0003`.
 */
data class NavEntry(val title: String, val href: String, val children: List<NavEntry> = emptyList()) {
    init {
        require(href.isNotBlank()) { "Nav entry href must not be blank" }
    }
}

data class EpubBook(
    val metadata: EpubMetadata,
    val chapters: List<EpubChapter>,
    val resources: List<EpubResource> = emptyList(),
    val cover: EpubResource? = null,
    val stylesheet: String = EpubWriter.DEFAULT_STYLESHEET,
    /** Explicit table of contents; null falls back to one flat entry per chapter. */
    val navigation: List<NavEntry>? = null
) {
    init {
        require(chapters.isNotEmpty()) { "An EPUB needs at least one chapter" }
        val ids = chapters.map { it.id }
        require(ids.toSet().size == ids.size) { "Chapter ids must be unique: $ids" }
        require(navigation == null || navigation.isNotEmpty()) {
            "Navigation must be null or non-empty; an empty table of contents is invalid EPUB"
        }
        val hrefs = resources.map { it.href }
        require(hrefs.toSet().size == hrefs.size) { "Resource hrefs must be unique" }
    }
}
