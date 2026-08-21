package dev.readana.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Transform
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
}
