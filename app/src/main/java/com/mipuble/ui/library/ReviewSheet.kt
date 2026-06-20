package com.mipuble.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mipuble.domain.model.Book

/**
 * Post-import confirmation for books whose catalog match was uncertain. Each
 * row shows the imported title, the closest official names as tappable chips,
 * an editable field, and an "add to catalog" toggle so a confirmed name is
 * learned for next time. Resolving or skipping removes the row; the sheet
 * closes once the queue is empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSheet(
    books: List<Book>,
    onResolve: (bookId: Long, name: String, addToCatalog: Boolean) -> Unit,
    onSkip: (bookId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                "Confirm book names",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "These didn't clearly match an official series. Pick a suggestion, " +
                    "edit the name, or skip to keep it as-is.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            LazyColumn {
                items(books, key = { it.id }) { book ->
                    ReviewRow(book = book, onResolve = onResolve, onSkip = onSkip)
                    HorizontalDivider()
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewRow(
    book: Book,
    onResolve: (bookId: Long, name: String, addToCatalog: Boolean) -> Unit,
    onSkip: (bookId: Long) -> Unit,
) {
    val firstSuggestion = book.reviewSuggestions.firstOrNull() ?: book.title
    var name by rememberSaveable(book.id) { mutableStateOf(firstSuggestion) }
    var addToCatalog by rememberSaveable(book.id) { mutableStateOf(false) }

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            "Imported as “${book.title}”",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        if (book.reviewSuggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                book.reviewSuggestions.forEach { suggestion ->
                    FilterChip(
                        selected = name == suggestion,
                        onClick = { name = suggestion },
                        label = { Text(suggestion, maxLines = 1) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Official series name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = addToCatalog, onCheckedChange = { addToCatalog = it })
            Text("Add to catalog for next time", style = MaterialTheme.typography.bodyMedium)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { onSkip(book.id) }) { Text("Skip") }
            Spacer(Modifier.height(0.dp))
            OutlinedButton(
                onClick = { onResolve(book.id, name, addToCatalog) },
                enabled = name.isNotBlank(),
            ) { Text("Apply") }
        }
    }
}
