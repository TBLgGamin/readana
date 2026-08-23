package dev.readana.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import dev.readana.core.designsystem.theme.ReadanaTheme
import dev.readana.core.model.CatalogPreset
import dev.readana.core.model.ConversionSettings
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.SourceSettings
import dev.readana.core.model.StoredQuota
import dev.readana.source.api.SourceConfigField
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The settings tabs, and the Sources page in particular: it is rendered entirely from what each
 * source declared, so a golden is the cheapest way to notice a source's fields going missing.
 *
 * `./gradlew testDebugUnitTest` checks these; `-PrecordScreenshots=true` accepts them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `reading tab`() = capture("settings-reading")

    @Test
    fun `sources tab`() = capture("settings-sources", tab = "Sources")

    @Test
    fun `sources tab, dark`() = capture("settings-sources-dark", tab = "Sources", darkTheme = true)

    @Test
    fun `files tab`() = capture("settings-files", tab = "Files")

    @Test
    fun `about tab`() = capture("settings-about", tab = "About")

    private fun capture(name: String, tab: String? = null, darkTheme: Boolean = false) {
        composeTestRule.setContent {
            ReadanaTheme(darkTheme = darkTheme) {
                SettingsScreen(
                    uiState = loadedState(),
                    aiState = AiSettingsUiState(),
                    aiActions = noopAiActions(),
                    actions = noopActions()
                )
            }
        }
        tab?.let { composeTestRule.onNodeWithText(it).performClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun loadedState() = SettingsUiState.Loaded(
        sources = SourceSettings(),
        conversion = ConversionSettings(),
        reading = ReaderSettings(),
        libraryFolderUri = null,
        sourceRows = listOf(
            SourceRow(
                id = "annas-archive",
                name = "Anna's Archive",
                summary = "Shadow library. Needs a membership key for fast downloads.",
                enabled = true,
                fields = listOf(
                    SourceConfigField("mirrors", "Mirrors", SourceConfigField.Kind.UrlList),
                    SourceConfigField("secretKey", "Membership key", SourceConfigField.Kind.Secret)
                ),
                values = emptyMap(),
                quota = StoredQuota(remaining = 7, perDay = 100),
                isRemovable = false
            ),
            SourceRow(
                id = "arxiv",
                name = "arXiv",
                summary = "Open-access preprints, converted from PDF.",
                enabled = false,
                fields = listOf(SourceConfigField("categories", "Categories", SourceConfigField.Kind.Text)),
                values = mapOf("categories" to "cs.AI, cs.LG"),
                quota = null,
                isRemovable = false
            ),
            SourceRow(
                id = "catalog-standard-ebooks",
                name = "Standard Ebooks",
                summary = "https://standardebooks.org/feeds/opds",
                enabled = true,
                fields = emptyList(),
                values = emptyMap(),
                quota = null,
                isRemovable = true
            ),
            SourceRow(
                id = "openlibrary",
                name = "Open Library",
                summary = "Public-domain scans from the Internet Archive.",
                enabled = true,
                fields = emptyList(),
                values = emptyMap(),
                quota = null,
                isRemovable = false
            )
        ),
        availablePresets = listOf(
            CatalogPreset(
                id = "doab",
                name = "Directory of Open Access Books",
                feedUrl = "https://directory.doabooks.org/feed/opds",
                description = "Peer-reviewed academic books, openly licensed."
            )
        )
    )

    private fun noopActions() = SettingsActions(
        onLibraryFolderChange = {},
        onSourceEnabledChange = { _, _ -> },
        onSourceConfigChange = { _, _, _ -> },
        onAddPresetCatalog = {},
        onAddCatalog = { _, _ -> true },
        onRemoveCatalog = {},
        onAutoConvertChange = {},
        onRemoveHeadersChange = {},
        onHeadingSensitivityChange = {},
        onExtractImagesChange = {},
        onReadingChange = {}
    )

    private fun noopAiActions() = AiSettingsActions(
        onSetActiveProvider = {},
        onApiKeyChange = { _, _ -> },
        onModelChange = { _, _ -> },
        onBaseUrlChange = { _, _ -> },
        onAddCustomProvider = {},
        onRemoveProvider = {},
        onFetchModels = {},
        onTestProvider = {},
        onContextSizeChange = {},
        onRecapEnabledChange = {},
        onIllustrateChange = {},
        onRecapAfterDaysChange = {}
    )
}
