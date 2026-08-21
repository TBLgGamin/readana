package dev.readana.core.model

/** File formats the library stores. PDFs are converted to EPUB on import but the original is kept. */
enum class BookFormat(val extension: String, val mimeType: String) {
    EPUB("epub", "application/epub+zip"),
    PDF("pdf", "application/pdf")
    ;

    companion object {
        fun fromExtension(extension: String): BookFormat? {
            val normalized = extension.trimStart('.').lowercase()
            return entries.firstOrNull { it.extension == normalized }
        }

        fun fromMimeType(mimeType: String): BookFormat? = entries.firstOrNull {
            it.mimeType.equals(mimeType.substringBefore(';').trim(), ignoreCase = true)
        }
    }
}
