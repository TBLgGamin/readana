package dev.readana.source.annas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response of `GET /dyn/api/fast_download.json?md5=…&key=…` (members only). */
@Serializable
internal data class FastDownloadResponse(
    @SerialName("download_url") val downloadUrl: String? = null,
    val error: String? = null,
    @SerialName("account_fast_download_info") val accountInfo: FastDownloadAccountInfo? = null
)

@Serializable
internal data class FastDownloadAccountInfo(
    @SerialName("downloads_left") val downloadsLeft: Int? = null,
    @SerialName("downloads_per_day") val downloadsPerDay: Int? = null,
    @SerialName("recently_downloaded_md5s") val recentlyDownloadedMd5s: List<String> = emptyList()
)
