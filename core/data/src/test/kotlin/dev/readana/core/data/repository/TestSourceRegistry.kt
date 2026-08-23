package dev.readana.core.data.repository

import dev.readana.source.api.BookSource

/**
 * A registry over a fixed set of sources. No test here configures a catalog, so asking for one is
 * a mistake worth failing on rather than quietly returning something that cannot work.
 */
internal fun testRegistry(sources: Set<BookSource>, settings: SettingsRepository): BookSourceRegistry =
    BookSourceRegistry(
        builtIn = sources,
        catalogFactory = { catalog -> error("unexpected catalog ${catalog.id}") },
        settings = settings
    )
