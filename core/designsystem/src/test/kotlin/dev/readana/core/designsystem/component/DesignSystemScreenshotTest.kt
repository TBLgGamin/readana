package dev.readana.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.theme.ReadanaTheme
import dev.readana.core.model.HighlightTint
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * What the shared components look like, in both themes.
 *
 * These run in the ordinary unit-test task: `./gradlew testDebugUnitTest` checks them against the
 * committed goldens, and `-PrecordScreenshots=true` accepts what the UI looks like now. Goldens
 * live in `src/test/screenshots`, so a change to any of them shows up as an image diff in review
 * rather than being noticed on a device weeks later.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignSystemScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `empty state`() = capture("empty-state") {
        EmptyState(
            icon = ReadanaIcons.Library,
            title = "Your library is empty",
            description = "Books you download or import will appear here."
        )
    }

    @Test
    fun `empty state, dark`() = capture("empty-state-dark", darkTheme = true) {
        EmptyState(
            icon = ReadanaIcons.Library,
            title = "Your library is empty",
            description = "Books you download or import will appear here."
        )
    }

    @Test
    fun `book cover placeholder`() = capture("book-cover-placeholder") {
        Box(Modifier.width(160.dp)) {
            BookCoverPlaceholder(title = "The Strange Case of Dr Jekyll and Mr Hyde")
        }
    }

    @Test
    fun `note row with a note`() = capture("note-row") {
        NoteRow(
            text = "It is a truth universally acknowledged, that a single man in possession of a " +
                "good fortune, must be in want of a wife.",
            tint = HighlightTint.Yellow,
            note = "The whole book in one sentence.",
            progression = 0.03,
            modifier = Modifier.fillMaxWidth()
        )
    }

    @Test
    fun `note row in every tint`() = capture("note-row-tints") {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
            HighlightTint.entries.forEach { tint ->
                NoteRow(text = tint.name, tint = tint, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    @Test
    fun `snackbar with an action`() = capture("snackbar-action") {
        Box(Modifier.padding(16.dp)) {
            ReadanaSnackbar(
                message = "Pride and Prejudice was added to your library",
                icon = ReadanaIcons.Download,
                actionLabel = "Open"
            )
        }
    }

    @Test
    fun `snackbar with an action, dark`() = capture("snackbar-action-dark", darkTheme = true) {
        Box(Modifier.padding(16.dp)) {
            ReadanaSnackbar(
                message = "Pride and Prejudice was added to your library",
                icon = ReadanaIcons.Download,
                actionLabel = "Open"
            )
        }
    }

    private fun capture(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            ReadanaTheme(darkTheme = darkTheme) {
                Surface { content() }
            }
        }
        // The snackbar rises and settles on first composition, so a capture taken immediately
        // would record a different frame every run.
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
