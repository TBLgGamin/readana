package dev.readana.source.opds

/**
 * Catalogs worth offering without making the user find a feed URL first. They are plain data, not
 * modules: adding one stores a catalog record, and from then on it behaves exactly like a feed the
 * user pasted in themselves.
 *
 * All three are freely licensed or public-domain collections, so nothing here needs an account.
 */
object PresetCatalogs {
    val all: List<PresetCatalog> = listOf(
        PresetCatalog(
            id = "standard-ebooks",
            name = "Standard Ebooks",
            feedUrl = "https://standardebooks.org/feeds/opds",
            description = "Carefully typeset public-domain literature."
        ),
        PresetCatalog(
            id = "doab",
            name = "Directory of Open Access Books",
            feedUrl = "https://directory.doabooks.org/feed/opds",
            description = "Peer-reviewed academic books, openly licensed."
        ),
        PresetCatalog(
            id = "feedbooks",
            name = "Feedbooks — public domain",
            feedUrl = "https://catalog.feedbooks.com/catalog/public_domain.atom",
            description = "Classics, in many languages."
        )
    )

    fun byId(id: String): PresetCatalog? = all.firstOrNull { it.id == id }
}

data class PresetCatalog(val id: String, val name: String, val feedUrl: String, val description: String)
