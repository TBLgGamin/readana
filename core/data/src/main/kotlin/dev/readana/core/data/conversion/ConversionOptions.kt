package dev.readana.core.data.conversion

import dev.readana.core.model.ConversionSettings
import dev.readana.core.model.HeadingSensitivity
import dev.readana.engine.pdf2epub.PdfExtractionOptions
import dev.readana.engine.reflow.ReflowOptions

/**
 * Maps the four settings a reader can judge onto the engine's tunables. Everything else keeps its
 * engine default, so the exposed surface stays small.
 *
 * Heading sensitivity moves two knobs together: how much larger than body text a line must be, and
 * how long a line may still be to count as a heading.
 */
internal fun ConversionSettings.toReflowOptions(): ReflowOptions = ReflowOptions(
    removeRepeatedHeadersAndFooters = removeHeadersAndFooters,
    headingSizeFactor = when (headingSensitivity) {
        HeadingSensitivity.Conservative -> 1.30f
        HeadingSensitivity.Balanced -> 1.15f
        HeadingSensitivity.Aggressive -> 1.05f
    },
    maxHeadingChars = when (headingSensitivity) {
        HeadingSensitivity.Conservative -> 80
        HeadingSensitivity.Balanced -> 120
        HeadingSensitivity.Aggressive -> 160
    }
)

internal fun ConversionSettings.toExtractionOptions(): PdfExtractionOptions =
    PdfExtractionOptions(extractImages = extractImages)
