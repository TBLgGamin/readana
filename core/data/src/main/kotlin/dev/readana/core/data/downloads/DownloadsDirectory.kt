package dev.readana.core.data.downloads

import javax.inject.Qualifier

/** Directory holding partially downloaded files. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DownloadsDirectory
