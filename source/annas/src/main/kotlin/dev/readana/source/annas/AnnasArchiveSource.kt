package dev.readana.source.annas

import dev.readana.core.common.result.AppError
import dev.readana.core.common.result.AppResult
import dev.readana.core.common.result.asFailure
import dev.readana.core.common.result.asSuccess
import dev.readana.core.common.result.getOrNull
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
import dev.readana.source.api.SourceConfigField
import dev.readana.source.api.SourceId
import dev.readana.source.api.VerifyOutcome
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
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
    /** Weeds out download links on domains this connection cannot resolve. */
    private val reachability: HostReachability = HostReachability.Dns,
    /** Called with the mirror that answered, so the choice survives a restart. */
    private val onWorkingMirror: suspend (String) -> Unit = {}
) : BookSource {
    override val id: SourceId = ID
    override val displayName: String = "Anna's Archive"

    /** Opt-in: the user must enable it in Settings after reading the legal notice. */
    override val enabledByDefault: Boolean = false

    override val summary: String = "Shadow library. Needs a membership key for fast downloads."

    override val configFields: List<SourceConfigField> = AnnasConfig.CONFIG_FIELDS

    private val json = Json { ignoreUnknownKeys = true }
    private val session = AnnasSession(client)
    private val libgen = LibgenDirectLinks(client)

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

    /**
     * Finds a way to fetch the file, best first.
     *
     * 1. A member's fast download, when a key is configured. Straight to the file, no wait.
     * 2. Library Genesis, which holds most of what the site indexes and serves it by md5 with no
     *    browser check at all — this is what makes a free download start immediately.
     * 3. The site's own free partner servers, which sometimes hold the file behind a short wait.
     * 4. The record page in a browser the user can see, for anything the other three could not do.
     *
     * A membership whose daily allowance is spent falls through to the free rungs rather than
     * straight to the browser: slower is still better than manual.
     */
    override suspend fun resolveDownload(result: SearchResult): AppResult<DownloadRequest> {
        val settings = config()
        val candidate = Candidate(
            md5 = result.extras[AnnasSearchParser.EXTRA_MD5] ?: result.recordId,
            format = result.format ?: BookFormat.EPUB,
            expectedSize = result.sizeBytes,
            collections = result.extras[AnnasSearchParser.EXTRA_COLLECTIONS],
            fileName = FileNames.suggest(
                result.title,
                result.authors,
                result.format ?: BookFormat.EPUB
            )
        )
        val key = settings.secretKey.orNull()
        var quota: DownloadQuota? = null

        if (key != null) {
            val outcome = onSomeMirror(settings) { base -> fastDownload(base, candidate, key) }
            when (val rung = outcome.getOrNull()) {
                is Rung.Ready -> return rung.request.asSuccess()

                is Rung.Stop -> return rung.error.asFailure()

                is Rung.Continue -> quota = rung.quota

                // Every mirror failed to answer at all; the free rungs may still work.
                null -> Unit
            }
        }

        if (candidate.mightBeOnLibgen) {
            libgen.resolve(candidate.md5)?.let { link ->
                return candidate.request(
                    url = link.url,
                    quota = quota,
                    headers = mapOf(HttpHeaders.Referrer to link.referer)
                ).asSuccess()
            }
        }

        val slow = onSomeMirror(settings) { base -> slowDownload(base, candidate, key, quota) }
        if (slow is AppResult.Success) return slow

        val base = mirrors.preferredMirror ?: settings.preferredMirror ?: settings.normalizedMirrors.first()
        return browserFallback(base, candidate, key, quota).asSuccess()
    }

    private suspend fun <T> onSomeMirror(settings: AnnasConfig, block: suspend (String) -> AppResult<T>): AppResult<T> =
        mirrors.withMirror(
            mirrors = settings.normalizedMirrors,
            preferred = settings.preferredMirror,
            onWorkingMirror = onWorkingMirror,
            block = block
        )

    /** What one rung of the ladder concluded. */
    private sealed interface Rung {
        data class Ready(val request: DownloadRequest) : Rung

        /** No point trying anything else: the answer would be the same. */
        data class Stop(val error: AppError) : Rung

        /** This rung cannot serve it; the quota is carried on so the UI can still report it. */
        data class Continue(val quota: DownloadQuota?) : Rung
    }

    /**
     * Asks for the member's download link, and asks again on another server if the first one is on
     * a domain this connection cannot resolve — ISPs block those before anything else. Asking
     * repeatedly for the same file is free: the site charges the allowance once per record, and
     * counts the rest as the same download.
     */
    private suspend fun fastDownload(base: String, candidate: Candidate, key: String): AppResult<Rung> {
        var unreachable: Rung.Ready? = null
        for (server in AnnasConfig.FAST_DOWNLOAD_SERVERS) {
            val response = client.get("$base/dyn/api/fast_download.json") {
                parameter("md5", candidate.md5)
                parameter("key", key)
                if (server > 0) parameter("domain_index", server)
            }
            response.failIfMirrorBroken()
            val payload = try {
                json.decodeFromString<FastDownloadResponse>(response.bodyAsText())
            } catch (e: SerializationException) {
                return AppError.Parse("Unexpected fast-download response", e).asFailure()
            } catch (e: IllegalArgumentException) {
                return AppError.Parse("Unexpected fast-download response", e).asFailure()
            }

            val rung = interpret(payload, response.status, candidate)
            // A rejected key or a spent allowance says the same thing whichever server answers.
            if (rung !is Rung.Ready) return rung.asSuccess()
            if (reachability.canReach(rung.request.url)) return rung.asSuccess()
            unreachable = rung
        }
        // Every server is behind the block; the free routes may still be reachable.
        return Rung.Continue(unreachable?.request?.quota).asSuccess()
    }

    /**
     * Asks the free partner servers, no-wait ones first. A short wait is worth sitting through
     * because the page then serves the link itself; a long one is not, so the next server gets a
     * turn and the browser is left as the last resort.
     */
    private suspend fun slowDownload(
        base: String,
        candidate: Candidate,
        key: String?,
        quota: DownloadQuota?
    ): AppResult<DownloadRequest> {
        var budget = AnnasConfig.MAX_WAIT_SECONDS
        var lastError: AppError? = null

        val link = AnnasConfig.SLOW_DOWNLOAD_SERVERS.firstNotNullOfOrNull { server ->
            val attempt = partnerLink(base, candidate, key, server, budget)
            lastError = attempt.error ?: lastError
            budget -= attempt.waited
            attempt.url
        }
        return link?.let { candidate.request(it, quota).asSuccess() }
            ?: (lastError ?: AppError.Unexpected("No free download server had this file")).asFailure()
    }

    /** What one partner server had to say. [waited] comes off the shared patience budget. */
    private data class Attempt(val url: String?, val waited: Int = 0, val error: AppError? = null)

    private suspend fun partnerLink(
        base: String,
        candidate: Candidate,
        key: String?,
        server: Int,
        budget: Int
    ): Attempt {
        val path = "/slow_download/${candidate.md5}/0/$server"
        val page = gatedGet(base, path, key)
        if (page is AppResult.Failure) return Attempt(url = null, error = page.error)

        val url = "$base$path"
        return when (val outcome = SlowDownloadParser.parse(page.getOrNull().orEmpty(), url, candidate.md5)) {
            is SlowDownloadParser.Outcome.Link -> Attempt(outcome.url)

            is SlowDownloadParser.Outcome.Wait -> when {
                outcome.seconds > budget -> Attempt(url = null)

                else -> {
                    // The page counts down and then reloads itself; this does the same.
                    delay((outcome.seconds + 1).seconds)
                    val reloaded = gatedGet(base, path, key).getOrNull().orEmpty()
                    val settled = SlowDownloadParser.parse(reloaded, url, candidate.md5)
                    Attempt(
                        url = (settled as? SlowDownloadParser.Outcome.Link)?.url,
                        waited = outcome.seconds
                    )
                }
            }

            SlowDownloadParser.Outcome.Unrecognised -> Attempt(url = null)
        }
    }

    private data class Candidate(
        val md5: String,
        val format: BookFormat,
        val fileName: String,
        val expectedSize: Long?,
        val collections: String?
    ) {
        /** Unknown means "worth asking"; a record the site says is only elsewhere is not. */
        val mightBeOnLibgen: Boolean
            get() = collections == null || LIBGEN_COLLECTIONS.any { collections.contains(it) }

        fun request(url: String, quota: DownloadQuota?, headers: Map<String, String> = emptyMap()) = DownloadRequest(
            url = url,
            format = format,
            suggestedFileName = fileName,
            headers = headers,
            expectedSizeBytes = expectedSize,
            quota = quota
        )

        private companion object {
            val LIBGEN_COLLECTIONS = listOf("lgli", "lgrs", "libgen")
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
    private fun browserFallback(base: String, candidate: Candidate, key: String?, quota: DownloadQuota? = null) =
        DownloadRequest(
            url = "$base/md5/${candidate.md5}",
            format = candidate.format,
            suggestedFileName = candidate.fileName,
            requiresBrowser = true,
            quota = quota,
            browserLogin = key?.let { BrowserLogin("$base/account/", "key=$it") }
        )

    private fun interpret(payload: FastDownloadResponse, status: HttpStatusCode, candidate: Candidate): Rung {
        val quota = payload.accountInfo?.let {
            DownloadQuota(remaining = it.downloadsLeft, perDay = it.downloadsPerDay)
        }
        payload.downloadUrl?.let { url -> return Rung.Ready(candidate.request(url, quota)) }
        refusal(status, payload)?.let { return Rung.Stop(it) }

        // The membership is fine, today's fast downloads are simply spent: take the free route.
        if (payload.isQuotaSpent(status, quota)) {
            return Rung.Continue(quota ?: DownloadQuota(remaining = 0, perDay = null))
        }
        return Rung.Stop(
            when {
                !status.isSuccess() -> AppError.Http(status.value, payload.error ?: "HTTP ${status.value}")
                else -> AppError.Unexpected(payload.error ?: "Fast download unavailable")
            }
        )
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
