package dev.readana.engine.epub

import java.io.BufferedOutputStream
import java.io.OutputStream
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a minimal, valid EPUB 3.3 package. Layout:
 *
 * ```
 * mimetype                     (first entry, stored, uncompressed — required by the OCF spec)
 * META-INF/container.xml
 * OEBPS/content.opf            (package document)
 * OEBPS/nav.xhtml              (EPUB 3 navigation document)
 * OEBPS/styles/stylesheet.css
 * OEBPS/text/<chapter-id>.xhtml
 * OEBPS/<resource href>
 * ```
 *
 * Output is validated against epubcheck in this module's tests.
 */
class EpubWriter {
    fun write(book: EpubBook, output: OutputStream) {
        ZipOutputStream(BufferedOutputStream(output), Charsets.UTF_8).use { zip ->
            zip.writeMimetype()
            zip.deflated("META-INF/container.xml", CONTAINER_XML)
            zip.deflated("$OEBPS/content.opf", packageDocument(book))
            zip.deflated("$OEBPS/nav.xhtml", navDocument(book))
            zip.deflated("$OEBPS/$STYLESHEET_HREF", book.stylesheet)
            book.chapters.forEach { chapter ->
                zip.deflated("$OEBPS/${chapterHref(chapter)}", chapterDocument(book, chapter))
            }
            book.cover?.let { zip.deflated("$OEBPS/${it.href}", it.bytes) }
            book.resources.forEach { zip.deflated("$OEBPS/${it.href}", it.bytes) }
        }
    }

    private fun ZipOutputStream.writeMimetype() {
        val bytes = MIMETYPE.toByteArray(Charsets.US_ASCII)
        val entry = ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = CRC32().apply { update(bytes) }.value
            time = ENTRY_TIME
        }
        putNextEntry(entry)
        write(bytes)
        closeEntry()
    }

    private fun ZipOutputStream.deflated(name: String, content: String) =
        deflated(name, content.toByteArray(Charsets.UTF_8))

    private fun ZipOutputStream.deflated(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name).apply { time = ENTRY_TIME })
        write(bytes)
        closeEntry()
    }

    internal fun packageDocument(book: EpubBook): String {
        val meta = book.metadata
        return buildString {
            append(XML_DECLARATION)
            append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"pub-id\"")
            append(" xml:lang=\"${Xml.escape(meta.language)}\">\n")
            append("<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n")
            append("<dc:identifier id=\"pub-id\">${Xml.escape(meta.identifier)}</dc:identifier>\n")
            append("<dc:title>${Xml.escape(meta.title)}</dc:title>\n")
            append("<dc:language>${Xml.escape(meta.language)}</dc:language>\n")
            meta.authors.forEachIndexed { index, author ->
                append("<dc:creator id=\"creator-${index + 1}\">${Xml.escape(author)}</dc:creator>\n")
            }
            meta.publisher?.let { append("<dc:publisher>${Xml.escape(it)}</dc:publisher>\n") }
            meta.description?.let { append("<dc:description>${Xml.escape(it)}</dc:description>\n") }
            append("<meta property=\"dcterms:modified\">${MODIFIED_FORMAT.format(meta.modified)}</meta>\n")
            book.cover?.let { append("<meta name=\"cover\" content=\"$COVER_ID\"/>\n") }
            append("</metadata>\n")

            append("<manifest>\n")
            append(
                "<item id=\"$NAV_ID\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n"
            )
            append("<item id=\"$STYLESHEET_ID\" href=\"$STYLESHEET_HREF\" media-type=\"text/css\"/>\n")
            book.chapters.forEach { chapter ->
                append("<item id=\"${chapter.id}\" href=\"${chapterHref(chapter)}\"")
                append(" media-type=\"application/xhtml+xml\"/>\n")
            }
            book.cover?.let {
                append("<item id=\"$COVER_ID\" href=\"${Xml.escape(it.href)}\"")
                append(" media-type=\"${Xml.escape(it.mediaType)}\" properties=\"cover-image\"/>\n")
            }
            book.resources.forEachIndexed { index, resource ->
                append("<item id=\"res-${index + 1}\" href=\"${Xml.escape(resource.href)}\"")
                append(" media-type=\"${Xml.escape(resource.mediaType)}\"/>\n")
            }
            append("</manifest>\n")

            append("<spine>\n")
            book.chapters.forEach { append("<itemref idref=\"${it.id}\"/>\n") }
            append("</spine>\n")
            append("</package>\n")
        }
    }

    internal fun navDocument(book: EpubBook): String = buildString {
        append(xhtmlHead(book.metadata.language, "Contents", stylesheetHref = STYLESHEET_HREF))
        append("<nav epub:type=\"toc\" id=\"toc\">\n<h1>Contents</h1>\n<ol>\n")
        book.chapters.forEach { chapter ->
            append("<li><a href=\"${chapterHref(chapter)}\">${Xml.escape(chapter.title)}</a></li>\n")
        }
        append("</ol>\n</nav>\n")
        append(XHTML_FOOT)
    }

    internal fun chapterDocument(book: EpubBook, chapter: EpubChapter): String = buildString {
        append(xhtmlHead(book.metadata.language, chapter.title, stylesheetHref = "../$STYLESHEET_HREF"))
        append(chapter.bodyXhtml)
        if (!chapter.bodyXhtml.endsWith("\n")) append('\n')
        append(XHTML_FOOT)
    }

    private fun xhtmlHead(language: String, title: String, stylesheetHref: String): String {
        val lang = Xml.escape(language)
        return XML_DECLARATION +
            "<!DOCTYPE html>\n" +
            "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\"" +
            " xml:lang=\"$lang\" lang=\"$lang\">\n" +
            "<head>\n<meta charset=\"utf-8\"/>\n<title>${Xml.escape(title)}</title>\n" +
            "<link rel=\"stylesheet\" type=\"text/css\" href=\"$stylesheetHref\"/>\n</head>\n<body>\n"
    }

    internal fun chapterHref(chapter: EpubChapter): String = "text/${chapter.id}.xhtml"

    companion object {
        const val MIMETYPE = "application/epub+zip"
        internal const val OEBPS = "OEBPS"
        internal const val STYLESHEET_HREF = "styles/stylesheet.css"
        internal const val NAV_ID = "nav"
        internal const val STYLESHEET_ID = "stylesheet"
        internal const val COVER_ID = "cover-image"
        val RESERVED_IDS: Set<String> = setOf(NAV_ID, STYLESHEET_ID, COVER_ID)

        /** Fixed timestamp so identical input produces byte-identical output. */
        private const val ENTRY_TIME = 1_767_225_600_000L // 2026-01-01T00:00:00Z

        private const val XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        private const val XHTML_FOOT = "</body>\n</html>\n"

        private val MODIFIED_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

        private val CONTAINER_XML = XML_DECLARATION +
            "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n" +
            "<rootfiles>\n" +
            "<rootfile full-path=\"$OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n" +
            "</rootfiles>\n" +
            "</container>\n"

        val DEFAULT_STYLESHEET: String =
            """
            |body { margin: 0 1em; line-height: 1.4; }
            |h1, h2, h3 { line-height: 1.2; margin: 1.5em 0 0.5em; page-break-after: avoid; }
            |h1 { font-size: 1.6em; text-align: center; }
            |h2 { font-size: 1.3em; }
            |h3 { font-size: 1.1em; }
            |p { margin: 0; text-indent: 1.2em; text-align: justify; }
            |h1 + p, h2 + p, h3 + p { text-indent: 0; }
            |img { max-width: 100%; height: auto; }
            """.trimMargin()
    }
}
