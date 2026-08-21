package dev.readana.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.readana.core.data.conversion.decodeConversionReport
import dev.readana.core.data.conversion.encodeToJson
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import dev.readana.core.model.BookOrigin
import dev.readana.core.model.ConversionInfo
import dev.readana.core.model.ReadingProgress
import java.time.Instant

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** Pipe-separated list; authors never contain '|'. */
    val authors: String,
    val language: String?,
    val format: String,
    @ColumnInfo(name = "file_uri") val fileUri: String,
    @ColumnInfo(name = "cover_uri") val coverUri: String?,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long?,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long?,
    @ColumnInfo(name = "progress_locator") val progressLocator: String?,
    @ColumnInfo(name = "progress_fraction") val progressFraction: Float?,
    @ColumnInfo(name = "source_id") val sourceId: String?,
    @ColumnInfo(name = "source_record_id") val sourceRecordId: String?,
    @ColumnInfo(name = "original_format") val originalFormat: String?,
    /** Where the PDF this EPUB was converted from still lives; null when nothing was converted. */
    @ColumnInfo(name = "original_file_uri") val originalFileUri: String? = null,
    @ColumnInfo(name = "converted_at") val convertedAt: Long? = null,
    /** JSON of the conversion report, for the details sheet. */
    @ColumnInfo(name = "conversion_report") val conversionReport: String? = null
)

private const val AUTHOR_SEPARATOR = "|"

fun BookEntity.toModel(): Book = Book(
    id = id,
    title = title,
    authors = authors.split(AUTHOR_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() },
    language = language,
    format = BookFormat.valueOf(format),
    fileUri = fileUri,
    coverUri = coverUri,
    sizeBytes = sizeBytes,
    addedAt = Instant.ofEpochMilli(addedAt),
    lastOpenedAt = lastOpenedAt?.let(Instant::ofEpochMilli),
    progress = progressLocator?.let { locator ->
        ReadingProgress(locator, (progressFraction ?: 0f).coerceIn(0f, 1f))
    },
    origin = sourceId?.let { source ->
        BookOrigin(
            sourceId = source,
            recordId = sourceRecordId.orEmpty(),
            originalFormat =
                originalFormat?.let(BookFormat::valueOf) ?: BookFormat.valueOf(format)
        )
    },
    conversion = originalFileUri?.let { original ->
        ConversionInfo(
            originalFileUri = original,
            convertedAt = Instant.ofEpochMilli(convertedAt ?: 0),
            report = conversionReport.decodeConversionReport()
        )
    }
)

fun Book.toEntity(): BookEntity = BookEntity(
    id = id,
    title = title,
    authors = authors.joinToString(AUTHOR_SEPARATOR) { it.replace(AUTHOR_SEPARATOR, "/") },
    language = language,
    format = format.name,
    fileUri = fileUri,
    coverUri = coverUri,
    sizeBytes = sizeBytes,
    addedAt = addedAt.toEpochMilli(),
    lastOpenedAt = lastOpenedAt?.toEpochMilli(),
    progressLocator = progress?.locatorJson,
    progressFraction = progress?.fraction,
    sourceId = origin?.sourceId,
    sourceRecordId = origin?.recordId,
    originalFormat = origin?.originalFormat?.name,
    originalFileUri = conversion?.originalFileUri,
    convertedAt = conversion?.convertedAt?.toEpochMilli(),
    conversionReport = conversion?.report?.encodeToJson()
)
