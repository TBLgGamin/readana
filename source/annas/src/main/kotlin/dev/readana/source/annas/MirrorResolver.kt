package dev.readana.source.annas

import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import kotlinx.coroutines.CancellationException

/** Thrown by a mirror attempt when the host answered but is clearly not serving the site. */
class MirrorUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Runs a request against the first mirror that works and remembers it for next time.
 * Transport exceptions, [MirrorUnavailableException] and 5xx results move on to the next mirror;
 * any other [AppResult.Failure] is returned as-is because retrying elsewhere would not help.
 */
class MirrorResolver {
    @Volatile
    var preferredMirror: String? = null
        private set

    suspend fun <T> withMirror(mirrors: List<String>, block: suspend (baseUrl: String) -> AppResult<T>): AppResult<T> {
        if (mirrors.isEmpty()) {
            return AppError.Unsupported("No Anna's Archive mirror configured").asFailure()
        }
        val preferred = preferredMirror?.takeIf { it in mirrors }
        val ordered = listOfNotNull(preferred) + mirrors.filter { it != preferred }

        var lastFailure: AppError? = null
        for (mirror in ordered) {
            val outcome = attempt(mirror, block)
            if (outcome is AppResult.Failure && outcome.error.isMirrorProblem()) {
                lastFailure = outcome.error
                continue
            }
            preferredMirror = mirror
            return outcome
        }
        return (lastFailure ?: AppError.Network("All Anna's Archive mirrors failed")).asFailure()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> attempt(mirror: String, block: suspend (String) -> AppResult<T>): AppResult<T> = try {
        block(mirror)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (unavailable: MirrorUnavailableException) {
        AppError.Network("$mirror unavailable: ${unavailable.message}", unavailable).asFailure()
    } catch (failure: Exception) {
        AppError.Network(
            "$mirror failed: ${failure.message ?: failure::class.simpleName}",
            failure
        ).asFailure()
    }

    private fun AppError.isMirrorProblem(): Boolean =
        this is AppError.Network || (this is AppError.Http && statusCode >= SERVER_ERROR_START)

    private companion object {
        const val SERVER_ERROR_START = 500
    }
}
