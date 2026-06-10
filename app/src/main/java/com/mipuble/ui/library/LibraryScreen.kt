package com.mipuble.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipuble.domain.model.Book
import com.mipuble.domain.sort.BookSortOption
import com.mipuble.ui.theme.MipubleTheme
import kotlin.math.absoluteValue

@Composable
fun LibraryScreen(viewModel: LibraryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryContent(uiState = uiState, onSortSelected = viewModel::onSortSelected)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryContent(
    uiState: LibraryUiState,
    onSortSelected: (BookSortOption) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    SortMenu(selected = uiState.sortOption, onSortSelected = onSortSelected)
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> Unit

            uiState.books.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Your library is empty",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(uiState.books, key = { it.id }) { book ->
                        BookCard(book = book)
                    }
                }
            }
        }
    }
}

@Composable
private fun SortMenu(
    selected: BookSortOption,
    onSortSelected: (BookSortOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Sort library")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        BookSortOption.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                trailingIcon = {
                    if (option == selected) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                },
                onClick = {
                    expanded = false
                    onSortSelected(option)
                },
            )
        }
    }
}

private val BookSortOption.label: String
    get() = when (this) {
        BookSortOption.TITLE_NATURAL -> "Title"
        BookSortOption.TITLE_LEXICOGRAPHIC -> "Title (strict A–Z)"
        BookSortOption.AUTHOR -> "Author"
        BookSortOption.DATE_ADDED -> "Recently added"
    }

@Composable
private fun BookCard(book: Book, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        // Placeholder cover until Phase 2 extracts real cover images:
        // a stable per-title color with the title as cover text.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
                .background(book.placeholderCoverColor()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
        if (book.progress > 0f) {
            LinearProgressIndicator(
                progress = { book.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = book.author,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val coverPalette = listOf(
    Color(0xFF1B4D3E),
    Color(0xFF3E5C76),
    Color(0xFF6D435A),
    Color(0xFF7A542E),
    Color(0xFF4A5240),
    Color(0xFF513B56),
)

private fun Book.placeholderCoverColor(): Color =
    coverPalette[title.hashCode().absoluteValue % coverPalette.size]

@Preview(showBackground = true)
@Composable
private fun LibraryContentPreview() {
    MipubleTheme {
        LibraryContent(
            uiState = LibraryUiState(
                isLoading = false,
                books = List(7) { index ->
                    Book(
                        id = index.toLong(),
                        title = "The Glass Archivist, Vol. ${index + 1}",
                        author = "Mira Holt",
                        addedAtEpochMillis = 0L,
                        progress = if (index == 0) 0.6f else 0f,
                    )
                },
            ),
            onSortSelected = {},
        )
    }
}
