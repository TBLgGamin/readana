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

        /** Marks the current selection and opens its note straight away. */
        fun note()

        /** Looks the current selection up in the dictionary. */
        fun define()

        /** Opens the assistant with a question about the current selection. */
        fun ask()

        /** Asks the assistant to explain the current selection straight away. */
        fun explain()
    }

    var actions: Actions? = null

    /** Labels come from the reader's resources, which the action mode has no access to. */
    var labels: Labels = Labels()

    /**
     * Whether the assistant items belong in the menu at all. False until a provider is configured;
     * a dead menu item is worse than no menu item.
     */
    var assistantEnabled: Boolean = false

    data class Labels(
        val highlight: String = "Highlight",
        val note: String = "Note",
        val define: String = "Define",
        val ask: String = "Ask",
        val explain: String = "Explain"
    )
}

/** Adds Readana's own items to the selection menu, keeping the platform ones (copy, share). */
internal class ReaderSelectionActionMode(private val relay: ReaderSelectionRelay) : BaseActionModeCallback() {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.putOurItems()
        return true
    }

    /**
     * The platform keeps one action mode alive across selections, so the menu is built once — long
     * before the assistant knows whether it has a provider. Rebuilding here means every selection
     * sees the current answer instead of whatever was true at the first long-press.
     */
    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.putOurItems()
        return true
    }

    private fun Menu.putOurItems() {
        listOf(ITEM_HIGHLIGHT, ITEM_NOTE, ITEM_DEFINE, ITEM_ASK, ITEM_EXPLAIN).forEach { removeItem(it) }
        // Our items go first, most-used first; the platform's copy and share stay where they are.
        addOurItem(ITEM_HIGHLIGHT, ORDER_HIGHLIGHT, relay.labels.highlight)
        addOurItem(ITEM_NOTE, ORDER_NOTE, relay.labels.note)
        addOurItem(ITEM_DEFINE, ORDER_DEFINE, relay.labels.define)
        if (relay.assistantEnabled) {
            addOurItem(ITEM_ASK, ORDER_ASK, relay.labels.ask)
            addOurItem(ITEM_EXPLAIN, ORDER_EXPLAIN, relay.labels.explain)
        }
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val actions = relay.actions ?: return false
        when (item.itemId) {
            ITEM_HIGHLIGHT -> actions.highlight()
            ITEM_NOTE -> actions.note()
            ITEM_DEFINE -> actions.define()
            ITEM_ASK -> actions.ask()
            ITEM_EXPLAIN -> actions.explain()
            else -> return false
        }
        // Finish first: the floating toolbar must be gone before a sheet takes the focus.
        mode.finish()
        return true
    }

    /**
     * The selection toolbar hides anything it cannot fit behind an overflow arrow, and the
     * platform's own copy/share get first refusal; asking for room keeps our items visible.
     */
    private fun Menu.addOurItem(id: Int, order: Int, title: String) {
        add(Menu.NONE, id, order, title).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
    }

    private companion object {
        /** The order they sit in, most-used first; the toolbar overflows the tail. */
        val ORDER_HIGHLIGHT = Menu.FIRST
        val ORDER_NOTE = Menu.FIRST + 1
        val ORDER_DEFINE = Menu.FIRST + 2
        val ORDER_ASK = Menu.FIRST + 3
        val ORDER_EXPLAIN = Menu.FIRST + 4

        const val ITEM_HIGHLIGHT = 1
        const val ITEM_NOTE = 2
        const val ITEM_DEFINE = 3
        const val ITEM_ASK = 4
        const val ITEM_EXPLAIN = 5
    }
}
