package dev.readana.source.annas

import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.common.result.asSuccess
import dev.readana.core.common.result.errorOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MirrorResolverTest {
    private val mirrors = listOf("https://one.test", "https://two.test", "https://three.test")
    private var now = 0L
    private val resolver = MirrorResolver(cooldownMillis = COOLDOWN, clock = { now })

    @Test
    fun `the mirror remembered from last run is tried first`() = runTest {
        val tried = mutableListOf<String>()

        resolver.withMirror(mirrors, preferred = "https://three.test") { mirror ->
            tried += mirror
            Unit.asSuccess()
        }

        assertEquals(listOf("https://three.test"), tried)
    }

    @Test
    fun `a mirror that just failed is tried last, not dropped`() = runTest {
        val firstRun = mutableListOf<String>()
        resolver.withMirror(mirrors) { mirror ->
            firstRun += mirror
            if (mirror == "https://one.test") AppError.Network("no route").asFailure() else Unit.asSuccess()
        }
        assertEquals(listOf("https://one.test", "https://two.test"), firstRun)

        // Same session, one minute later: the dead host must not cost another connect timeout.
        now += COOLDOWN / 5
        val secondRun = mutableListOf<String>()
        resolver.withMirror(mirrors) { mirror ->
            secondRun += mirror
            AppError.Network("no route").asFailure()
        }
        assertEquals(listOf("https://two.test", "https://three.test", "https://one.test"), secondRun)

        // Once the cooldown is over it takes its turn again, behind the last mirror that worked.
        now += COOLDOWN
        val thirdRun = mutableListOf<String>()
        resolver.withMirror(mirrors) { mirror ->
            thirdRun += mirror
            AppError.Network("no route").asFailure()
        }
        assertEquals(listOf("https://two.test", "https://one.test", "https://three.test"), thirdRun)
    }

    @Test
    fun `refusals and server errors move on, other failures are reported`() = runTest {
        val hopped = listOf(403, 408, 429, 451, 500, 503)
        hopped.forEach { status ->
            val tried = mutableListOf<String>()
            MirrorResolver(clock = { now }).withMirror(mirrors) { mirror ->
                tried += mirror
                AppError.Http(status).asFailure()
            }
            assertEquals(mirrors.size, tried.size, "HTTP $status should have tried every mirror")
        }

        val tried = mutableListOf<String>()
        MirrorResolver(clock = { now }).withMirror(mirrors) { mirror ->
            tried += mirror
            AppError.Http(404).asFailure()
        }
        assertEquals(1, tried.size, "a 404 means the same thing on every mirror")
    }

    @Test
    fun `a mirror that answered explains the failure better than one that did not resolve`() = runTest {
        val outcome = resolver.withMirror(mirrors) { mirror ->
            if (mirror == "https://one.test") {
                AppError.Http(429, "Anna's Archive is rate-limiting this connection").asFailure()
            } else {
                AppError.Network("$mirror failed: No such host").asFailure()
            }
        }

        val error = assertIs<AppError.Http>(outcome.errorOrNull())
        assertTrue("rate-limiting" in error.message, "a trailing DNS error hid the real reason")
    }

    @Test
    fun `the working mirror is reported once, and only when it changes`() = runTest {
        val remembered = mutableListOf<String>()
        val onWorking: suspend (String) -> Unit = { remembered += it }

        resolver.withMirror(mirrors, onWorkingMirror = onWorking) { Unit.asSuccess() }
        assertEquals(listOf("https://one.test"), remembered)

        // Already the stored choice: nothing new to write.
        resolver.withMirror(mirrors, preferred = "https://one.test", onWorkingMirror = onWorking) {
            Unit.asSuccess()
        }
        assertEquals(listOf("https://one.test"), remembered)
    }

    @Test
    fun `an empty mirror list is a configuration problem`() = runTest {
        val outcome: AppResult<Unit> = resolver.withMirror(emptyList()) { Unit.asSuccess() }

        assertIs<AppError.Unsupported>(outcome.errorOrNull())
    }

    private companion object {
        const val COOLDOWN = 5 * 60 * 1000L
    }
}
