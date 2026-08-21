package dev.readana.source.annas

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Parameters
import io.ktor.http.setCookie
import java.util.concurrent.ConcurrentHashMap

/**
 * Signs in to Anna's Archive with a membership key.
 *
 * The site has no username or password: posting the key to the account page is the whole login,
 * and it answers with a session cookie. That cookie is what makes a membership worth having for
 * search as well as downloads — a signed-in member is waved past the browser check entirely.
 *
 * Cookies belong to one host, so this tracks which mirrors have been signed in to. The tracking is
 * an optimisation, not a source of truth: [forget] puts a mirror back in the queue whenever the
 * site asks for a check despite it.
 */
internal class AnnasSession(private val client: HttpClient) {
    private val signedIn = ConcurrentHashMap.newKeySet<String>()

    fun isSignedIn(base: String): Boolean = base in signedIn

    fun forget(base: String) {
        signedIn.remove(base)
    }

    /** True when the site handed out a session cookie, which is the only sign the key was good. */
    suspend fun signIn(base: String, key: String): Boolean {
        val response: HttpResponse = client.submitForm(
            url = "$base/account/",
            formParameters = Parameters.build { append("key", key) }
        )
        // The redirect that carries the cookie is not followed for a POST, so it arrives here.
        val accepted = response.setCookie().any { it.name == ACCOUNT_COOKIE && it.value.isNotBlank() }
        if (accepted) signedIn += base
        return accepted
    }

    private companion object {
        const val ACCOUNT_COOKIE = "aa_account_id2"
    }
}
