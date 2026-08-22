package dev.readana.feature.downloads

/** Everything a row can be asked to do, gathered so the card takes one parameter rather than six. */
internal data class DownloadActions(
    val onPause: (String) -> Unit,
    val onResume: (String) -> Unit,
    val onCancel: (String) -> Unit,
    val onRetry: (String) -> Unit,
    val onDismiss: (String) -> Unit,
    val onOpenBook: (String) -> Unit
)
