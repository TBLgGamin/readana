package dev.readana.feature.reader

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import dagger.hilt.android.scopes.ActivityRetainedScoped
import dev.readana.ai.client.TextWalker
import dev.readana.core.model.PageTurnMode
import dev.readana.core.model.ReaderSettings
import javax.inject.Inject
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.search.SearchIterator

/** One entry of a publication's table of contents, flattened with its nesting depth. */
internal data class TocEntry(val title: String, val depth: Int, val locator: Locator?)

/**
 * An open publication together with everything the navigator needs to render it. Created once per
 * book and kept alive across configuration changes so page-turn state survives rotation.
 */
internal class ReaderSession(
    val bookId: String,
    val title: String,
    val navigatorClass: Class<out Fragment>,
    val fragmentFactory: FragmentFactory,
    val toc: List<TocEntry>,
    /** Every reading position, used to map the progress slider onto locators. */
    val positions: List<Locator>,
    /**
     * Where each [toc] entry starts, as an index into [positions], or -1 where it cannot be
     * placed. This is what turns "how far to the end of the chapter" into a number.
     */
    val tocPositions: List<Int>,
    /** False for formats Readium cannot search, such as PDFs opened through pdfium. */
    val isSearchable: Boolean,
    /** False when the publication exposes no text, which is what the assistant reads. */
    val hasText: Boolean,
    /**
     * Reads the book's own text around a locator, for the assistant's context. Null for
     * publications without a text service.
     */
    val textWalker: (Locator?) -> TextWalker?,
    /** Reads one of the book's own illustrations back out, by the href a figure was recorded with. */
    val loadFigure: suspend (String) -> ByteArray?,
    /**
     * Builds a read-aloud navigator over the same publication, from a starting locator.
     *
     * A closure rather than a navigator, for the same reason [search] is one: the session hands
     * out what the publication can do without ever handing out the publication. Null for anything
     * Readium cannot speak — a PDF opened through pdfium, for instance.
     */
    val createTts: suspend (Locator?, Double, () -> Unit) -> ReadanaTtsNavigator?,
    /** Fixed-layout EPUBs (for example comics) are page-based and cannot switch to scrolling. */
    val isFixedLayout: Boolean = false,
    val search: suspend (String) -> SearchIterator?,
    private val onClose: () -> Unit
) {
    fun close() = onClose()
}

/** Fixed-layout publications ignore scrolling preferences, so expose their actual page mode. */
internal fun ReaderSettings.forLayout(isFixedLayout: Boolean): ReaderSettings =
    if (isFixedLayout) copy(pageTurnMode = PageTurnMode.Paginated) else this

/**
 * Hands the live session to [ReaderNavigatorHostFragment], which is instantiated by the fragment
 * framework and so cannot receive it as a parameter. Activity-retained, so it survives rotation
 * but not process death; after process death the reader opens the book again.
 */
@ActivityRetainedScoped
internal class ReaderSessionStore @Inject constructor() {
    var session: ReaderSession? = null
}
