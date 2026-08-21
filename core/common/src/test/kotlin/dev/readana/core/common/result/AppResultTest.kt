package dev.readana.core.common.result

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppResultTest {
    @Test
    fun `map transforms success and keeps failure`() {
        val success: AppResult<Int> = 2.asSuccess()
        val failure: AppResult<Int> = AppError.NotFound("nope").asFailure()

        assertEquals(AppResult.Success(4), success.map { it * 2 })
        assertEquals(failure, failure.map { it * 2 })
    }

    @Test
    fun `getOrElse falls back on failure`() {
        val failure: AppResult<String> = AppError.Http(statusCode = 503).asFailure()

        assertEquals("fallback", failure.getOrElse { "fallback" })
        assertNull(failure.getOrNull())
        assertEquals(503, (failure.errorOrNull() as AppError.Http).statusCode)
    }
}
