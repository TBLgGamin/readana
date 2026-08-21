package dev.readana.feature.reader

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject
import org.readium.r2.navigator.util.BaseActionModeCallback

/**
 * Bridges Readium's text-selection action mode to the reading surface. The navigator is built
 * before any Compose state exists, so the action mode talks to this relay and the reader fills in
 * the actions while it is on screen.
 */
@ActivityRetainedScoped
internal class ReaderSelectionRelay @Inject constructor() {
    interface Actions {
        /** Marks the current selection. */
        fun highlight()

        /** Marks the current selection and opens the note editor. */
        fun annotate()

        /** Looks the selection up in whatever dictionary the device offers. */
        fun define()
    }

    var actions: Actions? = null

    /** Labels come from the reader's resources, which the action mode has no access to. */
    var labels: Labels = Labels()

    data class Labels(val highlight: String = "Highlight", val note: String = "Note", val define: String = "Define")
}

/** Adds Readana's own items to the selection menu, keeping the platform ones (copy, share). */
internal class ReaderSelectionActionMode(private val relay: ReaderSelectionRelay) : BaseActionModeCallback() {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.add(Menu.NONE, ITEM_HIGHLIGHT, Menu.FIRST, relay.labels.highlight)
        menu.add(Menu.NONE, ITEM_NOTE, Menu.FIRST + 1, relay.labels.note)
        menu.add(Menu.NONE, ITEM_DEFINE, Menu.FIRST + 2, relay.labels.define)
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val actions = relay.actions ?: return false
        when (item.itemId) {
            ITEM_HIGHLIGHT -> actions.highlight()
            ITEM_NOTE -> actions.annotate()
            ITEM_DEFINE -> actions.define()
            else -> return false
        }
        mode.finish()
        return true
    }

    private companion object {
        const val ITEM_HIGHLIGHT = 1
        const val ITEM_NOTE = 2
        const val ITEM_DEFINE = 3
    }
}
