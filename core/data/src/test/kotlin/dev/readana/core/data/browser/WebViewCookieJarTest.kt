package dev.readana.core.data.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewCookieJarTest {
    private val jar = WebViewCookieJar()
    private val mirror = "https://annas-archive.gl/search?q=dune".toHttpUrl()

    @Test
    fun `a cookie the site sets comes back on the next request`() {
        val cookie = checkNotNull(
            Cookie.parse(mirror, "aa_ddg_check=signed.jwt.value; Path=/; HttpOnly")
        )

        jar.saveFromResponse(mirror, listOf(cookie))
        val sent = jar.loadForRequest("https://annas-archive.gl/md5/abc".toHttpUrl())

        // HttpOnly is what the bot check hands out, so it has to survive the round trip.
        assertEquals(listOf("aa_ddg_check"), sent.map { it.name })
        assertEquals("signed.jwt.value", sent.single().value)
    }

    @Test
    fun `cookies do not leak to another mirror`() {
        val cookie = checkNotNull(Cookie.parse(mirror, "aa_account_id2=member.jwt; Path=/"))

        jar.saveFromResponse(mirror, listOf(cookie))

        assertTrue(jar.loadForRequest("https://annas-archive.pk/search".toHttpUrl()).isEmpty())
    }
}
