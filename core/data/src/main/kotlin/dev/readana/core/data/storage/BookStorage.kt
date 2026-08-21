package dev.readana.core.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.readana.core.common.coroutines.Dispatcher
import dev.readana.core.common.coroutines.ReadanaDispatchers
import dev.readana.core.data.repository.SettingsRepository
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Where book files live on disk (ADR 0007): app-specific external storage by default, or the
 * SAF tree the user picked in Settings. Covers are always app-internal because they are derived
 * data that can be regenerated from the file.
 */
@Singleton
class BookStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    @Dispatcher(ReadanaDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) {
    /** A file that now lives in the library, with the size actually written. */
    data class StoredFile(val uri: String, val sizeBytes: Long)

    /**
     * Copies [sourceUri] into the library. Falls back to app storage when no SAF folder is
     * configured or the configured one has become unusable (revoked permission, ejected card).
     */
    suspend fun store(sourceUri: String, fileName: String, mimeType: String): StoredFile = withContext(ioDispatcher) {
        val source = sourceUri.toUri()
        val treeUri = settingsRepository.libraryFolderUri.first()
        treeUri?.let { storeInTree(it, source, fileName, mimeType) }
            ?: storeInAppFolder(source, fileName)
    }

    /** Deletes a stored book file or cover. Missing files are not an error. */
    suspend fun delete(fileUri: String): Unit = withContext(ioDispatcher) {
        val uri = fileUri.toUri()
        when (uri.scheme) {
            "file" -> uri.path?.let { File(it).delete() }
            else -> DocumentFile.fromSingleUri(context, uri)?.delete()
        }
        Unit
    }

    /** Writes a cover as PNG under `filesDir/covers`, returning its `file://` URI. */
    suspend fun writeCover(bookId: String, cover: Bitmap): String? = withContext(ioDispatcher) {
        val dir = File(context.filesDir, COVERS_DIR).apply { mkdirs() }
        val file = File(dir, "$bookId.png")
        val written = file.outputStream().use { out ->
            cover.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
        }
        if (written) Uri.fromFile(file).toString() else null
    }

    private fun storeInTree(treeUri: String, source: Uri, fileName: String, mimeType: String): StoredFile? {
        val tree = DocumentFile.fromTreeUri(context, treeUri.toUri())?.takeIf { it.canWrite() }
            ?: return null
        val target = tree.createFile(mimeType, fileName.substringBeforeLast('.')) ?: return null
        val size = copy(source) { context.contentResolver.openOutputStream(target.uri) }
        return StoredFile(target.uri.toString(), size)
    }

    private fun storeInAppFolder(source: Uri, fileName: String): StoredFile {
        val target = uniqueFile(booksDir(), fileName)
        val size = copy(source) { target.outputStream() }
        return StoredFile(Uri.fromFile(target).toString(), size)
    }

    private fun booksDir(): File = (context.getExternalFilesDir(BOOKS_DIR) ?: File(context.filesDir, BOOKS_DIR))
        .apply { mkdirs() }

    private fun copy(source: Uri, openTarget: () -> OutputStream?): Long {
        val input = requireNotNull(context.contentResolver.openInputStream(source)) {
            "Cannot read $source"
        }
        return input.use { from ->
            requireNotNull(openTarget()) { "Cannot write to the library folder" }
                .use { to -> from.copyTo(to) }
        }
    }

    private fun uniqueFile(dir: File, fileName: String): File {
        val base = fileName.substringBeforeLast('.')
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
        val suffix = if (extension.isEmpty()) "" else ".$extension"
        var candidate = File(dir, fileName)
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($counter)$suffix")
            counter++
        }
        return candidate
    }

    private companion object {
        const val BOOKS_DIR = "books"
        const val COVERS_DIR = "covers"
        const val PNG_QUALITY = 100
    }
}
