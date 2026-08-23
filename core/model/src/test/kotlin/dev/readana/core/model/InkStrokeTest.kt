package dev.readana.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The layout fingerprint is what stops ink being drawn confidently over text that has moved, so it
 * has to change for exactly the settings that move text and no others.
 */
class InkStrokeTest {
    @Test
    fun `the same settings give the same fingerprint`() {
        assertEquals(
            InkStroke.fingerprintOf(ReaderSettings()),
            InkStroke.fingerprintOf(ReaderSettings())
        )
    }

    @Test
    fun `a bigger font moves the text, so the fingerprint changes`() {
        val before = InkStroke.fingerprintOf(ReaderSettings(fontScale = 1.0f))
        val after = InkStroke.fingerprintOf(ReaderSettings(fontScale = 1.4f))

        assertNotEquals(before, after)
    }

    @Test
    fun `every setting that reflows the page is part of the fingerprint`() {
        val base = ReaderSettings()
        val reflowing = listOf(
            base.copy(fontFamily = ReaderFont.Serif),
            base.copy(lineHeight = base.lineHeight + 0.3f),
            base.copy(paragraphSpacing = base.paragraphSpacing + 0.5f),
            base.copy(pageMargins = base.pageMargins + 0.5f),
            base.copy(pageTurnMode = PageTurnMode.Scrolled),
            base.copy(columns = ReaderColumns.Two)
        )

        reflowing.forEach { settings ->
            assertNotEquals(
                InkStroke.fingerprintOf(base),
                InkStroke.fingerprintOf(settings),
                "changing to $settings should have moved the text"
            )
        }
    }

    @Test
    fun `settings that only change how the page looks leave ink where it is`() {
        val base = ReaderSettings()
        // A different theme repaints the page; it does not re-lay it out, so ink stays put and
        // flagging it as misplaced would be wrong.
        val cosmetic = listOf(
            base.copy(theme = ReaderTheme.Dark),
            base.copy(keepScreenOn = !base.keepScreenOn),
            base.copy(tapToTurnPages = !base.tapToTurnPages),
            base.copy(volumeKeysTurnPages = !base.volumeKeysTurnPages),
            base.copy(palmRejection = !base.palmRejection),
            base.copy(penMode = PenMode.Ink)
        )

        cosmetic.forEach { settings ->
            assertEquals(
                InkStroke.fingerprintOf(base),
                InkStroke.fingerprintOf(settings),
                "changing to $settings should not have moved the text"
            )
        }
    }
}
