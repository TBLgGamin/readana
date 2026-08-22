package dev.readana.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Reading settings that belong to one book rather than to the app.
 *
 * A row exists only once the reader has adjusted something while that book was open; until then
 * the book reads with the global defaults, and deleting the row puts it back there.
 */
@Entity(tableName = "book_reader_settings")
data class BookSettingsEntity(
    @PrimaryKey @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "settings_json") val settingsJson: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
