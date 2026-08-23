package dev.readana.feature.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.commitNow
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.pdf.PdfNavigatorFragment

/**
 * Hosts a Readium navigator fragment inside its own child fragment manager.
 *
 * Readium's navigator fragments have no no-argument constructor, so the fragment manager that
 * restores them needs a matching [FragmentFactory] before `super.onCreate` runs. Owning a child
 * manager here is what makes that possible: the activity's manager only ever restores this
 * no-argument host. On a configuration change the retained [ReaderSessionStore] still holds the
 * real factory, so the navigator restores with its WebView and position intact. After process
 * death the store is empty, the restored navigator is a throwaway that is removed before it can
 * resume, and the reader composes a fresh host once the publication has been opened again.
 */
@AndroidEntryPoint
internal class ReaderNavigatorHostFragment : Fragment() {
    @Inject
    lateinit var sessionStore: ReaderSessionStore

    private var onNavigatorChanged: ((Navigator?) -> Unit)? = null
    private var restoredWithoutSession = false
    private var surface: ReaderSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val session = sessionStore.session
        restoredWithoutSession = session == null && savedInstanceState != null
        childFragmentManager.fragmentFactory = session?.fragmentFactory ?: DiscardingFactory
        super.onCreate(savedInstanceState)
        if (restoredWithoutSession) {
            discardRestoredNavigator()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // The navigator sits inside a surface that sees touches first, which is the only place a
        // pen can still be told from a palm — see ReaderSurfaceView.
        val surface = ReaderSurfaceView(requireContext())
        surface.addView(FragmentContainerView(requireContext()).apply { id = CONTAINER_ID })
        this.surface = surface
        val session = sessionStore.session
        if (session != null && childFragmentManager.findFragmentByTag(NAVIGATOR_TAG) == null) {
            childFragmentManager.commitNow {
                add(CONTAINER_ID, session.navigatorClass, Bundle(), NAVIGATOR_TAG)
            }
        }
        return surface
    }

    /** The surface the pen draws on, for the reader to configure once it is composed. */
    fun surface(): ReaderSurfaceView? = surface

    override fun onStart() {
        super.onStart()
        notifyNavigator()
    }

    override fun onDestroyView() {
        onNavigatorChanged?.invoke(null)
        surface = null
        super.onDestroyView()
    }

    /** Called by Compose to receive the navigator as soon as it exists. */
    fun setOnNavigatorChanged(listener: ((Navigator?) -> Unit)?) {
        onNavigatorChanged = listener
        if (listener != null) {
            notifyNavigator()
        }
    }

    private fun notifyNavigator() {
        val navigator = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? Navigator
        onNavigatorChanged?.invoke(navigator)
    }

    private fun discardRestoredNavigator() {
        val restored = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG) ?: return
        childFragmentManager.commitNow { remove(restored) }
    }

    private companion object {
        val CONTAINER_ID = R.id.feature_reader_navigator_container
        const val NAVIGATOR_TAG = "readana:navigator"

        /**
         * Instantiates Readium's throwaway navigators after process death, so the restore does not
         * crash before [discardRestoredNavigator] removes them.
         */
        val DiscardingFactory = object : FragmentFactory() {
            override fun instantiate(classLoader: ClassLoader, className: String): Fragment = when (className) {
                EpubNavigatorFragment::class.java.name ->
                    EpubNavigatorFragment.createDummyFactory()
                        .instantiate(classLoader, className)

                PdfNavigatorFragment::class.java.name ->
                    PdfNavigatorFragment.createDummyFactory(PdfiumEngineProvider())
                        .instantiate(classLoader, className)

                else -> super.instantiate(classLoader, className)
            }
        }
    }
}
