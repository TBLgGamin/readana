package dev.readana.feature.reader

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import dagger.hilt.android.scopes.ActivityRetainedScoped
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
    /** False for formats Readium cannot search, such as PDFs opened through pdfium. */
    val isSearchable: Boolean,
    val search: suspend (String) -> SearchIterator?,
    private val onClose: () -> Unit
) {
    fun close() = onClose()
}

/**
 * Hands the live session to [ReaderNavigatorHostFragment], which is instantiated by the fragment
 * framework and so cannot receive it as a parameter. Activity-retained, so it survives rotation
 * but not process death; after process death the reader opens the book again.
 */
@ActivityRetainedScoped
internal class ReaderSessionStore @Inject constructor() {
    var session: ReaderSession? = null
}
