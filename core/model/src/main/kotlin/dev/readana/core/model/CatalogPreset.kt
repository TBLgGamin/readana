package dev.readana.core.model

/**
 * A catalog offered ready-made, so adding one does not mean hunting down a feed URL. Presets are
 * data: adding one stores an ordinary [CatalogRef], and from then on it behaves exactly like a
 * feed the user pasted in themselves.
 */
data class CatalogPreset(val id: String, val name: String, val feedUrl: String, val description: String)
