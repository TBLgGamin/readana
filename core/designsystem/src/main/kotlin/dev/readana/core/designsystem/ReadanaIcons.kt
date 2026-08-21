package dev.readana.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Single place to pick icons so screens stay consistent and swaps are one-line changes. */
object ReadanaIcons {
    val Library: ImageVector = Icons.AutoMirrored.Filled.MenuBook
    val Discover: ImageVector = Icons.Filled.Search
    val Settings: ImageVector = Icons.Filled.Settings
    val Back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val Download: ImageVector = Icons.Filled.Download
    val OpenInBrowser: ImageVector = Icons.AutoMirrored.Filled.OpenInNew
}
