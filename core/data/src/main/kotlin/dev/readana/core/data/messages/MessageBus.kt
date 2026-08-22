package dev.readana.core.data.messages

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Something to tell the reader, named by resource rather than resolved.
 *
 * Carrying the id means the message can be raised from a ViewModel, outlive the screen that raised
 * it, and still be worded in the reader's own language when it is finally shown.
 */
sealed interface UiMessage {
    data class Text(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiMessage

    data class Quantity(@param:PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiMessage

    companion object {
        fun text(@StringRes id: Int, vararg args: Any): UiMessage = Text(id, args.toList())

        fun quantity(@PluralsRes id: Int, count: Int, vararg args: Any): UiMessage = Quantity(id, count, args.toList())
    }
}

/**
 * Where every passing message goes, so exactly one thing on screen shows them.
 *
 * Each screen used to keep its own host, which meant a message only appeared if the reader
 * happened to still be on the screen that raised it: a download queued from the in-app browser
 * said so to a browser that was already closing, and an import finishing while you were in
 * Settings said nothing at all. The app shell holds the only host now, and this is how anything
 * reaches it.
 */
interface MessageBus {
    val messages: Flow<UiMessage>

    fun show(message: UiMessage)
}

@Singleton
class DefaultMessageBus @Inject constructor() : MessageBus {
    // Buffered rather than replayed: a message that has been shown is spent, but one raised a beat
    // before the shell starts collecting should still arrive.
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = BUFFER)
    override val messages: Flow<UiMessage> = _messages.asSharedFlow()

    override fun show(message: UiMessage) {
        _messages.tryEmit(message)
    }

    private companion object {
        const val BUFFER = 8
    }
}
