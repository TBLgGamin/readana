package dev.readana.core.data.conversion

import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.model.Book
import dev.readana.engine.pdf2epub.ConversionProgress
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class FakeConversionManager : ConversionManager {
    val enqueued = mutableListOf<String>()
    val awaited = mutableListOf<String>()
    var outcome: (String) -> AppResult<Book> = { AppError.Unexpected("no conversion configured").asFailure() }

    override val active: StateFlow<Map<String, ConversionProgress>> = MutableStateFlow(emptyMap())

    override val events: SharedFlow<ConversionEvent> = MutableSharedFlow()

    override fun enqueue(bookId: String) {
        enqueued += bookId
    }

    override suspend fun convertAndWait(bookId: String): AppResult<Book> {
        awaited += bookId
        return outcome(bookId)
    }
}
