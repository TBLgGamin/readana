package dev.readana.core.common.coroutines

import kotlinx.coroutines.CancellationException

/**
 * Like [runCatching], but never swallows coroutine cancellation.
 * Catching [CancellationException] breaks structured concurrency, which is why plain
 * `runCatching` is banned in suspending code in this project.
 */
@Suppress("TooGenericExceptionCaught")
suspend inline fun <T> suspendRunCatching(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (exception: Exception) {
    Result.failure(exception)
}
