package dev.readana.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.model.BookCollection

private val ListMaxHeight = 280.dp

/**
 * Which shelves a book is on, and a way to start a new one.
 *
 * Managed from the book rather than from a screen of its own: putting a book on a shelf is
 * something you decide while looking at the book, and a library with one management screen per
 * concept is a library nobody tidies.
 */
@Composable
internal fun CollectionsDialog(
    collections: List<BookCollection>,
    memberOf: Set<String>,
    onSetMembership: (collectionId: String, member: Boolean) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newName by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.feature_library_collections)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (collections.isEmpty()) {
                    Text(stringResource(R.string.feature_library_collections_none))
                } else {
                    LazyColumn(Modifier.heightIn(max = ListMaxHeight)) {
                        items(collections, key = { it.id }) { collection ->
                            val member = collection.id in memberOf
                            ListItem(
                                headlineContent = { Text(collection.name) },
                                modifier = Modifier.fillMaxWidth(),
                                leadingContent = {
                                    Checkbox(
                                        checked = member,
                                        onCheckedChange = { onSetMembership(collection.id, it) }
                                    )
                                }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.feature_library_collections_new)) },
                        singleLine = true
                    )
                    IconButton(
                        onClick = {
                            onCreate(newName)
                            newName = ""
                        },
                        enabled = newName.isNotBlank()
                    ) {
                        Icon(
                            ReadanaIcons.Add,
                            contentDescription = stringResource(R.string.feature_library_collections_create)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.feature_library_collections_done))
            }
        }
    )
}
