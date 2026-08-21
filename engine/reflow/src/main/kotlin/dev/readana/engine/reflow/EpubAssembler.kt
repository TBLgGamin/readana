package dev.readana.engine.reflow

import dev.readana.engine.epub.EpubBook
import dev.readana.engine.epub.EpubChapter
import dev.readana.engine.epub.EpubMetadata
import dev.readana.engine.epub.Xhtml

/** Groups reflowed [Block]s into chapters and builds an [EpubBook] ready for [dev.readana.engine.epub.EpubWriter]. */
object EpubAssembler {
    private const val DEFAULT_MAX_PARAGRAPHS_PER_CHAPTER = 300

    /**
     * Chapters split at the highest heading level present in the document. Documents without
     * headings are chunked every [maxParagraphsPerChapter] paragraphs so readers can still
     * paginate and report progress sensibly.
     */
    fun assemble(
        blocks: List<Block>,
        metadata: EpubMetadata,
        maxParagraphsPerChapter: Int = DEFAULT_MAX_PARAGRAPHS_PER_CHAPTER
    ): EpubBook {
        require(maxParagraphsPerChapter > 0) { "maxParagraphsPerChapter must be positive" }
        val splitLevel = blocks.filterIsInstance<Block.Heading>().minOfOrNull { it.level }
        val builder = ChapterListBuilder(metadata.title)

        for (block in blocks) {
            when (block) {
                is Block.Heading -> {
                    if (block.level == splitLevel) builder.startChapter(block.text)
                    builder.append(Xhtml.heading(block.level, block.text))
                }

                is Block.Paragraph -> {
                    if (splitLevel == null &&
                        builder.paragraphsInCurrent >= maxParagraphsPerChapter
                    ) {
                        builder.startChapter("${metadata.title} (${builder.size + 2})")
                    }
                    builder.append(Xhtml.paragraph(block.text))
                    builder.paragraphsInCurrent++
                }
            }
        }

        return EpubBook(metadata = metadata, chapters = builder.build())
    }

    private class ChapterListBuilder(private val defaultTitle: String) {
        private val chapters = mutableListOf<EpubChapter>()
        private var title = defaultTitle
        private var body = StringBuilder()
        var paragraphsInCurrent = 0

        val size: Int
            get() = chapters.size

        fun append(xhtml: String) {
            body.append(xhtml)
        }

        fun startChapter(newTitle: String) {
            flush()
            title = newTitle
        }

        fun build(): List<EpubChapter> {
            flush()
            if (chapters.isEmpty()) {
                chapters +=
                    EpubChapter(
                        id = chapterId(1),
                        title = defaultTitle,
                        bodyXhtml = Xhtml.paragraph("")
                    )
            }
            return chapters.toList()
        }

        private fun flush() {
            if (body.isEmpty()) return
            chapters +=
                EpubChapter(
                    id = chapterId(chapters.size + 1),
                    title = title,
                    bodyXhtml = body.toString()
                )
            body = StringBuilder()
            paragraphsInCurrent = 0
        }

        private fun chapterId(number: Int) = "ch-" + number.toString().padStart(CHAPTER_ID_WIDTH, '0')
    }

    private const val CHAPTER_ID_WIDTH = 4
}
