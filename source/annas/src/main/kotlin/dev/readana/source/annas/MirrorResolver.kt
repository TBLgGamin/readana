package dev.readana.source.annas

import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException

/** Thrown by a mirror attempt when the host answered but is clearly not serving the site. */
class MirrorUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Runs a request against the first mirror that works and remembers it for next time.
 *
 * Transport failures, [MirrorUnavailableException], 5xx and "we are refusing you" replies (403,
 * 429, …) move on to the next mirror; any other [AppResult.Failure] is returned as-is, because
 * retrying elsewhere would not help. A mirror that just failed is demoted for [cooldownMillis]
 * rather than dropped, so a domain the user's connection cannot reach stops costing a connect
 * timeout on every search without ever becoming unreachable to a connection that can.
 */
class MirrorResolver(
    private val cooldownMillis: Long = COOLDOWN_MS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    @Volatile
    var preferredMirror: String? = null
        private set

    private val failedAt = ConcurrentHashMap<String, Long>()

    suspend fun <T> withMirror(
        mirrors: List<String>,
        preferred: String? = null,
        onWorkingMirror: suspend (String) -> Unit = {},
        block: suspend (baseUrl: String) -> AppResult<T>
    ): AppResult<T> {
        if (mirrors.isEmpty()) {
            return AppError.Unsupported("No Anna's Archive mirror configured").asFailure()
        }
        // The one that worked this session beats the one that worked last time the app ran.
        val target = listOfNotNull(preferredMirror, preferred).firstOrNull { it in mirrors }
        val now = clock()
        val ordered = mirrors.sortedWith(
            compareBy({ if (it.isCoolingDown(now)) 1 else 0 }, { if (it == target) 0 else 1 })
        )

        var failure: AppError? = null
        for (mirror in ordered) {
            val outcome = attempt(mirror, block)
            if (outcome is AppResult.Failure && outcome.error.isMirrorProblem()) {
                failedAt[mirror] = clock()
                failure = mostTelling(failure, outcome.error)
                continue
            }
            failedAt.remove(mirror)
            preferredMirror = mirror
            if (mirror != preferred) onWorkingMirror(mirror)
            return outcome
        }
        return (failure ?: AppError.Network("All Anna's Archive mirrors failed")).asFailure()
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

    private fun String.isCoolingDown(now: Long): Boolean = failedAt[this]?.let { now - it < cooldownMillis } == true

    /**
     * What to report once everything has failed. A mirror that answered explains more than one
     * whose name did not resolve, and keeping the first such answer stops a trailing DNS error
     * from hiding "every mirror is refusing this connection".
     */
    private fun mostTelling(previous: AppError?, candidate: AppError): AppError = when {
        previous is AppError.Http -> previous
        candidate is AppError.Http -> candidate
        else -> previous ?: candidate
    }

    private fun AppError.isMirrorProblem(): Boolean = when {
        this is AppError.Network -> true

        this !is AppError.Http -> false

        statusCode >= SERVER_ERROR_START -> true

        // Mirrors are separate front-ends: one refusing this connection right now says nothing
        // about the next, and an ISP that blocks one domain rarely blocks them all.
        else -> statusCode in BLOCKED_STATUSES
    }

    private companion object {
        const val SERVER_ERROR_START = 500
        const val COOLDOWN_MS = 5 * 60 * 1000L

        /** Forbidden, request timeout, rate-limited, and blocked-for-legal-reasons. */
        val BLOCKED_STATUSES = setOf(403, 408, 429, 451)
    }
}
