package dev.readana.core.data.repository

import dev.readana.source.annas.AnnasConfig

/**
 * Source defaults that the UI needs to show. Re-exported here because the source modules are an
 * implementation detail of `core/data`, and features should not depend on them directly.
 */
object SourceDefaults {
    /** Mirrors used when the user has not entered any of their own. */
    val ANNAS_MIRRORS: List<String> = AnnasConfig.DEFAULT_MIRRORS
}
