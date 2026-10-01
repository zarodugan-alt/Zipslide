package com.example.ui.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import android.provider.DocumentsContract
import com.example.data.AppSettings
import com.example.data.VolumeRepository
import com.example.data.ZipRepository
import com.example.data.model.BrowserFilter
import com.example.data.model.SortBy
import com.example.data.model.ViewMode
import com.example.data.model.ZipItem
import com.example.design.ZipSlideTheme
import com.example.ui.components.ActionSheet
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.FilterChipRow
import com.example.ui.components.InfoDialog
import com.example.ui.components.ProgressSheet
import com.example.ui.components.RenameDialog
import com.example.ui.components.ShimmerBlock
import com.example.ui.components.ZipActions
import com.example.ui.components.ZipCard
import com.example.ui.components.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    zips: List<ZipItem>,
    isScanning: Boolean,
    settings: AppSettings,
    volumeRepository: VolumeRepository,
    zipRepository: ZipRepository,
    onNavigateToSlideshow: (zipPath: String, startFrame: Int) -> Unit,
    onNavigateToContents: (zipPath: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToStorageOverview: () -> Unit,
    onSelectFolderToScan: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val gridState = rememberLazyGridState()

    var selectedFilter by remember { mutableStateOf(BrowserFilter.ALL) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Action sheet state
    var selectedZipForSheet by remember { mutableStateOf<ZipItem?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Selection mode state
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedZipPaths = remember { mutableStateListOf<String>() }

    // Dialog states
    var infoZip by remember { mutableStateOf<ZipItem?>(null) }
    var renameZip by remember { mutableStateOf<ZipItem?>(null) }
    var deleteZipTarget by remember { mutableStateOf<ZipItem?>(null) }
    var isDeletingSelection by remember { mutableStateOf(false) }

    // Progress sheet for extraction
    var progressTitle by remember { mutableStateOf<String?>(null) }
    var progressCurrent by remember { mutableStateOf(0) }
    var progressTotal by remember { mutableStateOf(0) }
    var progressCurrentName by remember { mutableStateOf("") }

    // Menus
    var sortMenuOpen by remember { mutableStateOf(false) }
    var overflowMenuOpen by remember { mutableStateOf(false) }

    // Filter items based on active filter chip
    val filteredZips = remember(zips, selectedFilter) {
        when (selectedFilter) {
            BrowserFilter.ALL -> zips
            BrowserFilter.FAVORITES -> zips.filter { it.isFavorite }
            BrowserFilter.MISSING_COVER -> zips.filter { !it.hasCover && it.imageCount > 0 }
        }
    }

    // Top bar title collapse logic
    val isScrolled by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 40 }
    }

    // Multiple volumes check
    val multiVolumes = remember(volumeRepository.volumes.value) {
        volumeRepository.volumes.value.size > 1
    }

    // SAF Picker for Copy / Move
    var pendingCopyZip by remember { mutableStateOf<ZipItem?>(null) }
    var isPendingMove by remember { mutableStateOf(false) }
    val safFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null && pendingCopyZip != null) {
            val zip = pendingCopyZip!!
            scope.launch {
                progressTitle = if (isPendingMove) "Moving zip…" else "Copying zip…"
                progressCurrent = 1
                progressTotal = 1
                progressCurrentName = zip.name
                // Perform SAF copy
                withContext(Dispatchers.IO) {
                    try {
                        val srcFile = File(zip.path)
                        val docId = DocumentsContract.getTreeDocumentId(uri)
                        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)
                        val createdDoc = DocumentsContract.createDocument(context.contentResolver, parentDocUri, "application/zip", zip.name)
                        if (createdDoc != null) {
                            context.contentResolver.openOutputStream(createdDoc)?.use { out ->
                                srcFile.inputStream().use { input -> input.copyTo(out) }
                            }
                            if (isPendingMove) {
                                zipRepository.deleteZip(zip)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                progressTitle = null
                snackbarHostState.showSnackbar(if (isPendingMove) "Moved to selected folder" else "Copied to selected folder")
                pendingCopyZip = null
            }
        }
    }

    // Action handlers implementation
    val actions = remember {
        object : ZipActions {
            override fun onPlaySlideshow(zip: ZipItem) {
                if (!zip.isMounted) {
                    scope.launch { snackbarHostState.showSnackbar("Reinsert the SD card to open this zip") }
                    return
                }
                onNavigateToSlideshow(zip.path, zip.lastFrameIndex)
            }

            override fun onOpenContents(zip: ZipItem) {
                if (!zip.isMounted) {
                    scope.launch { snackbarHostState.showSnackbar("Reinsert the SD card to open this zip") }
                    return
                }
                onNavigateToContents(zip.path)
            }

            override fun onToggleFavorite(zip: ZipItem) {
                scope.launch {
                    zipRepository.toggleFavorite(zip.path)
                }
            }

            override fun onRename(zip: ZipItem) {
                renameZip = zip
            }

            override fun onCopyTo(zip: ZipItem) {
                pendingCopyZip = zip
                isPendingMove = false
                safFolderPicker.launch(null)
            }

            override fun onMoveTo(zip: ZipItem) {
                pendingCopyZip = zip
                isPendingMove = true
                safFolderPicker.launch(null)
            }

            override fun onShare(zip: ZipItem) {
                try {
                    val file = File(zip.path)
                    if (!file.exists()) {
                        Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
                        return
                    }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share ZipSlide Archive"))
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not share file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onExtractImages(zip: ZipItem) {
                scope.launch {
                    progressTitle = "Extracting images…"
                    progressCurrent = 0
                    progressTotal = zip.imageCount
                    val result = zipRepository.extractImages(zip) { current, total, name ->
                        progressCurrent = current
                        progressTotal = total
                        progressCurrentName = name
                    }
                    progressTitle = null
                    if (result.isSuccess) {
                        snackbarHostState.showSnackbar("Extracted ${result.getOrNull()?.name}")
                    } else {
                        snackbarHostState.showSnackbar("Extraction failed: ${result.exceptionOrNull()?.message}")
                    }
                }
            }

            override fun onInfo(zip: ZipItem) {
                infoZip = zip
            }

            override fun onDelete(zip: ZipItem) {
                deleteZipTarget = zip
            }
        }
    }

    BackHandler(enabled = isSelectionMode || searchOpen) {
        if (searchOpen) searchOpen = false
        else if (isSelectionMode) {
            isSelectionMode = false
            selectedZipPaths.clear()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ZipSlideTheme.colors.bg,
        topBar = {
            if (isSelectionMode) {
                // Selection Mode Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(64.dp)
                        .background(ZipSlideTheme.colors.surfaceElev)
                        .padding(horizontal = ZipSlideTheme.spacing.s8),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        isSelectionMode = false
                        selectedZipPaths.clear()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close selection", tint = ZipSlideTheme.colors.textPrimary)
                    }
                    Text(
                        text = "${selectedZipPaths.size} selected",
                        style = ZipSlideTheme.typography.titleM,
                        color = ZipSlideTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        scope.launch {
                            for (path in selectedZipPaths) {
                                zipRepository.toggleFavorite(path)
                            }
                            isSelectionMode = false
                            selectedZipPaths.clear()
                        }
                    }) {
                        Icon(Icons.Default.Star, contentDescription = "Favorite selected", tint = ZipSlideTheme.colors.accent)
                    }
                    IconButton(onClick = {
                        isDeletingSelection = true
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = ZipSlideTheme.colors.error)
                    }
                }
            } else {
                // Normal Top Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = ZipSlideTheme.spacing.screenGutter)
                        .padding(top = ZipSlideTheme.spacing.s8)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Zips",
                                style = if (isScrolled) ZipSlideTheme.typography.titleL else ZipSlideTheme.typography.display,
                                color = ZipSlideTheme.colors.textPrimary
                            )
                            val totalSize = remember(zips) { formatBytes(zips.sumOf { it.size }) }
                            val volumeCount = volumeRepository.volumes.value.size
                            Text(
                                text = if (isScanning) "Scanning…" else "${zips.size} zips · $totalSize · $volumeCount volumes",
                                style = ZipSlideTheme.typography.caption,
                                color = ZipSlideTheme.colors.textSecondary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Search icon (48dp target, 24dp glyph)
                            IconButton(
                                onClick = { searchOpen = true },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = ZipSlideTheme.colors.textSecondary)
                            }

                            // Sort icon & dropdown
                            Box {
                                IconButton(
                                    onClick = { sortMenuOpen = true },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Sort, contentDescription = "Sort", tint = ZipSlideTheme.colors.textSecondary)
                                }
                                DropdownMenu(
                                    expanded = sortMenuOpen,
                                    onDismissRequest = { sortMenuOpen = false },
                                    modifier = Modifier.background(ZipSlideTheme.colors.surfaceElev)
                                ) {
                                    SortBy.entries.forEach { sortOption ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    RadioButton(
                                                        selected = settings.sortBy == sortOption,
                                                        onClick = null,
                                                        colors = RadioButtonDefaults.colors(selectedColor = ZipSlideTheme.colors.accent)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = sortOption.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                                                        color = ZipSlideTheme.colors.textPrimary
                                                    )
                                                }
                                            },
                                            onClick = {
                                                sortMenuOpen = false
                                                scope.launch {
                                                    zipRepository.triggerRescan()
                                                    val repo = com.example.ZipSlideApplication.instance.settingsRepository
                                                    repo.updateSortBy(sortOption)
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            // Overflow menu
                            Box {
                                IconButton(
                                    onClick = { overflowMenuOpen = true },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = ZipSlideTheme.colors.textSecondary)
                                }
                                DropdownMenu(
                                    expanded = overflowMenuOpen,
                                    onDismissRequest = { overflowMenuOpen = false },
                                    modifier = Modifier.background(ZipSlideTheme.colors.surfaceElev)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Rescan storage", color = ZipSlideTheme.colors.textPrimary) },
                                        onClick = {
                                            overflowMenuOpen = false
                                            zipRepository.triggerRescan()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Select all", color = ZipSlideTheme.colors.textPrimary) },
                                        onClick = {
                                            overflowMenuOpen = false
                                            isSelectionMode = true
                                            selectedZipPaths.clear()
                                            selectedZipPaths.addAll(filteredZips.map { it.path })
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Storage overview", color = ZipSlideTheme.colors.textPrimary) },
                                        onClick = {
                                            overflowMenuOpen = false
                                            onNavigateToStorageOverview()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Settings", color = ZipSlideTheme.colors.textPrimary) },
                                        onClick = {
                                            overflowMenuOpen = false
                                            onNavigateToSettings()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s12))
                    FilterChipRow(
                        selectedFilter = selectedFilter,
                        onFilterSelected = { selectedFilter = it }
                    )
                    Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s12))
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isScanning && zips.isEmpty()) {
                // Skeleton grid of 6 shimmer cards during initial scan
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = ZipSlideTheme.spacing.screenGutter, vertical = ZipSlideTheme.spacing.s8),
                    horizontalArrangement = Arrangement.spacedBy(ZipSlideTheme.spacing.cardGap),
                    verticalArrangement = Arrangement.spacedBy(ZipSlideTheme.spacing.cardGap),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(6) {
                        Box(
                            modifier = Modifier
                                .aspectRatio(4f / 5f)
                                .clip(RoundedCornerShape(ZipSlideTheme.radii.lg))
                        ) {
                            ShimmerBlock(modifier = Modifier.fillMaxSize(), radius = ZipSlideTheme.radii.lg)
                        }
                    }
                }
            } else if (filteredZips.isEmpty()) {
                // Context-aware empty state
                val emptyTitle = when (selectedFilter) {
                    BrowserFilter.FAVORITES -> "No favorites yet"
                    BrowserFilter.MISSING_COVER -> "All zips have covers"
                    BrowserFilter.ALL -> "No zips here"
                }
                val emptyBody = when (selectedFilter) {
                    BrowserFilter.FAVORITES -> "Long-press any slideshow card and tap the star to add it to your favorites."
                    BrowserFilter.MISSING_COVER -> "Every zip slideshow in your library has a valid 1.jpg cover!"
                    BrowserFilter.ALL -> "Point ZipSlide at the folder where your slideshow zips live."
                }
                val buttonText = if (selectedFilter == BrowserFilter.ALL) "Choose folder" else null

                EmptyState(
                    title = emptyTitle,
                    body = emptyBody,
                    buttonText = buttonText,
                    onButtonClick = onSelectFolderToScan,
                    icon = Icons.Default.FolderZip,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // Main 2-column Grid (4:5 ratio, 12dp gap, 20dp gutter)
                val columnsCount = when (settings.viewMode) {
                    ViewMode.SMALL -> 3
                    ViewMode.MEDIUM -> 2
                    ViewMode.LARGE -> 1
                    ViewMode.LIST -> 1
                }

                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columnsCount),
                    contentPadding = PaddingValues(
                        start = ZipSlideTheme.spacing.screenGutter,
                        end = ZipSlideTheme.spacing.screenGutter,
                        top = ZipSlideTheme.spacing.s8,
                        bottom = ZipSlideTheme.spacing.s64
                    ),
                    horizontalArrangement = Arrangement.spacedBy(ZipSlideTheme.spacing.cardGap),
                    verticalArrangement = Arrangement.spacedBy(ZipSlideTheme.spacing.cardGap),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredZips, key = { it.path }) { zip ->
                        val isSelected = selectedZipPaths.contains(zip.path)
                        ZipCard(
                            zipItem = zip,
                            showMultiVolumeBadge = multiVolumes,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    if (isSelected) selectedZipPaths.remove(zip.path)
                                    else selectedZipPaths.add(zip.path)
                                    if (selectedZipPaths.isEmpty()) isSelectionMode = false
                                } else {
                                    actions.onPlaySlideshow(zip)
                                }
                            },
                            onLongClick = {
                                if (isSelectionMode) {
                                    if (isSelected) selectedZipPaths.remove(zip.path)
                                    else selectedZipPaths.add(zip.path)
                                } else {
                                    selectedZipForSheet = zip
                                }
                            }
                        )
                    }
                }
            }

            // Action Sheet
            if (selectedZipForSheet != null) {
                ActionSheet(
                    zipItem = selectedZipForSheet,
                    sheetState = sheetState,
                    onDismissRequest = { selectedZipForSheet = null },
                    actions = actions
                )
            }

            // Info Dialog
            if (infoZip != null) {
                InfoDialog(zipItem = infoZip!!, onDismiss = { infoZip = null })
            }

            // Rename Dialog
            if (renameZip != null) {
                RenameDialog(
                    currentName = renameZip!!.name,
                    onConfirm = { newName ->
                        val target = renameZip!!
                        renameZip = null
                        scope.launch {
                            val result = zipRepository.renameZip(target, newName)
                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar("Renamed to $newName.zip")
                            } else {
                                snackbarHostState.showSnackbar("Rename failed: ${result.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    onDismiss = { renameZip = null }
                )
            }

            // Single Delete Dialog
            if (deleteZipTarget != null) {
                ConfirmDialog(
                    title = "Delete zip",
                    message = "Are you sure you want to permanently delete '${deleteZipTarget!!.name}' from storage?",
                    confirmText = "Delete",
                    isDestructive = true,
                    onConfirm = {
                        val target = deleteZipTarget!!
                        deleteZipTarget = null
                        scope.launch {
                            val result = zipRepository.deleteZip(target)
                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar("Deleted ${target.name}")
                            } else {
                                snackbarHostState.showSnackbar("Delete failed: ${result.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    onDismiss = { deleteZipTarget = null }
                )
            }

            // Batch Delete Dialog
            if (isDeletingSelection) {
                ConfirmDialog(
                    title = "Delete ${selectedZipPaths.size} zips",
                    message = "Are you sure you want to delete ${selectedZipPaths.size} selected slideshow zip files?",
                    confirmText = "Delete All",
                    isDestructive = true,
                    onConfirm = {
                        isDeletingSelection = false
                        val targets = selectedZipPaths.toList()
                        isSelectionMode = false
                        selectedZipPaths.clear()
                        scope.launch {
                            for (p in targets) {
                                val item = zips.find { it.path == p }
                                if (item != null) {
                                    zipRepository.deleteZip(item)
                                }
                            }
                            snackbarHostState.showSnackbar("Deleted ${targets.size} zips")
                        }
                    },
                    onDismiss = { isDeletingSelection = false }
                )
            }

            // Progress Sheet for copy / extract
            if (progressTitle != null) {
                ProgressSheet(
                    title = progressTitle!!,
                    current = progressCurrent,
                    total = progressTotal,
                    currentFileName = progressCurrentName,
                    onCancel = { progressTitle = null }
                )
            }

            // Fullscreen Search Overlay
            AnimatedVisibility(
                visible = searchOpen,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                SearchOverlay(
                    allZips = zips,
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        searchOpen = false
                        searchQuery = ""
                    },
                    onZipSelected = { zip ->
                        searchOpen = false
                        searchQuery = ""
                        actions.onPlaySlideshow(zip)
                    }
                )
            }
        }
    }
}

@Composable
fun SearchOverlay(
    allZips: List<ZipItem>,
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onZipSelected: (ZipItem) -> Unit
) {
    val searchResults = remember(query, allZips) {
        if (query.isBlank()) emptyList()
        else allZips.filter {
            it.name.contains(query, ignoreCase = true) || it.path.contains(query, ignoreCase = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ZipSlideTheme.colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZipSlideTheme.spacing.screenGutter, vertical = ZipSlideTheme.spacing.s8),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search zips or paths…", color = ZipSlideTheme.colors.textTertiary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZipSlideTheme.colors.accent,
                        unfocusedBorderColor = ZipSlideTheme.colors.outline,
                        focusedTextColor = ZipSlideTheme.colors.textPrimary,
                        unfocusedTextColor = ZipSlideTheme.colors.textPrimary
                    ),
                    shape = RoundedCornerShape(ZipSlideTheme.radii.full),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(ZipSlideTheme.spacing.s8))
                TextButton(onClick = onClose) {
                    Text("Cancel", color = ZipSlideTheme.colors.textSecondary)
                }
            }

            if (query.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(ZipSlideTheme.spacing.sectionGap),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Type a filename or folder to search",
                        style = ZipSlideTheme.typography.body,
                        color = ZipSlideTheme.colors.textTertiary
                    )
                }
            } else if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(ZipSlideTheme.spacing.sectionGap),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No zips match '$query'",
                        style = ZipSlideTheme.typography.body,
                        color = ZipSlideTheme.colors.textSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = ZipSlideTheme.spacing.screenGutter, vertical = ZipSlideTheme.spacing.s8)
                ) {
                    items(searchResults, key = { it.path }) { zip ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable { onZipSelected(zip) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(ZipSlideTheme.radii.sm))
                                    .background(ZipSlideTheme.colors.surfaceElev),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderZip,
                                    contentDescription = null,
                                    tint = ZipSlideTheme.colors.accent,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(ZipSlideTheme.spacing.s12))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = zip.name,
                                    style = ZipSlideTheme.typography.label.copy(fontWeight = FontWeight.SemiBold),
                                    color = ZipSlideTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = zip.path,
                                    style = ZipSlideTheme.typography.caption,
                                    color = ZipSlideTheme.colors.textTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${zip.imageCount} images · ${formatBytes(zip.size)}",
                                    style = ZipSlideTheme.typography.caption,
                                    color = ZipSlideTheme.colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
