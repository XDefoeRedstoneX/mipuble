package com.mipuble.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mipuble.domain.model.Book
import kotlinx.coroutines.delay

/**
 * Post-import confirmation. Every newly added book is listed with its best-guess
 * official name pre-selected; the user can switch to another close suggestion,
 * or search the full catalog per book. "Apply all" confirms every row's
 * selection at once (teaching the catalog any name the user actually picked); a
 * row can be skipped to keep its imported title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSheet(
    books: List<Book>,
    onApplyAll: (List<ReviewDecision>) -> Unit,
    onSkip: (bookId: Long) -> Unit,
    onSearch: suspend (String) -> List<String>,
    onDismiss: () -> Unit,
) {
    // Per-book *overrides* only — the default (top suggestion, else the imported
    // title) is derived read-only, so nothing is written to state during
    // composition.
    val overrides = remember { mutableStateMapOf<Long, String>() }
    fun selectedFor(book: Book) =
        overrides[book.id] ?: book.reviewSuggestions.firstOrNull() ?: book.title

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text("Confirm book names", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Pick the right official series for each book, or search for it. " +
                    "Apply all accepts the highlighted choices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        onApplyAll(
                            books.map { book ->
                                val name = selectedFor(book)
                                // Only teach the catalog a name the user actually
                                // chose — never the messy imported title left as the
                                // fallback when nothing matched.
                                ReviewDecision(book.id, name, addToCatalog = name != book.title)
                            },
                        )
                    },
                ) { Text("Apply all (${books.size})") }
                TextButton(onClick = onDismiss) { Text("Later") }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()

            LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                items(books, key = { it.id }) { book ->
                    ReviewRow(
                        book = book,
                        selected = selectedFor(book),
                        onSelect = { overrides[book.id] = it },
                        onSkip = { onSkip(book.id) },
                        onSearch = onSearch,
                    )
                    HorizontalDivider()
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** One book's confirmed name and whether to add it to the writable catalog. */
data class ReviewDecision(val bookId: Long, val name: String, val addToCatalog: Boolean)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewRow(
    book: Book,
    selected: String,
    onSelect: (String) -> Unit,
    onSkip: () -> Unit,
    onSearch: suspend (String) -> List<String>,
) {
    var query by rememberSaveable(book.id) { mutableStateOf("") }
    val results = remember { mutableStateListOf<String>() }

    // Debounced catalog search as the user types.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results.clear()
        } else {
            delay(200)
            results.clear()
            results.addAll(onSearch(query))
        }
    }

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            "Imported as “${book.title}”",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "→ $selected",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))

        // Top suggestions as quick-pick chips.
        if (book.reviewSuggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                book.reviewSuggestions.forEach { suggestion ->
                    FilterChip(
                        selected = selected == suggestion,
                        onClick = { onSelect(suggestion) },
                        label = { Text(suggestion, maxLines = 1) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search the catalog") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Search hits — tapping one selects it and collapses the list.
        results.take(6).forEach { hit ->
            TextButton(
                onClick = {
                    onSelect(hit)
                    query = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    hit,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onSkip) { Text("Skip this one") }
        }
    }
}
