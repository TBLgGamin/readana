package dev.readana.engine.reflow

import dev.readana.engine.epub.EpubBook
import dev.readana.engine.epub.EpubChapter
import dev.readana.engine.epub.EpubMetadata
import dev.readana.engine.epub.EpubResource
import dev.readana.engine.epub.NavEntry
import dev.readana.engine.epub.Xhtml
import dev.readana.engine.reflow.model.OutlineNode
import java.security.MessageDigest

/** The assembled book plus what the assembler had to decide along the way. */
data class Assembly(
    val book: EpubBook,
    /** True when the PDF's own outline drove the chapter split. */
    val outlineUsed: Boolean,
    /** Outline entries that pointed at nothing we could link to. */
    val droppedNavEntries: Int
)

/** Groups reflowed [Block]s into chapters and builds an [EpubBook] ready for [dev.readana.engine.epub.EpubWriter]. */
object EpubAssembler {
    private const val DEFAULT_MAX_PARAGRAPHS_PER_CHAPTER = 300
    private const val CHAPTER_ID_WIDTH = 4
    private const val MIN_OUTLINE_ENTRIES = 2
    private const val IMAGES_DIR = "images"

    /**
     * Chapters follow the document's own outline when it has one that maps onto the text; otherwise
     * they split at the highest heading level present. Documents with neither are chunked every
     * [maxParagraphsPerChapter] paragraphs so readers can still paginate and report progress.
     */
    fun assemble(
        blocks: List<Block>,
        metadata: EpubMetadata,
        outline: List<OutlineNode> = emptyList(),
        maxParagraphsPerChapter: Int = DEFAULT_MAX_PARAGRAPHS_PER_CHAPTER
    ): Assembly {
        require(maxParagraphsPerChapter > 0) { "maxParagraphsPerChapter must be positive" }
        val images = ImageCollector()
        val splits = outlineSplits(blocks, outline)
        val builder = ChapterListBuilder(metadata.title)

        if (splits != null) {
            buildFromOutline(blocks, splits, builder, images)
        } else {
            buildFromHeadings(blocks, builder, images, maxParagraphsPerChapter, metadata.title)
        }

        val chapters = builder.build()
        val navigation = if (splits != null) {
            builder.outlineNavigation(splits, chapters)
        } else {
            null
        }
        return Assembly(
            book = EpubBook(
                metadata = metadata,
                chapters = chapters,
                resources = images.resources(),
                navigation = navigation?.takeIf { it.isNotEmpty() }
            ),
            outlineUsed = splits != null,
            droppedNavEntries = splits?.dropped ?: 0
        )
    }

    private fun buildFromOutline(
        blocks: List<Block>,
        splits: OutlineSplits,
        builder: ChapterListBuilder,
        images: ImageCollector
    ) {
        blocks.forEachIndexed { index, block ->
            splits.chapterTitleAt(index)?.let(builder::startChapter)
            val anchor = splits.anchorAt(index)
            builder.append(render(block, images, anchor))
            if (block !is Block.Heading) builder.paragraphsInCurrent++
        }
    }

    private fun buildFromHeadings(
        blocks: List<Block>,
        builder: ChapterListBuilder,
        images: ImageCollector,
        maxParagraphsPerChapter: Int,
        title: String
    ) {
        val splitLevel = blocks.filterIsInstance<Block.Heading>().minOfOrNull { it.level }
        for (block in blocks) {
            if (block is Block.Heading && block.level == splitLevel) builder.startChapter(block.text)
            if (block !is Block.Heading) {
                if (splitLevel == null && builder.paragraphsInCurrent >= maxParagraphsPerChapter) {
                    builder.startChapter("$title (${builder.size + 2})")
                }
                builder.paragraphsInCurrent++
            }
            builder.append(render(block, images, anchor = null))
        }
    }

    private fun render(block: Block, images: ImageCollector, anchor: String?): String = when (block) {
        is Block.Heading -> Xhtml.heading(block.level, block.text, anchor)
        is Block.Paragraph -> Xhtml.paragraph(block.text)
        is Block.Image -> Xhtml.image("../${images.add(block)}", block.altText)
    }

    /**
     * Maps outline entries onto block positions. Returns null when the outline is missing, too
     * small, or does not line up with the text — the heading heuristics do better than a broken
     * table of contents.
     */
    private fun outlineSplits(blocks: List<Block>, outline: List<OutlineNode>): OutlineSplits? {
        if (outline.size < MIN_OUTLINE_ENTRIES || blocks.isEmpty()) return null
        val pages = outline.map { it.pageIndex }
        if (pages != pages.sorted()) return null

        val chapters = mutableListOf<ChapterSplit>()
        var dropped = 0
        var searchFrom = 0
        for (node in outline) {
            val at = blockIndexFor(blocks, node.pageIndex, searchFrom)
            if (at == null) {
                dropped += 1 + node.children.size
                continue
            }
            chapters += ChapterSplit(at, node.title)
            searchFrom = at + 1
        }
        if (chapters.map { it.blockIndex }.distinct().size < MIN_OUTLINE_ENTRIES) return null

        val anchors = mutableMapOf<Int, Anchor>()
        outline.forEachIndexed { chapterIndex, node ->
            val chapter = chapters.getOrNull(chapterIndex) ?: return@forEachIndexed
            val limit = chapters.getOrNull(chapterIndex + 1)?.blockIndex ?: blocks.size
            var childFrom = chapter.blockIndex + 1
            node.children.forEach { child ->
                val at = headingIndexFor(blocks, child.pageIndex, childFrom, limit)
                if (at == null) {
                    dropped++
                } else {
                    anchors[at] = Anchor(chapterIndex, child.title, anchorId(anchors.size + 1))
                    childFrom = at + 1
                }
            }
        }
        return OutlineSplits(chapters, anchors, dropped)
    }

    /** First heading on the page, else the first block that reaches it. */
    private fun blockIndexFor(blocks: List<Block>, pageIndex: Int, from: Int): Int? {
        if (from >= blocks.size) return null
        val heading = (from until blocks.size).firstOrNull {
            blocks[it] is Block.Heading && blocks[it].pageIndex == pageIndex
        }
        return heading ?: (from until blocks.size).firstOrNull { blocks[it].pageIndex >= pageIndex }
    }

    private fun headingIndexFor(blocks: List<Block>, pageIndex: Int, from: Int, until: Int): Int? =
        (from until minOf(until, blocks.size)).firstOrNull {
            blocks[it] is Block.Heading && blocks[it].pageIndex >= pageIndex
        }

    private fun anchorId(number: Int) = "h-" + number.toString().padStart(CHAPTER_ID_WIDTH, '0')

    private data class ChapterSplit(val blockIndex: Int, val title: String)

    private data class Anchor(val chapterIndex: Int, val title: String, val id: String)

    private class OutlineSplits(
        val chapters: List<ChapterSplit>,
        private val anchors: Map<Int, Anchor>,
        val dropped: Int
    ) {
        fun chapterTitleAt(blockIndex: Int): String? = chapters.firstOrNull { it.blockIndex == blockIndex }?.title

        fun anchorAt(blockIndex: Int): String? = anchors[blockIndex]?.id

        fun childrenOf(chapterIndex: Int): List<Anchor> = anchors.values.filter { it.chapterIndex == chapterIndex }
    }

    private fun ChapterListBuilder.outlineNavigation(
        splits: OutlineSplits,
        chapters: List<EpubChapter>
    ): List<NavEntry> {
        // Blocks before the first outline entry become a leading chapter, which shifts the rest.
        val offset = chapters.size - splits.chapters.size
        return splits.chapters.mapIndexedNotNull { index, split ->
            val chapter = chapters.getOrNull(index + offset) ?: return@mapIndexedNotNull null
            val href = "text/${chapter.id}.xhtml"
            NavEntry(
                title = split.title,
                href = href,
                children = splits.childrenOf(index).map { NavEntry(it.title, "$href#${it.id}") }
            )
        }
    }

    /** Collects image bytes once each: repeated logos and rules should not be stored per page. */
    private class ImageCollector {
        private val hrefsByDigest = mutableMapOf<String, String>()
        private val collected = mutableListOf<EpubResource>()

        fun add(block: Block.Image): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(block.bytes)
                .joinToString("") { "%02x".format(it) }
            hrefsByDigest[digest]?.let { return it }

            val href = "$IMAGES_DIR/img-${(collected.size + 1).toString().padStart(CHAPTER_ID_WIDTH, '0')}" +
                extensionFor(block.mediaType)
            hrefsByDigest[digest] = href
            collected += EpubResource(href, block.mediaType, block.bytes)
            return href
        }

        fun resources(): List<EpubResource> = collected.toList()

        private fun extensionFor(mediaType: String) = when (mediaType) {
            "image/png" -> ".png"
            "image/gif" -> ".gif"
            else -> ".jpg"
        }
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
}
