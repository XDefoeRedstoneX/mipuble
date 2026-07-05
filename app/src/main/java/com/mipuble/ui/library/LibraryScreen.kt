package com.mipuble.ui.library

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mipuble.domain.model.Book
import com.mipuble.domain.model.Category
import com.mipuble.domain.model.DownloadStatus
import com.mipuble.domain.model.UploadProgress
import com.mipuble.domain.sort.BookSortOption
import com.mipuble.domain.title.TitleNormalizer
import com.mipuble.ui.theme.Eyebrow
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    onOpenBook: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

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

    // Multi-pick for uploading EPUBs straight into the Drive mipuble folder.
    val uploadPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.onUploadBooks(uris.map { it.toString() }) }

    // Pick a folder; every EPUB under it (recursively) is uploaded.
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let { viewModel.onUploadFolder(it.toString()) } }

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // Read the granted token from the returned data rather than starting a
        // fresh authorize() — the latter re-prompted and looped.
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onConsentResult(result.data)
        } else {
            viewModel.onConsentCanceled(result.resultCode)
        }
    }

    LaunchedEffect(uiState.pendingConsent) {
        val consent = uiState.pendingConsent
        if (consent != null) {
            try {
                authLauncher.launch(IntentSenderRequest.Builder(consent).build())
            } catch (e: Exception) {
                viewModel.onConsentLaunchFailed(e.message)
            } finally {
                viewModel.onConsentShown()
            }
        }
    }

    var assigningBook by remember { mutableStateOf<Book?>(null) }
    var deletingBook by remember { mutableStateOf<Book?>(null) }
    var renamingBook by remember { mutableStateOf<Book?>(null) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var creatingCategory by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            CategoryDrawer(
                categories = uiState.categories,
                bookCount = uiState.libraryCount,
                selectedId = uiState.selectedCategoryId,
                onSelect = { id ->
                    viewModel.onCategorySelected(id)
                    scope.launch { drawerState.close() }
                },
                onEdit = { editingCategory = it },
                onCreate = { creatingCategory = true },
                onReviewAll = {
                    scope.launch { drawerState.close() }
                    viewModel.onReviewAll()
                    showReview = true
                },
                onSelectToReview = {
                    scope.launch { drawerState.close() }
                    viewModel.onEnterSelection()
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                },
            )
        },
    ) {
        LibraryContent(
            uiState = uiState,
            snackbarHostState = snackbarHostState,
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onSortSelected = viewModel::onSortSelected,
            onImportClick = { picker.launch(arrayOf("application/epub+zip", "*/*")) },
            onSync = viewModel::onSync,
            onUploadFiles = { uploadPicker.launch(arrayOf("application/epub+zip", "*/*")) },
            onUploadFolder = { folderPicker.launch(null) },
            onBookClick = { book ->
                when {
                    book.isDownloaded -> onOpenBook(book.id)
                    book.isRemote -> viewModel.onDownload(book.id)
                    else -> viewModel.onUnavailableBook()
                }
            },
            onBookLongPress = { assigningBook = it },
            onReorder = viewModel::onReorder,
            onReviewClick = { showReview = true },
            onExitSelection = viewModel::onExitSelection,
            onReviewSelected = {
                viewModel.onReviewSelected()
                showReview = true
            },
            onToggleSelected = viewModel::onToggleSelected,
        )
    }

    // Close the sheet automatically once the queue is cleared.
    LaunchedEffect(uiState.reviewQueue.isEmpty()) {
        if (uiState.reviewQueue.isEmpty()) showReview = false
    }

    if (showReview && uiState.reviewQueue.isNotEmpty()) {
        ReviewSheet(
            books = uiState.reviewQueue,
            onApplyAll = viewModel::onApplyAllReviews,
            onSkip = viewModel::onDismissReview,
            onSearch = viewModel::onSearchCatalog,
            onDismiss = { showReview = false },
        )
    }

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
            onRename = {
                assigningBook = null
                renamingBook = book
            },
            onDelete = {
                assigningBook = null
                deletingBook = book
            },
            onDismiss = { assigningBook = null },
        )
    }

    deletingBook?.let { book ->
        DeleteBookDialog(
            book = book,
            onConfirm = { alsoFromDrive ->
                viewModel.onDeleteBook(book.id, alsoFromDrive)
                deletingBook = null
            },
            onDismiss = { deletingBook = null },
        )
    }

    renamingBook?.let { book ->
        RenameBookDialog(
            book = book,
            onConfirm = { series, volume, addToBookmark ->
                viewModel.onRenameBook(book.id, series, volume, addToBookmark)
                renamingBook = null
            },
            onDismiss = { renamingBook = null },
        )
    }

    if (creatingCategory) {
        CategoryEditorDialog(
            title = "New category",
            confirmLabel = "Create",
            onConfirm = { name, color ->
                viewModel.onCreateCategory(name, color)
                creatingCategory = false
            },
            onDismiss = { creatingCategory = false },
        )
    }

    editingCategory?.let { category ->
        CategoryEditorDialog(
            title = "Edit category",
            confirmLabel = "Save",
            initialName = category.name,
            initialColorArgb = category.colorArgb,
            onConfirm = { name, color ->
                viewModel.onUpdateCategory(category.id, name, color)
                editingCategory = null
            },
            onDelete = {
                viewModel.onDeleteCategory(category.id)
                editingCategory = null
            },
            onDismiss = { editingCategory = null },
        )
    }
}

/** A thin banner showing upload-batch progress at the top of the grid. */
@Composable
private fun UploadBanner(progress: UploadProgress) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Eyebrow(if (progress.scanning) "Scanning" else "Uploading")
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (progress.scanning) {
                "Scanning folder…"
            } else {
                "${progress.currentIndex}/${progress.total} · ${progress.fileName}"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        // Indeterminate bar while scanning; determinate per-file while uploading.
        if (progress.scanning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A tappable banner prompting the user to confirm uncertain book names. */
@Composable
private fun ReviewBanner(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (count == 1) "1 book needs a name check" else "$count books need a name check",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Review",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}

// Shapes and lambdas used inside grid items — hoisted so cards scrolling into
// view don't reallocate them on every composition.
private val CoverShape = RoundedCornerShape(6.dp)
private val RailShape = RoundedCornerShape(2.dp)
private val CardShape = RoundedCornerShape(14.dp)
private val NoStopIndicator: DrawScope.() -> Unit = {}

/**
 * The cloth-bookmark silhouette: a rectangle with a downward notch in its bottom
 * edge — the signature shape of the category system. Drawn directly (no clip
 * layer) so a long, fast-scrolling bookmark list stays smooth.
 */
@Composable
private fun CategoryTag(
    color: Color,
    modifier: Modifier = Modifier,
    width: Dp = 22.dp,
    height: Dp = 30.dp,
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .drawBehind {
                // Downward-notched cloth bookmark, drawn directly (no clip layer).
                val notch = size.height * 0.28f
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(size.width / 2f, size.height - notch)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, color)
            },
    )
}


/**
 * The navigation drawer: every category is a colored bookmark row (the active
 * one highlighted), with create/settings actions pinned below the list.
 */
@Composable
private fun CategoryDrawer(
    categories: List<Category>,
    bookCount: Int,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onEdit: (Category) -> Unit,
    onCreate: () -> Unit,
    onReviewAll: () -> Unit,
    onSelectToReview: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // Accent-tinted highlight for the active shelf; hairline ribbon for the rest.
    val itemColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp)) {
            Text(
                text = "mipuble",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "$bookCount ${plural(bookCount, "book")} · " +
                    "${categories.size} ${plural(categories.size, "shelf", "shelves")}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item {
                NavigationDrawerItem(
                    label = { Text("All books") },
                    icon = {
                        CategoryTag(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            width = 18.dp,
                            height = 24.dp,
                        )
                    },
                    colors = itemColors,
                    selected = selectedId == null,
                    onClick = { onSelect(null) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            if (categories.isNotEmpty()) {
                item {
                    Eyebrow(
                        text = "Shelves",
                        modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
            }
            items(categories, key = { it.id }) { category ->
                NavigationDrawerItem(
                    label = {
                        Text(category.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    icon = {
                        CategoryTag(color = Color(category.colorArgb), width = 18.dp, height = 24.dp)
                    },
                    colors = itemColors,
                    badge = {
                        IconButton(onClick = { onEdit(category) }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit ${category.name}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    selected = selectedId == category.id,
                    onClick = { onSelect(category.id) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationDrawerItem(
            label = { Text("Create category") },
            icon = {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            selected = false,
            onClick = onCreate,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("Review book names") },
            icon = { Icon(Icons.Default.Edit, contentDescription = null) },
            selected = false,
            onClick = onReviewAll,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("Select books to review") },
            icon = { Icon(Icons.Default.Check, contentDescription = null) },
            selected = false,
            onClick = onSelectToReview,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("Settings") },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            selected = false,
            onClick = onOpenSettings,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryContent(
    uiState: LibraryUiState,
    snackbarHostState: SnackbarHostState,
    onOpenDrawer: () -> Unit,
    onSortSelected: (BookSortOption) -> Unit,
    onImportClick: () -> Unit,
    onSync: () -> Unit,
    onUploadFiles: () -> Unit,
    onUploadFolder: () -> Unit,
    onBookClick: (Book) -> Unit,
    onBookLongPress: (Book) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onReviewClick: () -> Unit = {},
    onExitSelection: () -> Unit = {},
    onReviewSelected: () -> Unit = {},
    onToggleSelected: (Long) -> Unit = {},
) {
    val title = uiState.categories
        .firstOrNull { it.id == uiState.selectedCategoryId }?.name ?: "Library"

    Scaffold(
        topBar = {
            if (uiState.selectionMode) {
                TopAppBar(
                    title = { Text("${uiState.selectedBookIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = onExitSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = onReviewSelected,
                            enabled = uiState.selectedBookIds.isNotEmpty(),
                        ) { Text("Review") }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = "Open categories")
                        }
                    },
                    actions = {
                        UploadMenu(
                            enabled = uiState.upload == null,
                            onFiles = onUploadFiles,
                            onFolder = onUploadFolder,
                        )
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
                    },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onImportClick,
                shape = RoundedCornerShape(19.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
            ) {
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
            // Banner occupies its own row above the grid (not overlapping it).
            uiState.upload?.let { UploadBanner(it) }
            if (uiState.reviewQueue.isNotEmpty()) {
                ReviewBanner(count = uiState.reviewQueue.size, onClick = onReviewClick)
            }

            // Hero + shelf header ride above the grid (kept out of the grid so the
            // reorder index space stays untouched). The hero is only shown in the
            // unfiltered "All books" view, matching where it's sourced from. The
            // header stays visible on an empty shelf — it hosts the app's only
            // sort control, which must remain reachable to leave "My order".
            if (!uiState.selectionMode && !uiState.isLoading) {
                if (uiState.selectedCategoryId == null) {
                    uiState.continueReading?.let { book ->
                        ContinueReadingCard(book = book, onOpen = { onBookClick(book) })
                    }
                }
                ShelfHeader(
                    label = if (uiState.selectedCategoryId == null) "All books" else title,
                    count = uiState.books.size,
                    sortOption = uiState.sortOption,
                    onSortSelected = onSortSelected,
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                        onToggleSelected = onToggleSelected,
                    )
                }
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
    onToggleSelected: (Long) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val localBooks = remember(uiState.books) { uiState.books.toMutableStateList() }
    // O(1) ribbon lookup per card; a linear scan would run per item per recomposition.
    val categoriesById = remember(uiState.categories) { uiState.categories.associateBy { it.id } }

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
        columns = GridCells.Adaptive(minSize = 116.dp),
        state = gridState,
        modifier = gridModifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(localBooks, key = { _, book -> book.id }) { index, book ->
            BookCard(
                book = book,
                category = book.categoryId?.let { categoriesById[it] },
                downloadStatus = uiState.downloads[book.id],
                selectionMode = uiState.selectionMode,
                isSelected = book.id in uiState.selectedBookIds,
                modifier = Modifier
                    .reorderableItem(dragState, index)
                    .then(
                        when {
                            // Selecting books to review: taps toggle, no long-press.
                            uiState.selectionMode ->
                                Modifier.combinedClickable(onClick = { onToggleSelected(book.id) })
                            // Taps still open; long-press is claimed by the drag.
                            uiState.isReorderingEnabled ->
                                Modifier.combinedClickable(onClick = { onBookClick(book) })
                            else ->
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
private fun AssignCategoryDialog(
    book: Book,
    categories: List<Category>,
    onAssign: (Long?) -> Unit,
    onEvict: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = CardShape,
        title = { Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                // The category list scrolls within a bounded height so the
                // Rename/Delete actions below it always stay reachable.
                Column(
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
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
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                TextButton(onClick = onRename) {
                    Text("Rename book…")
                }
                TextButton(onClick = onDelete) {
                    Text("Delete book…", color = MaterialTheme.colorScheme.error)
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

/**
 * Confirms removing a book. If the book is in Drive, an opt-in moves the Drive
 * copy to trash (recoverable) as well; otherwise it's just removed locally.
 */
@Composable
private fun DeleteBookDialog(
    book: Book,
    onConfirm: (alsoFromDrive: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var alsoFromDrive by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = CardShape,
        title = { Text("Delete book") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Remove \"${book.title}\" from your library?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (book.isRemote) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = alsoFromDrive, onCheckedChange = { alsoFromDrive = it })
                        Text(
                            "Also move the Drive copy to trash",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(alsoFromDrive) }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Manual rename: splits the current title into editable series + volume fields
 * (volume accepts decimals like "1.5"), with an opt-in to file it on a per-series
 * bookmark. Pre-filled by re-parsing the book's current title.
 */
@Composable
private fun RenameBookDialog(
    book: Book,
    onConfirm: (series: String, volume: String?, addToBookmark: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val parsed = remember(book.id) { TitleNormalizer.normalize(book.title) }
    var series by remember(book.id) { mutableStateOf(parsed.series) }
    var volume by remember(book.id) { mutableStateOf(parsed.volume ?: "") }
    var addToBookmark by remember(book.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename book") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = series,
                    onValueChange = { series = it },
                    label = { Text("Series name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = volume,
                    onValueChange = { volume = it },
                    label = { Text("Volume (optional, e.g. 1.5)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = addToBookmark, onCheckedChange = { addToBookmark = it })
                    Text("Add to bookmark")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(series.trim(), volume.trim().ifBlank { null }, addToBookmark) },
                enabled = series.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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

/** Warm "cloth" palette offered for categories; a hue slider covers the rest. */
private val categoryPalette = listOf(
    0xFFC1683C, 0xFF3E7F79, 0xFF82577E, 0xFFC39B45,
    0xFF536493, 0xFFA8493A, 0xFF5E7B54, 0xFF6B7078,
).map { Color(it) }

/** Shared create/edit dialog: name field, palette, hue slider, optional delete. */
@Composable
private fun CategoryEditorDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (name: String, colorArgb: Int) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    initialColorArgb: Int? = null,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedColor by remember {
        mutableStateOf(initialColorArgb?.let { Color(it) } ?: categoryPalette.first())
    }
    var hue by remember { mutableStateOf<Float?>(null) }

    val effectiveColor = hue?.let { Color.hsv(it, 0.6f, 0.7f) } ?: selectedColor

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = CardShape,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryTag(color = effectiveColor)
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

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
                onClick = { onConfirm(name, effectiveColor.toArgb()) },
                enabled = name.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun UploadMenu(
    enabled: Boolean,
    onFiles: () -> Unit,
    onFolder: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }, enabled = enabled) {
        Icon(
            painter = painterResource(com.mipuble.R.drawable.ic_cloud_upload),
            contentDescription = "Upload to Drive",
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text("Upload files…") },
            onClick = {
                expanded = false
                onFiles()
            },
        )
        DropdownMenuItem(
            text = { Text("Upload folder…") },
            onClick = {
                expanded = false
                onFolder()
            },
        )
    }
}

/** Pill surfacing the current sort; tapping opens the full sort menu. */
@Composable
private fun SortChip(
    selected: BookSortOption,
    onSortSelected: (BookSortOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // minimumInteractiveComponentSize keeps the 48dp touch target the
            // old IconButton provided; the visual pill stays compact.
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable { expanded = true }
                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.List,
                contentDescription = "Sort library",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = selected.chipLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
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
}

/** Eyebrow (shelf name · count) on the left, sort chip on the right. */
@Composable
private fun ShelfHeader(
    label: String,
    count: Int,
    sortOption: BookSortOption,
    onSortSelected: (BookSortOption) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Eyebrow(
            text = "$label · $count",
            modifier = Modifier.weight(1f),
        )
        SortChip(selected = sortOption, onSortSelected = onSortSelected)
    }
}

/** The fine 4dp accent progress rail shared by the grid cards and the hero. */
@Composable
private fun BookProgressRail(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress },
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = NoStopIndicator,
        modifier = modifier
            .height(4.dp)
            .clip(RailShape),
    )
}

/**
 * A book cover: the image when present, else a generated cover — the title set
 * in cloth ink on cream paper. Ribbons/overlays layer on via [content].
 */
@Composable
private fun BookCover(
    book: Book,
    titleStyle: TextStyle,
    titlePadding: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .clip(CoverShape)
            .background(PlaceholderPaper),
        contentAlignment = Alignment.Center,
    ) {
        if (book.coverPath != null) {
            AsyncImage(
                model = File(book.coverPath),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = book.title,
                style = titleStyle,
                color = book.placeholderCoverColor(),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(titlePadding),
            )
        }
        content()
    }
}

/** Horizontal hero surfacing the book to resume; tapping opens the reader. */
@Composable
private fun ContinueReadingCard(book: Book, onOpen: () -> Unit) {
    Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp)) {
        Eyebrow("Continue reading")
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
                .clickable(onClick = onOpen)
                .padding(14.dp),
        ) {
            BookCover(
                book = book,
                titleStyle = MaterialTheme.typography.labelMedium,
                titlePadding = 6.dp,
                modifier = Modifier
                    .width(66.dp)
                    .height(99.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BookProgressRail(progress = book.progress, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${(book.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Chapter ${book.lastChapterIndex + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Tiny English pluralizer for the drawer counts. */
private fun plural(n: Int, one: String, many: String = one + "s"): String =
    if (n == 1) one else many

private val BookSortOption.label: String
    get() = when (this) {
        BookSortOption.TITLE_NATURAL -> "Title"
        BookSortOption.TITLE_LEXICOGRAPHIC -> "Title (strict A–Z)"
        BookSortOption.AUTHOR -> "Author"
        BookSortOption.DATE_ADDED -> "Recently added"
        BookSortOption.CUSTOM -> "My order (drag to arrange)"
    }

/** Short form for the sort chip (the full [label] is too long for a pill). */
private val BookSortOption.chipLabel: String
    get() = when (this) {
        BookSortOption.TITLE_NATURAL -> "Title"
        BookSortOption.TITLE_LEXICOGRAPHIC -> "A–Z"
        BookSortOption.AUTHOR -> "Author"
        BookSortOption.DATE_ADDED -> "Recent"
        BookSortOption.CUSTOM -> "My order"
    }

@Composable
private fun BookCard(
    book: Book,
    category: Category?,
    downloadStatus: DownloadStatus?,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
) {
    Column(
        // Not-yet-downloaded books read as dimmed until their bytes arrive.
        modifier = modifier.alpha(if (book.isDownloaded) 1f else 0.6f),
    ) {
        BookCover(
            book = book,
            titleStyle = MaterialTheme.typography.titleSmall,
            titlePadding = 10.dp,
            contentDescription = "Cover of ${book.title}",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.667f),
        ) {
            // Category ribbon: a cloth bookmark flush to the cover's top edge.
            if (category != null) {
                CategoryTag(
                    color = Color(category.colorArgb),
                    width = 15.dp,
                    height = 24.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 10.dp),
                )
            }

            DownloadOverlay(book = book, status = downloadStatus)

            // Multi-select affordance: a tick on chosen books, a dim wash on the rest.
            if (selectionMode) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f)),
                    )
                }
            }
        }
        if (book.progress > 0f) {
            BookProgressRail(
                progress = book.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = book.author,
            style = MaterialTheme.typography.labelMedium,
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
                    val fraction = status.fraction
                    if (fraction == null) {
                        // Unknown size (Drive sends no length) — indeterminate.
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
                        Text(
                            "Downloading…",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { fraction },
                            color = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            "${(fraction * 100).toInt()}%",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        is DownloadStatus.Failed -> {
            // Show the tap-to-retry badge in an error tint.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .padding(4.dp),
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Download failed — tap to retry",
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(18.dp),
                )
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

/** Warm cloth ink tones for generated placeholder covers (the serif title color
 *  on a cream paper ground). */
private val coverPalette = listOf(
    Color(0xFF6B4A3A),
    Color(0xFF3E5C4E),
    Color(0xFF43566B),
    Color(0xFF7A4A52),
    Color(0xFF5C5140),
    Color(0xFF513B56),
    Color(0xFF4A4E6B),
)

/** The cream paper ground behind a generated placeholder cover. */
private val PlaceholderPaper = Color(0xFFEFE6D4)

// mod (not absoluteValue + %) — abs(Int.MIN_VALUE) is still negative and would
// index out of bounds; floored mod is non-negative for any hash.
private fun Book.placeholderCoverColor(): Color =
    coverPalette[title.hashCode().mod(coverPalette.size)]
