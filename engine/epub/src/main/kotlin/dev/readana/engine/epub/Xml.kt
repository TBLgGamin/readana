package dev.readana.engine.epub

/** Minimal XML/XHTML text helpers; no external XML library needed for what the writer emits. */
object Xml {
    /** Extra builder capacity so a handful of escaped entities don't force a resize. */
    private const val ESCAPE_HEADROOM = 16

    /** Escapes text for use in element content or attribute values and drops characters XML 1.0 forbids. */
    fun escape(text: String): String = buildString(text.length + ESCAPE_HEADROOM) {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (isLegal(ch)) append(ch)
            }
        }
    }

    private fun isLegal(ch: Char): Boolean =
        ch == '\t' || ch == '\n' || ch == '\r' || (ch >= ' ' && ch != '￾' && ch != '￿')
}

object Xhtml {
    private const val MIN_HEADING_LEVEL = 1
    private const val MAX_HEADING_LEVEL = 6

    fun paragraph(text: String): String = "<p>${Xml.escape(text)}</p>\n"

    /** [id] makes the heading linkable from the navigation document. */
    fun heading(level: Int, text: String, id: String? = null): String {
        val clamped = level.coerceIn(MIN_HEADING_LEVEL, MAX_HEADING_LEVEL)
        val anchor = id?.let { " id=\"${Xml.escape(it)}\"" }.orEmpty()
        return "<h$clamped$anchor>${Xml.escape(text)}</h$clamped>\n"
    }

    fun image(href: String, alt: String): String =
        """<img src="${Xml.escape(href)}" alt="${Xml.escape(alt)}"/>""" + "\n"
}
