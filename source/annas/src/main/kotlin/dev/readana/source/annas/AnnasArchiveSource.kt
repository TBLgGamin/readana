package dev.readana.source.annas

import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.common.result.asSuccess
import dev.readana.core.model.BookFormat
import dev.readana.source.api.BookSource
import dev.readana.source.api.BrowserLogin
import dev.readana.source.api.BrowserVerifier
import dev.readana.source.api.DownloadQuota
import dev.readana.source.api.DownloadRequest
import dev.readana.source.api.FileNames
import dev.readana.source.api.NoBrowserVerifier
import dev.readana.source.api.SearchPage
import dev.readana.source.api.SearchQuery
import dev.readana.source.api.SearchResult
import dev.readana.source.api.SourceId
import dev.readana.source.api.VerifyOutcome
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Anna's Archive book source.
 *
 * - **Search** scrapes the HTML result page (there is no public search API).
 * - **Download** uses the members-only JSON fast-download API when a secret key is configured;
 *   otherwise it hands the record page to an in-app browser so the user can use the slow
 *   (wait-list / captcha) partner downloads themselves.
 *
 * Whether downloading a given work is lawful depends on the work and the jurisdiction; the app
 * surfaces this in Settings and leaves the choice with the user.
 */
class AnnasArchiveSource(
    private val client: HttpClient,
    private val config: suspend () -> AnnasConfig,
    private val mirrors: MirrorResolver = MirrorResolver(),
    /** Clears the site's browser check without troubling the user, when the device can. */
    private val verifier: BrowserVerifier = NoBrowserVerifier,
    /** Called with the mirror that answered, so the choice survives a restart. */
    private val onWorkingMirror: suspend (String) -> Unit = {}
) : BookSource {
    override val id: SourceId = ID
    override val displayName: String = "Anna's Archive"

    /** Opt-in: the user must enable it in Settings after reading the legal notice. */
    override val enabledByDefault: Boolean = false

    private val json = Json { ignoreUnknownKeys = true }
    private val session = AnnasSession(client)

    private fun String?.orNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    override suspend fun search(query: SearchQuery): AppResult<SearchPage> {
        val settings = config()
        return mirrors.withMirror(
            mirrors = settings.normalizedMirrors,
            preferred = settings.preferredMirror,
            onWorkingMirror = onWorkingMirror
        ) { base ->
            val html = when (
                val page = gatedGet(base, "/search", settings.secretKey.orNull()) {
                    parameter("q", query.text)
                    // The table layout puts every field in a fixed column, which survives the site's
                    // frequent restyling of the card layout.
                    parameter("display", "table")
                    // Repeatable: the site filters on the set, and we can only open these two anyway.
                    query.formats.forEach { parameter("ext", it.extension) }
                    query.language?.let { parameter("lang", it) }
                    if (query.page > 1) parameter("page", query.page)
                }
            ) {
                is AppResult.Failure -> return@withMirror page
                is AppResult.Success -> page.value
            }
            if (AnnasSearchParser.isRedirectInterstitial(html)) {
                throw MirrorUnavailableException("mirror only serves a redirect page")
            }
            if (!AnnasSearchParser.looksLikeAnnasArchive(html)) {
                throw MirrorUnavailableException(
                    "response does not look like Anna's Archive (parked domain or block page)"
                )
            }
            val parsed = AnnasSearchParser.parse(html, base, id)
            val results = parsed.results.filter { it.format == null || it.format in query.formats }
            SearchPage(
                results = results,
                page = query.page,
                // A page the site filled to the brim probably has another behind it. Counting
                // records rather than results keeps that true when formats we cannot open are cut.
                hasMore = parsed.recordCount >= AnnasSearchParser.RESULTS_PER_PAGE
            ).asSuccess()
        }
    }

    override suspend fun resolveDownload(result: SearchResult): AppResult<DownloadRequest> {
        val settings = config()
        val md5 = result.extras[AnnasSearchParser.EXTRA_MD5] ?: result.recordId
        val format = result.format ?: BookFormat.EPUB
        val fileName = FileNames.suggest(result.title, result.authors, format)
        val key = settings.secretKey?.trim()?.takeIf { it.isNotEmpty() }

        return mirrors.withMirror(
            mirrors = settings.normalizedMirrors,
            preferred = settings.preferredMirror,
            onWorkingMirror = onWorkingMirror
        ) { base ->
            if (key == null) {
                return@withMirror browserFallback(base, md5, format, fileName, key = null).asSuccess()
            }
            val response = client.get("$base/dyn/api/fast_download.json") {
                parameter("md5", md5)
                parameter("key", key)
            }
            response.failIfMirrorBroken()
            val payload = try {
                json.decodeFromString<FastDownloadResponse>(response.bodyAsText())
            } catch (e: SerializationException) {
                return@withMirror AppError.Parse("Unexpected fast-download response", e).asFailure()
            } catch (e: IllegalArgumentException) {
                return@withMirror AppError.Parse("Unexpected fast-download response", e).asFailure()
            }
            interpret(payload, response.status, base, md5, key, format, fileName, result.sizeBytes)
        }
    }

    /**
     * Fetches an Anna's Archive page, getting past the browser check on the way if the site asks
     * for one.
     *
     * A member is waved past it: signing in with the key sets a session cookie the site trusts, so
     * that is tried first and costs one extra request per mirror per session. Everyone else needs
     * the check answered by something that runs JavaScript, which is what [verifier] is for; the
     * cookie it earns is shared with this client, so the retry goes through. When there is nothing
     * to verify with, the caller gets the URL to hand to a browser the user can see.
     */
    private suspend fun gatedGet(
        base: String,
        path: String,
        key: String?,
        params: HttpRequestBuilder.() -> Unit = {}
    ): AppResult<String> {
        val url = "$base$path"
        var response = client.get(url, params)
        var body = response.bodyAsText()

        if (response.isChallenge(body)) {
            if (key != null && !session.isSignedIn(base) && session.signIn(base, key)) {
                response = client.get(url, params)
                body = response.bodyAsText()
            }
        }
        if (response.isChallenge(body)) {
            // A membership that has lapsed still signs in, so stop trusting the session and let
            // the next attempt try again from scratch.
            session.forget(base)
            when (verifier.verify(url)) {
                is VerifyOutcome.Verified -> {
                    response = client.get(url, params)
                    body = response.bodyAsText()
                }

                is VerifyOutcome.Failed, VerifyOutcome.Unavailable -> return browserCheck(url)
            }
        }
        if (response.isChallenge(body)) return browserCheck(url)

        response.failIfMirrorBroken()
        if (!response.status.isSuccess()) {
            return AppError.Http(
                response.status.value,
                blockedMessage(response.status) ?: "HTTP ${response.status.value}"
            ).asFailure()
        }
        return body.asSuccess()
    }

    private fun browserCheck(url: String): AppResult<String> = AppError.BrowserCheck(
        "Anna's Archive wants a browser check before it will answer",
        url
    ).asFailure()

    private fun HttpResponse.isChallenge(body: String): Boolean = DdosGuard.isChallenge(
        status = status.value,
        header = { headers[it] },
        // Redirects are followed, so this is where the answer actually came from.
        url = call.request.url.toString(),
        body = body
    )

    /**
     * The record page, for the in-app browser to run a slow partner download. When a key is
     * configured the browser can sign in with it first: the key is the account.
     */
    private fun browserFallback(
        base: String,
        md5: String,
        format: BookFormat,
        fileName: String,
        key: String?,
        quota: DownloadQuota? = null
    ) = DownloadRequest(
        url = "$base/md5/$md5",
        format = format,
        suggestedFileName = fileName,
        requiresBrowser = true,
        quota = quota,
        browserLogin = key?.let { BrowserLogin("$base/account/", "key=$it") }
    )

    @Suppress("LongParameterList")
    private fun interpret(
        payload: FastDownloadResponse,
        status: HttpStatusCode,
        base: String,
        md5: String,
        key: String,
        format: BookFormat,
        fileName: String,
        expectedSize: Long?
    ): AppResult<DownloadRequest> {
        val quota = payload.accountInfo?.let {
            DownloadQuota(remaining = it.downloadsLeft, perDay = it.downloadsPerDay)
        }
        payload.downloadUrl?.let { url ->
            return DownloadRequest(
                url,
                format,
                fileName,
                expectedSizeBytes = expectedSize,
                quota = quota
            ).asSuccess()
        }
        refusal(status, payload)?.let { return it.asFailure() }

        // The membership is fine, today's fast downloads are simply spent: offer the browser.
        if (payload.isQuotaSpent(status, quota)) {
            return browserFallback(
                base,
                md5,
                format,
                fileName,
                key,
                quota ?: DownloadQuota(remaining = 0, perDay = null)
            ).asSuccess()
        }
        return when {
            !status.isSuccess() -> AppError.Http(status.value, payload.error ?: "HTTP ${status.value}")
            else -> AppError.Unexpected(payload.error ?: "Fast download unavailable")
        }.asFailure()
    }

    /**
     * The refusals the fast-download API documents, mapped to what the user can do about each.
     * None of them change with the mirror or with a retry.
     */
    private fun refusal(status: HttpStatusCode, payload: FastDownloadResponse): AppError? = when {
        // "Invalid md5": we sent something that is not a record id, so this one is our bug.
        status == HttpStatusCode.BadRequest -> AppError.Parse(
            "Anna's Archive did not recognise this record (${payload.error ?: "invalid md5"})"
        )

        status == HttpStatusCode.Unauthorized ||
            payload.error?.contains("key", ignoreCase = true) == true ->
            AppError.Unauthorized("Anna's Archive rejected the membership key. Check it in Settings.")

        status == HttpStatusCode.Forbidden -> AppError.Unauthorized(
            "This key has no active membership. Renew it, or clear the key in Settings to " +
                "download at the free speed."
        )

        status == HttpStatusCode.NotFound -> AppError.NotFound("Anna's Archive no longer has this record")

        else -> null
    }

    private fun FastDownloadResponse.isQuotaSpent(status: HttpStatusCode, quota: DownloadQuota?): Boolean =
        status == HttpStatusCode.TooManyRequests ||
            quota?.isExhausted == true ||
            error?.looksLikeQuota() == true

    private fun String.looksLikeQuota(): Boolean =
        contains("limit", ignoreCase = true) || contains("quota", ignoreCase = true) ||
            contains("no downloads left", ignoreCase = true)

    /**
     * Anna's Archive throttles scraping by IP and answers with a bare 403 or 429. Saying so beats
     * "HTTP 403", because waiting or switching mirror is what actually helps.
     */
    private fun blockedMessage(status: HttpStatusCode): String? = when (status.value) {
        HttpStatusCode.Forbidden.value, HttpStatusCode.TooManyRequests.value ->
            "Anna's Archive is rate-limiting this connection. Wait a few minutes, " +
                "or add another mirror in Settings."

        else -> null
    }

    private fun HttpResponse.failIfMirrorBroken() {
        if (status.value >= HttpStatusCode.InternalServerError.value) {
            throw MirrorUnavailableException("HTTP ${status.value}")
        }
    }

    companion object {
        val ID = SourceId("annas-archive")
    }
}
