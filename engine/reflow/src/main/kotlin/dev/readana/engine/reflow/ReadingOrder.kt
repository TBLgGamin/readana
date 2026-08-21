package dev.readana.engine.reflow

import dev.readana.engine.reflow.model.PageContent
import dev.readana.engine.reflow.model.PageImage
import dev.readana.engine.reflow.model.PageText
import dev.readana.engine.reflow.model.TextLine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One thing to read, in the order it should be read. Flattening pages into these up front is what
 * lets the paragraph builder stay simple while columns and figures are handled correctly.
 */
internal sealed interface ReadingItem {
    val pageIndex: Int

    /** Left edge of the column band this belongs to; the indent rule measures against it. */
    val columnLeft: Float

    /** 0-based column, or [SPANNING] for full-width items such as a title or a wide figure. */
    val columnIndex: Int

    data class Line(
        override val pageIndex: Int,
        override val columnLeft: Float,
        override val columnIndex: Int,
        val line: TextLine
    ) : ReadingItem

    class Image(
        override val pageIndex: Int,
        override val columnLeft: Float,
        override val columnIndex: Int,
        val image: PageImage
    ) : ReadingItem

    companion object {
        const val SPANNING = -1
    }
}

/**
 * Finds the gutter of a two-column page. Deliberately timid: unless the evidence is strong the
 * page is treated as one column, because splitting a single-column page interleaves its text.
 */
internal class ColumnDetector(private val options: ReflowOptions) {
    /** The x of the gutter centre, or null when the page reads as one column. */
    fun detectGutter(page: PageText, bodyFontSize: Float): Float? {
        if (!options.detectColumns) return null
        val body = page.lines.filter { abs(it.fontSize - bodyFontSize) <= bodyFontSize * BODY_TOLERANCE }
        if (body.size < MIN_BODY_LINES) return null

        val centre = gutterCentre(body, bodyFontSize) ?: return null
        return centre.takeIf { balanced(body, it) }
    }

    /** The widest empty vertical band, if one is wide enough and central enough to be a gutter. */
    private fun gutterCentre(body: List<TextLine>, bodyFontSize: Float): Float? {
        val textLeft = body.minOf { it.left }
        val textWidth = body.maxOf { it.right } - textLeft
        if (textWidth <= 0f) return null

        val covered = BooleanArray(BUCKETS)
        body.forEach { line ->
            val from = bucketOf(line.left, textLeft, textWidth)
            val to = bucketOf(line.right, textLeft, textWidth)
            for (bucket in from..to) covered[bucket] = true
        }

        val valley = widestValley(covered, textLeft, textWidth) ?: return null
        if (valley.width < bodyFontSize * MIN_GUTTER_FACTOR) return null

        val position = (valley.centre - textLeft) / textWidth
        return valley.centre.takeIf { position in MIN_SPLIT_POSITION..MAX_SPLIT_POSITION }
    }

    /** Both sides must carry real text, not a stray indent or a two-word label. */
    private fun balanced(body: List<TextLine>, gutter: Float): Boolean {
        val left = body.filter { it.right <= gutter }
        val right = body.filter { it.left >= gutter }
        if (left.size < MIN_LINES_PER_SIDE || right.size < MIN_LINES_PER_SIDE) return false

        val leftChars = left.sumOf { it.text.length }
        val rightChars = right.sumOf { it.text.length }
        val total = (leftChars + rightChars).toFloat()
        if (total <= 0f) return false
        return min(leftChars, rightChars) / total >= MIN_SIDE_SHARE
    }

    private fun widestValley(covered: BooleanArray, textLeft: Float, textWidth: Float): Valley? {
        var best: Valley? = null
        var runStart = -1
        for (bucket in covered.indices) {
            if (!covered[bucket]) {
                if (runStart < 0) runStart = bucket
            } else if (runStart >= 0) {
                best = longer(best, valley(runStart, bucket - 1, textLeft, textWidth))
                runStart = -1
            }
        }
        // A trailing gap is the right margin, not a gutter, so it is deliberately not considered.
        return best
    }

    private fun longer(a: Valley?, b: Valley): Valley? = if (a == null || b.width > a.width) b else a

    private fun valley(fromBucket: Int, toBucket: Int, textLeft: Float, textWidth: Float): Valley {
        val bucketWidth = textWidth / BUCKETS
        val from = textLeft + fromBucket * bucketWidth
        val to = textLeft + (toBucket + 1) * bucketWidth
        return Valley(from, to)
    }

    private fun bucketOf(x: Float, textLeft: Float, textWidth: Float): Int =
        (((x - textLeft) / textWidth) * BUCKETS).roundToInt().coerceIn(0, BUCKETS - 1)

    private data class Valley(val from: Float, val to: Float) {
        val width: Float get() = to - from
        val centre: Float get() = (from + to) / 2f
    }

    private companion object {
        const val BUCKETS = 120
        const val BODY_TOLERANCE = 0.1f
        const val MIN_BODY_LINES = 8
        const val MIN_LINES_PER_SIDE = 4
        const val MIN_SIDE_SHARE = 0.25f
        const val MIN_GUTTER_FACTOR = 1.5f
        const val MIN_SPLIT_POSITION = 0.3f
        const val MAX_SPLIT_POSITION = 0.7f
    }
}

/** Flattens pages into reading order, splitting two-column pages and slotting images in by position. */
internal class PageSegmenter(options: ReflowOptions) {
    private val detector = ColumnDetector(options)

    var multiColumnPages: Int = 0
        private set

    fun segment(pages: List<PageContent>, bodyFontSize: Float): List<ReadingItem> {
        multiColumnPages = 0
        return pages.flatMap { page ->
            val gutter = detector.detectGutter(page.text, bodyFontSize)
            if (gutter == null) {
                singleColumn(page)
            } else {
                multiColumnPages++
                twoColumns(page, gutter)
            }
        }
    }

    private fun singleColumn(page: PageContent): List<ReadingItem> {
        val left = page.text.lines.minOfOrNull { it.left } ?: 0f
        val items = page.text.lines.map { line ->
            ReadingItem.Line(page.text.index, left, 0, line) as ReadingItem to line.baseline
        } + page.images.map { image ->
            ReadingItem.Image(page.text.index, left, 0, image) as ReadingItem to image.y
        }
        return items.sortedBy { it.second }.map { it.first }
    }

    /**
     * Rows that cross the gutter (a title, a wide figure) break the page into bands; within each
     * band the left column is read before the right one.
     */
    private fun twoColumns(page: PageContent, gutter: Float): List<ReadingItem> {
        val index = page.text.index
        val leftEdge = page.text.lines.filter { it.right <= gutter }.minOfOrNull { it.left }
            ?: page.text.lines.minOfOrNull { it.left } ?: 0f
        val rightEdge = page.text.lines.filter { it.left >= gutter }.minOfOrNull { it.left } ?: gutter

        val placed = page.text.lines.map { line ->
            val column = columnOf(line.left, line.right, gutter)
            Placed(
                top = line.baseline,
                column = column,
                item = ReadingItem.Line(index, columnLeft(column, leftEdge, rightEdge), column, line)
            )
        } + page.images.map { image ->
            val column = imageColumn(image, gutter)
            Placed(
                top = image.y,
                column = column,
                item = ReadingItem.Image(index, columnLeft(column, leftEdge, rightEdge), column, image)
            )
        }

        return orderBands(placed.sortedBy { it.top })
    }

    private fun orderBands(sorted: List<Placed>): List<ReadingItem> {
        val ordered = mutableListOf<ReadingItem>()
        val band = mutableListOf<Placed>()
        for (placed in sorted) {
            if (placed.column == ReadingItem.SPANNING) {
                ordered += flushBand(band)
                ordered += placed.item
            } else {
                band += placed
            }
        }
        ordered += flushBand(band)
        return ordered
    }

    private fun flushBand(band: MutableList<Placed>): List<ReadingItem> {
        if (band.isEmpty()) return emptyList()
        val ordered = band.sortedWith(compareBy({ it.column }, { it.top })).map { it.item }
        band.clear()
        return ordered
    }

    private fun columnOf(left: Float, right: Float, gutter: Float): Int = when {
        right <= gutter -> 0
        left >= gutter -> 1
        else -> ReadingItem.SPANNING
    }

    private fun imageColumn(image: PageImage, gutter: Float): Int {
        if (image.right <= gutter) return 0
        if (image.x >= gutter) return 1

        // It crosses the gutter. A figure with real width on both sides spans the page; one that
        // merely pokes over the edge belongs to the side holding most of it.
        val onLeft = gutter - image.x
        val onRight = image.right - gutter
        val minorSide = min(onLeft, onRight)
        return when {
            image.width > 0f && minorSide / image.width >= MIN_SPANNING_SIDE -> ReadingItem.SPANNING
            onLeft > onRight -> 0
            else -> 1
        }
    }

    private fun columnLeft(column: Int, leftEdge: Float, rightEdge: Float): Float =
        if (column == 1) rightEdge else leftEdge

    private data class Placed(val top: Float, val column: Int, val item: ReadingItem)

    private companion object {
        /** A crossing figure spans the page once this much of it sits on the narrower side. */
        const val MIN_SPANNING_SIDE = 0.2f
    }
}
