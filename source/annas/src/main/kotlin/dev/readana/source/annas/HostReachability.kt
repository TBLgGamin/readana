package dev.readana.source.annas

import dev.readana.core.common.coroutines.suspendRunCatching
import io.ktor.http.Url
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Whether a download host can be reached from this connection at all.
 *
 * Anna's Archive hands out download links on rotating partner domains, and those are exactly what
 * an ISP blocks first: the name simply stops resolving. Since the site offers several servers for
 * the same file, a link on a blocked one is worth swapping rather than failing — but only if we
 * ask before handing it to the downloader, which would otherwise spend all its retries on a name
 * that is never going to resolve.
 *
 * Deliberately a name lookup and not a request: it costs nothing, and a signed download URL should
 * not be spent on a test.
 */
fun interface HostReachability {
    suspend fun canReach(url: String): Boolean

    companion object {
        /** Assumes yes; for tests and for callers that would rather find out the hard way. */
        val Always = HostReachability { true }

        val Dns = HostReachability { url ->
            val host = suspendRunCatching { Url(url).host }.getOrNull()
            if (host.isNullOrBlank()) {
                false
            } else {
                withContext(Dispatchers.IO) {
                    suspendRunCatching { InetAddress.getByName(host) }.isSuccess
                }
            }
        }
    }
}
