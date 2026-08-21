package dev.readana.feature.reader

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * An answer, with the pictures it asked for shown where it asked for them.
 *
 * The model writes markers — `[figure:1]` for a plate it was given from the book, `[image: HMS
 * Victory]` for something it would like the reader to see — and they are swapped for the pictures
 * here. A marker with no picture behind it (the lookup found nothing, or the setting is off) is
 * simply dropped rather than left as stray brackets in the prose.
 */
@Composable
internal fun AssistantAnswer(text: String, pictures: Map<String, AssistantPicture>, modifier: Modifier = Modifier) {
    val parts = remember(text) { text.splitOnMarkers() }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        parts.forEach { part ->
            when (part) {
                is AnswerPart.Prose -> SelectionContainer {
                    Text(text = part.text, style = MaterialTheme.typography.bodyLarge)
                }

                is AnswerPart.Marker -> pictures[part.key]?.let { PictureBlock(it) }
            }
        }
    }
}

@Composable
private fun PictureBlock(picture: AssistantPicture, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val bitmap = picture.bytes?.let { bytes -> remember(bytes) { bytes.toImageBitmap() } }
        when {
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = picture.caption,
                modifier = Modifier.fillMaxWidth().heightIn(max = MaxPictureHeight),
                contentScale = ContentScale.Fit
            )

            picture.url != null -> AsyncImage(
                model = picture.url,
                contentDescription = picture.caption,
                modifier = Modifier.fillMaxWidth().heightIn(max = MaxPictureHeight),
                contentScale = ContentScale.Fit
            )

            else -> return
        }
        val credit = picture.sourceUrl?.let {
            stringResource(R.string.feature_reader_ai_picture_credit, picture.caption.orEmpty())
        } ?: picture.caption
        credit?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun ByteArray.toImageBitmap(): ImageBitmap? = BitmapFactory.decodeByteArray(this, 0, size)?.asImageBitmap()

/** A piece of an answer: prose, or a place where a picture belongs. */
private sealed interface AnswerPart {
    data class Prose(val text: String) : AnswerPart

    data class Marker(val key: String) : AnswerPart
}

/** Splits an answer around its picture markers, keeping the prose either side intact. */
private fun String.splitOnMarkers(): List<AnswerPart> {
    val parts = mutableListOf<AnswerPart>()
    var cursor = 0
    PICTURE_MARKER.findAll(this).forEach { match ->
        addProse(parts, substring(cursor, match.range.first))
        parts += AnswerPart.Marker(match.markerKey())
        cursor = match.range.last + 1
    }
    addProse(parts, substring(cursor))
    return parts
}

private fun addProse(parts: MutableList<AnswerPart>, raw: String) {
    val text = raw.trim()
    if (text.isNotEmpty()) parts += AnswerPart.Prose(text)
}

/** `[figure:1]` keeps its number; `[image: HMS Victory]` is keyed by the subject, lower-cased. */
private fun MatchResult.markerKey(): String {
    val figure = groupValues[1].trim()
    if (figure.isNotEmpty()) return "figure:" + figure
    return "image:" + groupValues[2].trim().lowercase()
}

private val PICTURE_MARKER = Regex("""\[(?:figure:\s*(\d{1,2})|image:\s*([^\]]{1,120}))]""")

private val MaxPictureHeight = 320.dp
