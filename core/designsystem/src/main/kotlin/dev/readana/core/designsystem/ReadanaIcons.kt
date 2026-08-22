package dev.readana.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Translate
import androidx.compose.ui.graphics.vector.ImageVector

/** Single place to pick icons so screens stay consistent and swaps are one-line changes. */
object ReadanaIcons {
    val Library: ImageVector = Icons.AutoMirrored.Filled.MenuBook
    val Discover: ImageVector = Icons.Filled.Search
    val Settings: ImageVector = Icons.Filled.Settings
    val Back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val Download: ImageVector = Icons.Filled.Download
    val OpenInBrowser: ImageVector = Icons.AutoMirrored.Filled.OpenInNew
    val Add: ImageVector = Icons.Filled.Add
    val Sort: ImageVector = Icons.AutoMirrored.Filled.Sort
    val Delete: ImageVector = Icons.Filled.Delete
    val Close: ImageVector = Icons.Filled.Close
    val Folder: ImageVector = Icons.Filled.Folder
    val BookmarkOn: ImageVector = Icons.Filled.Bookmark
    val BookmarkOff: ImageVector = Icons.Filled.BookmarkBorder
    val Refresh: ImageVector = Icons.Filled.Refresh
    val ExpandMore: ImageVector = Icons.Filled.ExpandMore
    val ExpandLess: ImageVector = Icons.Filled.ExpandLess

    /** PDF → EPUB conversion. */
    val Convert: ImageVector = Icons.Filled.Transform

    /** The reading assistant. */
    val Assistant: ImageVector = Icons.Filled.AutoAwesome

    val Send: ImageVector = Icons.AutoMirrored.Filled.Send

    /** Saved conversations with the assistant. */
    val Conversations: ImageVector = Icons.AutoMirrored.Filled.Chat

    /** A marked passage, with or without a note written against it. */
    val Note: ImageVector = Icons.Filled.FormatQuote

    val Edit: ImageVector = Icons.Filled.Edit
    val Share: ImageVector = Icons.Filled.Share
    val Filter: ImageVector = Icons.Filled.FilterList
    val Done: ImageVector = Icons.Filled.Done

    /** Actions that will not fit on the bar, behind one button. */
    val More: ImageVector = Icons.Filled.MoreVert

    /** Something finished, and something that stopped short of it. */
    val Succeeded: ImageVector = Icons.Filled.CheckCircle
    val Failed: ImageVector = Icons.Filled.ErrorOutline

    /** Read-aloud, and its transport. */
    val Listen: ImageVector = Icons.Filled.Headphones
    val Play: ImageVector = Icons.Filled.PlayArrow
    val Pause: ImageVector = Icons.Filled.Pause
    val Next: ImageVector = Icons.Filled.SkipNext
    val Previous: ImageVector = Icons.Filled.SkipPrevious
    val Speed: ImageVector = Icons.Filled.Speed

    /** Looking a word up in the dictionary. */
    val Define: ImageVector = Icons.Filled.Translate

    /** A shelf the reader made, and a series the book came in. */
    val Collection: ImageVector = Icons.Filled.Bookmarks
    val Series: ImageVector = Icons.Filled.Layers
}
