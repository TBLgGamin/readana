package dev.readana.core.common.result

/** Typed failure reasons shared across data sources, engines and UI. */
sealed interface AppError {
    val message: String

    /** Connectivity problems: DNS, timeouts, TLS, unreachable mirrors. */
    data class Network(override val message: String, val cause: Throwable? = null) : AppError

    /** The server answered, but with a non-success status code. */
    data class Http(val statusCode: Int, override val message: String = "HTTP $statusCode") : AppError

    /** The payload could not be understood (malformed JSON, unexpected HTML layout, corrupt file). */
    data class Parse(override val message: String, val cause: Throwable? = null) : AppError

    /** The operation needs credentials or a membership the user has not configured. */
    data class Unauthorized(override val message: String) : AppError

    /**
     * The site insists on a "checking your browser" gate that the app could not clear on its own,
     * so someone has to open [url] in a browser. Carrying the URL is what lets the UI offer that.
     */
    data class BrowserCheck(override val message: String, val url: String) : AppError

    data class NotFound(override val message: String) : AppError

    /** Valid input that this app deliberately does not handle (e.g. an unsupported file format). */
    data class Unsupported(override val message: String) : AppError

    data class Unexpected(override val message: String, val cause: Throwable? = null) : AppError
}
