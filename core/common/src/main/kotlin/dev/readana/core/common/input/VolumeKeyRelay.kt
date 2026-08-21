package dev.readana.core.common.input

import javax.inject.Inject
import javax.inject.Singleton

enum class VolumeKey {
    Up,
    Down
}

/**
 * Lets the reader claim the volume keys for page turns without the activity knowing about it.
 * The activity forwards key presses here; whatever screen is reading registers a handler and
 * returns true for the presses it consumed, so volume still works everywhere else.
 */
@Singleton
class VolumeKeyRelay @Inject constructor() {
    private var handler: ((VolumeKey) -> Boolean)? = null

    fun setHandler(handler: ((VolumeKey) -> Boolean)?) {
        this.handler = handler
    }

    fun dispatch(key: VolumeKey): Boolean = handler?.invoke(key) == true
}
