package dev.readana.core.data.settings

import dev.readana.core.model.PageTurnMode
import dev.readana.core.model.ReaderColumns
import dev.readana.core.model.ReaderFont
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.ReaderTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderSettingsJsonTest {
    @Test
    fun `round-trips every setting`() {
        val settings = ReaderSettings(
            fontScale = 1.4f,
            fontFamily = ReaderFont.Serif,
            theme = ReaderTheme.Sepia,
            lineHeight = 1.8f,
            paragraphSpacing = 1.2f,
            pageMargins = 2.5f,
            pageTurnMode = PageTurnMode.Scrolled,
            columns = ReaderColumns.Two,
            tapToTurnPages = false,
            volumeKeysTurnPages = true,
            keepScreenOn = false
        )

        assertEquals(settings, settings.encodeToJson().decodeReaderSettings())
    }

    @Test
    fun `no stored override reads as none`() {
        assertNull(null.decodeReaderSettings())
        assertNull("".decodeReaderSettings())
        assertNull("   ".decodeReaderSettings())
    }

    @Test
    fun `a blob that cannot be read costs this book its adjustments and nothing else`() {
        assertNull("not json at all".decodeReaderSettings())
        assertNull("""{"fontScale":"much too big"}""".decodeReaderSettings())
    }

    @Test
    fun `an enum member that no longer exists reads as the default`() {
        val decoded = """{"theme":"Solarized","fontFamily":"Comic"}""".decodeReaderSettings()

        assertEquals(ReaderTheme.System, decoded?.theme)
        assertEquals(ReaderFont.Publisher, decoded?.fontFamily)
    }

    @Test
    fun `values are clamped back into range on the way out`() {
        val decoded = """{"fontScale":99.0,"lineHeight":-4.0,"pageMargins":100.0}"""
            .decodeReaderSettings()

        assertEquals(ReaderSettings.FONT_SCALE_RANGE.endInclusive, decoded?.fontScale)
        assertEquals(ReaderSettings.LINE_HEIGHT_RANGE.start, decoded?.lineHeight)
        assertEquals(ReaderSettings.PAGE_MARGIN_RANGE.endInclusive, decoded?.pageMargins)
    }

    @Test
    fun `a partial blob fills the gaps with the defaults`() {
        val decoded = """{"fontScale":1.5}""".decodeReaderSettings()
        val defaults = ReaderSettings()

        assertEquals(1.5f, decoded?.fontScale)
        assertEquals(defaults.lineHeight, decoded?.lineHeight)
        assertEquals(defaults.theme, decoded?.theme)
    }
}
