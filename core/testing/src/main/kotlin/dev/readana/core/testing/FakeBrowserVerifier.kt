package dev.readana.core.testing

import dev.readana.source.api.BrowserVerifier
import dev.readana.source.api.VerifyOutcome

/**
 * A [BrowserVerifier] that records what it was asked to verify and answers however the test wants.
 *
 * [onVerify] is the hook that makes a round trip believable: a test can point it at the mock HTTP
 * engine and have the site start serving real pages, exactly as clearing the check would.
 */
class FakeBrowserVerifier(
    var outcome: VerifyOutcome = VerifyOutcome.Verified,
    private val onVerify: suspend (String) -> Unit = {}
) : BrowserVerifier {
    val verified: MutableList<String> = mutableListOf()

    override suspend fun verify(url: String): VerifyOutcome {
        verified += url
        onVerify(url)
        return outcome
    }
}
