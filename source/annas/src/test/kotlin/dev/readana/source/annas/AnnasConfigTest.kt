package dev.readana.source.annas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnnasConfigTest {
    @Test
    fun `a bare host is usable without typing a scheme`() {
        val config = AnnasConfig(mirrors = listOf("annas-archive.gl"))

        assertEquals(listOf("https://annas-archive.gl"), config.normalizedMirrors)
    }

    @Test
    fun `blank lines and trailing slashes are tidied away`() {
        val config = AnnasConfig(
            mirrors = listOf("  https://annas-archive.gl/  ", "", "   ", "annas-archive.pk/")
        )

        assertEquals(
            listOf("https://annas-archive.gl", "https://annas-archive.pk"),
            config.normalizedMirrors
        )
    }

    @Test
    fun `an entry that cannot be a mirror is dropped`() {
        val config = AnnasConfig(mirrors = listOf("ftp://annas-archive.gl", "notahost", "https://annas-archive.gl"))

        assertEquals(listOf("https://annas-archive.gl"), config.normalizedMirrors)
    }

    @Test
    fun `nothing usable falls back to the built-in list rather than reporting none configured`() {
        val config = AnnasConfig(mirrors = listOf("   ", "notahost"))

        assertEquals(AnnasConfig.DEFAULT_MIRRORS, config.normalizedMirrors)
    }

    @Test
    fun `the built-in list leads with a mirror that serves the site`() {
        // .org and .se stopped resolving; searching should not start with two dead lookups.
        assertEquals("https://annas-archive.gl", AnnasConfig.DEFAULT_MIRRORS.first())
        assertTrue(AnnasConfig.DEFAULT_MIRRORS.all { it.startsWith("https://") })
    }

    @Test
    fun `a configured key is what turns on the member api`() {
        assertTrue(AnnasConfig(secretKey = "s3cr3t").hasSecretKey)
        assertTrue(!AnnasConfig(secretKey = "   ").hasSecretKey)
        assertTrue(!AnnasConfig().hasSecretKey)
    }
}
