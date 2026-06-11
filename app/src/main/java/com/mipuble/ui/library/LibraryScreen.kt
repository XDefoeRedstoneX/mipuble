package com.mipuble.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mipuble.domain.model.Book
import com.mipuble.domain.model.Category
import com.mipuble.domain.model.DownloadStatus
import com.mipuble.domain.sort.BookSortOption
import java.io.File
import kotlin.math.absoluteValue

@Composable
fun LibraryScreen(
    onOpenBook: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    // System file picker scoped to EPUBs (with a wildcard fallback for devices
    // that don't recognize the EPUB MIME type).
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.onImport(it.toString()) } }

    var assigningBook by remember { mutableStateOf<Book?>(null) }
    var managingCategories by remember { mutableStateOf(false) }

    LibraryContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onSortSelected = viewModel::onSortSelected,
        onCategorySelected = viewModel::onCategorySelected,
        onImportClick = { picker.launch(arrayOf("application/epub+zip", "*/*")) },
        onSync = viewModel::onSync,
        onBookClick = { book ->
            when {
                book.isDownloaded -> onOpenBook(book.id)
                book.isRemote -> viewModel.onDownload(book.id)
                else -> viewModel.onUnavailableBook()
            }
        },
        onBookLongPress = { assigningBook = it },
        onManageCategories = { managingCategories = true },
        onReorder = viewModel::onReorder,
    )

    assigningBook?.let { book ->
        AssignCategoryDialog(
            book = book,
            categories = uiState.categories,
            onAssign = { categoryId ->
                viewModel.onAssignCategory(book.id, categoryId)
                assigningBook = null
            },
            onEvict = {
                viewModel.onEvict(book.id)
                assigningBook = null
            },
            onDismiss = { assigningBook = null },
        )
    }

    if (managingCategories) {
        ManageCategoriesDialog(
            categories = uiState.categories,
            onCreate = viewModel::onCreateCategory,
            onDelete = viewModel::onDeleteCategory,
            onDismiss = { managingCategories = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryContent(
    uiState: LibraryUiState,
    snackbarHostState: SnackbarHostState,
    onSortSelected: (BookSortOption) -> Unit,
    onCategorySelected: (Long?) -> Unit,
    onImportClick: () -> Unit,
    onSync: () -> Unit,
    onBookClick: (Book) -> Unit,
    onBookLongPress: (Book) -> Unit,
    onManageCategories: () -> Unit,
    onReorder: (List<Long>) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = onSync, enabled = !uiState.isSyncing) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync remote library")
                        }
                    }
                    SortMenu(selected = uiState.sortOption, onSortSelected = onSortSelected)
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onImportClick) {
                Icon(Icons.Default.Add, contentDescription = "Import EPUB")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            CategoryFilterRow(
                categories = uiState.categories,
                selectedId = uiState.selectedCategoryId,
                onSelect = onCategorySelected,
                onManage = onManageCategories,
            )

            when {
                uiState.isLoading -> Unit

                uiState.books.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (uiState.selectedCategoryId != null) {
                                "No books in this category yet.\nLong-press a book to file it here."
                            } else {
                                "Your library is empty.\nTap + to import an EPUB."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> BookGrid(
                    uiState = uiState,
                    onBookClick = onBookClick,
                    onBookLongPress = onBookLongPress,
                    onReorder = onReorder,
                )
            }
        }
    }
}

/**
 * The grid in two modes: normal (tap to open, long-press to categorize) and
 * reorder (long-press drags, sort = Custom). While dragging, order changes are
 * applied to a local snapshot list for instant feedback; the result is
 * persisted once on drop, and Room's re-emission then matches the local order.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookGrid(
    uiState: LibraryUiState,
    onBookClick: (Book) -> Unit,
    onBookLongPress: (Book) -> Unit,
    onReorder: (List<Long>) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val localBooks = remember(uiState.books) { uiState.books.toMutableStateList() }

    val dragState = rememberGridDragState(
        gridState = gridState,
        onMove = { from, to ->
            if (from in localBooks.indices && to in localBooks.indices) {
                localBooks.add(to, localBooks.removeAt(from))
            }
        },
        onDragEnd = { onReorder(localBooks.map { it.id }) },
    )

    val gridModifier = if (uiState.isReorderingEnabled) {
        Modifier.reorderableGrid(dragState)
    } else {
        Modifier
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        state = gridState,
        modifier = gridModifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(localBooks, key = { _, book -> book.id }) { index, book ->
            BookCard(
                book = book,
                category = uiState.categories.firstOrNull { it.id == book.categoryId },
                downloadStatus = uiState.downloads[book.id],
                modifier = Modifier
                    .reorderableItem(dragState, index)
                    .then(
                        if (uiState.isReorderingEnabled) {
                            // Taps still open; long-press is claimed by the drag.
                            Modifier.combinedClickable(onClick = { onBookClick(book) })
                        } else {
                            Modifier.combinedClickable(
                                onClick = { onBookClick(book) },
                                onLongClick = { onBookLongPress(book) },
                            )
                        },
                    ),
            )
        }
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onManage: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selectedId == null,
                onClick = { onSelect(null) },
                label = { Text("All") },
            )
        }
        items(categories, key = { it.id }) { category ->
            FilterChip(
                selected = selectedId == category.id,
                onClick = { onSelect(category.id.takeIf { selectedId != category.id }) },
                label = { Text(category.name) },
                leadingIcon = { CategoryDot(category) },
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = onManage,
                label = { Text("Edit") },
            )
        }
    }
}

@Composable
private fun CategoryDot(category: Category, size: androidx.compose.ui.unit.Dp = 12.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(category.colorArgb)),
    )
}

@Composable
private fun AssignCategoryDialog(
    book: Book,
    categories: List<Category>,
    onAssign: (Long?) -> Unit,
    onEvict: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                CategoryChoiceRow(
                    label = "No category",
                    selected = book.categoryId == null,
                    color = null,
                    onClick = { onAssign(null) },
                )
                categories.forEach { category ->
                    CategoryChoiceRow(
                        label = category.name,
                        selected = book.categoryId == category.id,
                        color = Color(category.colorArgb),
                        onClick = { onAssign(category.id) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        // Reclaim space: drop the local file but keep this book's metadata.
        dismissButton = if (book.canEvict) {
            { TextButton(onClick = onEvict) { Text("Remove download") } }
        } else {
            null
        },
    )
}

@Composable
private fun CategoryChoiceRow(
    label: String,
    selected: Boolean,
    color: Color?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        if (color != null) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Palette offered for new categories; a hue slider covers everything else. */
private val categoryPalette = listOf(
    0xFF00897B, 0xFF7B1FA2, 0xFFC62828, 0xFFEF6C00,
    0xFF2E7D32, 0xFF1565C0, 0xFF6D4C41, 0xFF546E7A,
).map { Color(it) }

@Composable
private fun ManageCategoriesDialog(
    categories: List<Category>,
    onCreate: (name: String, colorArgb: Int) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(categoryPalette.first()) }
    var hue by remember { mutableStateOf<Float?>(null) }

    val effectiveColor = hue?.let { Color.hsv(it, 0.6f, 0.7f) } ?: selectedColor

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categories") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                categories.forEach { category ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryDot(category, size = 16.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            category.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        IconButton(onClick = { onDelete(category.id) }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Delete ${category.name}",
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("New category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categoryPalette.forEach { color ->
                        val isSelected = hue == null && color == selectedColor
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .clickable {
                                    selectedColor = color
                                    hue = null
                                },
                        )
                    }
                }

                // Custom hue for colors outside the palette.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(effectiveColor),
                    )
                    Slider(
                        value = hue ?: 0f,
                        onValueChange = { hue = it },
                        valueRange = 0f..360f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(name, effectiveColor.toArgb())
                    name = ""
                },
                enabled = name.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
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
        BookSortOption.CUSTOM -> "My order (drag to arrange)"
    }

@Composable
private fun BookCard(
    book: Book,
    category: Category?,
    downloadStatus: DownloadStatus?,
    modifier: Modifier = Modifier,
) {
    Column(
        // Not-yet-downloaded books read as dimmed until their bytes arrive.
        modifier = modifier.alpha(if (book.isDownloaded) 1f else 0.6f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
                .background(book.placeholderCoverColor()),
            contentAlignment = Alignment.Center,
        ) {
            if (book.coverPath != null) {
                AsyncImage(
                    model = File(book.coverPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp),
                )
            }

            // Category ribbon in the cover's top corner.
            if (category != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(category.colorArgb))
                        .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                )
            }

            DownloadOverlay(book = book, status = downloadStatus)
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

/** Cloud/progress affordance over a cover: shows download state for remote books. */
@Composable
private fun BoxScope.DownloadOverlay(book: Book, status: DownloadStatus?) {
    when (status) {
        is DownloadStatus.Downloading -> {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        progress = { status.fraction },
                        color = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        "${(status.fraction * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        else -> {
            // A remote book not yet on the device gets a "tap to download" badge.
            if (book.isRemote && !book.isDownloaded) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(4.dp),
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Download",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
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
