package dev.readana.core.common.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VolumeKeyRelayTest {
    private val relay = VolumeKeyRelay()

    @Test
    fun `nobody listening means the system keeps the volume keys`() {
        assertFalse(relay.dispatch(VolumeKey.Up))
    }

    @Test
    fun `a registered handler receives the presses it claims`() {
        val received = mutableListOf<VolumeKey>()
        relay.setHandler { key ->
            received += key
            true
        }

        assertTrue(relay.dispatch(VolumeKey.Down))
        assertTrue(relay.dispatch(VolumeKey.Up))
        assertEquals(listOf(VolumeKey.Down, VolumeKey.Up), received)
    }

    @Test
    fun `a handler that declines leaves the press to the system`() {
        relay.setHandler { false }

        assertFalse(relay.dispatch(VolumeKey.Down))
    }

    @Test
    fun `clearing the handler hands the keys back`() {
        relay.setHandler { true }
        relay.setHandler(null)

        assertFalse(relay.dispatch(VolumeKey.Down))
    }
}
