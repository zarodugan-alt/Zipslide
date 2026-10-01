package com.example.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppSettings
import com.example.data.model.SortBy
import com.example.data.model.ViewMode
import com.example.design.ZipSlideTheme
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.SectionHeader
import com.example.ui.components.formatBytes
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onNavigateToDiagnostics: () -> Unit,
    onBack: () -> Unit,
    cacheSizeBytes: Long = 0L,
    onEvent: (SettingsEvent) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showRegenerateConfirm by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var sortDropdownOpen by remember { mutableStateOf(false) }

    // Repository calculates cache size on Dispatchers.IO; the screen only formats immutable state.
    val cacheSize = formatBytes(cacheSizeBytes)

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            onEvent(SettingsEvent.SetCustomScanFolder(uri.toString()))
            scope.launch { snackbarHostState.showSnackbar("Scan folder set") }
        }
    }

    BackHandler {
        onBack()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ZipSlideTheme.colors.bg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = ZipSlideTheme.spacing.s8),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ZipSlideTheme.colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(ZipSlideTheme.spacing.s8))
                Text(
                    text = "Settings",
                    style = ZipSlideTheme.typography.titleM,
                    color = ZipSlideTheme.colors.textPrimary
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(bottom = ZipSlideTheme.spacing.s48)
        ) {
            // APPEARANCE SECTION
            item {
                SectionHeader(title = "Appearance")
            }

            item {
                SettingRow(label = "Theme") {
                    SegmentedButtons(
                        options = listOf("Dark", "Light", "System"),
                        selected = settings.theme.name.lowercase().replaceFirstChar { it.uppercase() },
                        onSelect = {
                            onEvent(SettingsEvent.SetTheme(it.lowercase()))
                        }
                    )
                }
            }

            item {
                SettingRow(label = "View Grid Size") {
                    SegmentedButtons(
                        options = listOf("S", "M", "L"),
                        selected = when (settings.viewMode) {
                            ViewMode.GRID_SMALL -> "S"
                            ViewMode.GRID_MEDIUM -> "M"
                            ViewMode.GRID_LARGE, ViewMode.LIST -> "L"
                        },
                        onSelect = {
                            val mode = when (it) {
                                "S" -> ViewMode.GRID_SMALL
                                "M" -> ViewMode.GRID_MEDIUM
                                else -> ViewMode.GRID_LARGE
                            }
                            onEvent(SettingsEvent.SetViewMode(mode))
                        }
                    )
                }
            }

            item {
                SettingSwitchRow(
                    label = "Show filenames on cards",
                    checked = settings.showFilenames,
                    onCheckedChange = { onEvent(SettingsEvent.SetShowFilenames(it)) }
                )
            }

            item {
                SettingSwitchRow(
                    label = "Show volume & cover badges",
                    checked = settings.showBadges,
                    onCheckedChange = { onEvent(SettingsEvent.SetShowBadges(it)) }
                )
            }

            // SORTING SECTION
            item {
                SectionHeader(title = "Sorting")
            }

            item {
                SettingRow(label = "Sort by") {
                    Box {
                        Text(
                            text = settings.sortBy.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                            style = ZipSlideTheme.typography.bodyM,
                            color = ZipSlideTheme.colors.accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(ZipSlideTheme.radii.sm))
                                .clickable { sortDropdownOpen = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        DropdownMenu(
                            expanded = sortDropdownOpen,
                            onDismissRequest = { sortDropdownOpen = false },
                            modifier = Modifier.background(ZipSlideTheme.colors.surfaceElev)
                        ) {
                            SortBy.entries.forEach { sortOption ->
                                DropdownMenuItem(
                                    text = { Text(sortOption.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, color = ZipSlideTheme.colors.textPrimary) },
                                    onClick = {
                                        sortDropdownOpen = false
                                        onEvent(SettingsEvent.SetSort(sortOption))
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingSwitchRow(
                    label = "Ascending order",
                    checked = settings.sortAscending,
                    onCheckedChange = { onEvent(SettingsEvent.SetAscending(it)) }
                )
            }

            item {
                SettingSwitchRow(
                    label = "Natural sort (2 before 10)",
                    checked = settings.naturalSort,
                    onCheckedChange = { onEvent(SettingsEvent.SetNaturalSort(it)) }
                )
            }

            // SCANNING SECTION
            item {
                SectionHeader(title = "Library")
            }

            item {
                SettingSwitchRow(
                    label = "Only 1.x slideshows",
                    description = "Hide archives that do not start with a 1.jpg / 1.png frame",
                    checked = settings.onlyNumberedCovers,
                    onCheckedChange = { onEvent(SettingsEvent.SetOnlyNumberedCovers(it)) }
                )
            }

            item {
                SectionHeader(title = "Scanning")
            }

            item {
                SettingRow(label = "Storage Source") {
                    SegmentedButtons(
                        options = listOf("All", "Internal", "SD"),
                        selected = when (settings.storageSource) {
                            "internal" -> "Internal"
                            "sd" -> "SD"
                            else -> "All"
                        },
                        onSelect = {
                            val src = when (it) {
                                "Internal" -> "internal"
                                "SD" -> "sd"
                                else -> "all"
                            }
                            onEvent(SettingsEvent.SetStorageSource(src))
                        }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { folderPickerLauncher.launch(null) }
                        .padding(horizontal = ZipSlideTheme.spacing.screenGutter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "Custom scan folder", style = ZipSlideTheme.typography.body)
                        Text(
                            text = if (settings.customScanFolderUri != null) "Folder configured" else "Default (All mounted roots)",
                            style = ZipSlideTheme.typography.caption,
                            color = ZipSlideTheme.colors.textTertiary
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ZipSlideTheme.colors.textTertiary)
                }
            }

            item {
                SettingSwitchRow(
                    label = "Include subfolders",
                    checked = settings.includeSubfolders,
                    onCheckedChange = {
                        onEvent(SettingsEvent.SetIncludeSubfolders(it))
                    }
                )
            }

            item {
                SettingSwitchRow(
                    label = "Show hidden files",
                    checked = settings.showHidden,
                    onCheckedChange = {
                        onEvent(SettingsEvent.SetShowHidden(it))
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { onNavigateToDiagnostics() }
                        .padding(horizontal = ZipSlideTheme.spacing.screenGutter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "Volume diagnostics", style = ZipSlideTheme.typography.body)
                        Text(text = "View mount points, SD permissions, and access paths", style = ZipSlideTheme.typography.caption, color = ZipSlideTheme.colors.textTertiary)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ZipSlideTheme.colors.textTertiary)
                }
            }

            // SLIDESHOW SECTION
            item {
                SectionHeader(title = "Slideshow")
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ZipSlideTheme.spacing.screenGutter, vertical = ZipSlideTheme.spacing.s8)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Slide interval", style = ZipSlideTheme.typography.body)
                        Text(
                            text = String.format("%.1fs", settings.slideshowInterval),
                            style = ZipSlideTheme.typography.bodyM,
                            color = ZipSlideTheme.colors.accent
                        )
                    }
                    Slider(
                        value = settings.slideshowInterval,
                        onValueChange = { onEvent(SettingsEvent.SetSlideInterval(it)) },
                        valueRange = 0.5f..15.0f,
                        steps = 29,
                        colors = SliderDefaults.colors(
                            thumbColor = ZipSlideTheme.colors.accent,
                            activeTrackColor = ZipSlideTheme.colors.accent,
                            inactiveTrackColor = ZipSlideTheme.colors.surfaceHigh
                        )
                    )
                }
            }

            item {
                SettingSwitchRow(
                    label = "Shuffle order",
                    checked = settings.slideshowShuffle,
                    onCheckedChange = { onEvent(SettingsEvent.SetShuffle(it)) }
                )
            }

            item {
                SettingSwitchRow(
                    label = "Loop playback",
                    checked = settings.slideshowLoop,
                    onCheckedChange = { onEvent(SettingsEvent.SetLoop(it)) }
                )
            }

            item {
                SettingSwitchRow(
                    label = "Fit to screen (preserve aspect ratio)",
                    checked = settings.slideshowFitToScreen,
                    onCheckedChange = { onEvent(SettingsEvent.SetFitToScreen(it)) }
                )
            }

            item {
                SettingSwitchRow(
                    label = "Keep screen on while playing",
                    checked = settings.slideshowKeepScreenOn,
                    onCheckedChange = { onEvent(SettingsEvent.SetKeepScreenOn(it)) }
                )
            }

            item {
                Text(
                    text = "Transition",
                    style = ZipSlideTheme.typography.bodyM,
                    color = ZipSlideTheme.colors.textPrimary,
                    modifier = Modifier.padding(
                        start = ZipSlideTheme.spacing.screenGutter,
                        end = ZipSlideTheme.spacing.screenGutter,
                        bottom = ZipSlideTheme.spacing.s8
                    )
                )
            }

            item {
                TransitionPicker(
                    selected = settings.slideTransition,
                    durationMs = settings.slideTransitionMs,
                    onSelect = { onEvent(SettingsEvent.SetTransition(it)) },
                    modifier = Modifier.padding(bottom = ZipSlideTheme.spacing.s8)
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ZipSlideTheme.spacing.screenGutter, vertical = ZipSlideTheme.spacing.s8)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Transition speed", style = ZipSlideTheme.typography.body)
                        Text(
                            text = "${settings.slideTransitionMs} ms",
                            style = ZipSlideTheme.typography.bodyM,
                            color = ZipSlideTheme.colors.accent
                        )
                    }
                    Slider(
                        value = settings.slideTransitionMs.toFloat(),
                        onValueChange = { onEvent(SettingsEvent.SetTransitionMs(it.toInt())) },
                        valueRange = 160f..1200f,
                        steps = 12,
                        colors = SliderDefaults.colors(
                            thumbColor = ZipSlideTheme.colors.accent,
                            activeTrackColor = ZipSlideTheme.colors.accent,
                            inactiveTrackColor = ZipSlideTheme.colors.surfaceHigh
                        )
                    )
                }
            }

            item {
                SettingSwitchRow(
                    label = "Tap edges to navigate",
                    description = "Right third goes forward, left third goes back, centre toggles controls",
                    checked = settings.slideTapZones,
                    onCheckedChange = { onEvent(SettingsEvent.SetTapZones(it)) }
                )
            }

            item {
                SettingSwitchRow(
                    label = "Volume keys navigate",
                    description = "Volume down jumps forward, volume up goes back — hold either to fly through frames",
                    checked = settings.slideVolumeKeys,
                    onCheckedChange = { onEvent(SettingsEvent.SetVolumeKeys(it)) }
                )
            }

            item {
                SettingSwitchRow(
                    label = "Invert volume keys",
                    description = "Volume up jumps forward instead of back",
                    checked = settings.slideVolumeKeysInverted,
                    onCheckedChange = { onEvent(SettingsEvent.SetVolumeKeysInverted(it)) }
                )
            }

            item {
                SettingSwitchRow(
                    label = "Lock rotation while viewing",
                    description = "Freezes the current orientation until you leave the viewer",
                    checked = settings.slideLockRotation,
                    onCheckedChange = { onEvent(SettingsEvent.SetLockRotation(it)) }
                )
            }

            item {
                SettingSwitchRow(
                    label = "Remaining-time line",
                    description = "Thin bar at the very top that shrinks as the frame runs out",
                    checked = settings.slideProgressLine,
                    onCheckedChange = { onEvent(SettingsEvent.SetProgressLine(it)) }
                )
            }

            item {
                SettingSwitchRow(
                    label = "Slow drift (Ken Burns)",
                    description = "Barely-there zoom that keeps still frames alive",
                    checked = settings.slideKenBurns,
                    onCheckedChange = { onEvent(SettingsEvent.SetKenBurns(it)) }
                )
            }

            item {
                SettingSwitchRow(
                    label = "Hide system bars",
                    checked = settings.slideImmersive,
                    onCheckedChange = { onEvent(SettingsEvent.SetImmersive(it)) }
                )
            }

            item {
                SettingSwitchRow(
                    label = "Haptic feedback",
                    checked = settings.slideHaptics,
                    onCheckedChange = { onEvent(SettingsEvent.SetSlideHaptics(it)) }
                )
            }

            item {
                SettingSwitchRow(
                    label = "High quality frames",
                    description = "Full-colour decoding; turn off on low-memory devices",
                    checked = settings.slideHighQuality,
                    onCheckedChange = { onEvent(SettingsEvent.SetHighQuality(it)) }
                )
            }

            // THUMBNAILS SECTION
            item {
                SectionHeader(title = "Thumbnails")
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ZipSlideTheme.spacing.screenGutter, vertical = ZipSlideTheme.spacing.s8)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Thumbnail target quality", style = ZipSlideTheme.typography.body)
                        Text(
                            text = "${settings.thumbnailPx}px",
                            style = ZipSlideTheme.typography.bodyM,
                            color = ZipSlideTheme.colors.accent
                        )
                    }
                    Slider(
                        value = settings.thumbnailPx.toFloat(),
                        onValueChange = { onEvent(SettingsEvent.SetThumbnailPx(it.toInt())) },
                        valueRange = 256f..1024f,
                        steps = 3,
                        colors = SliderDefaults.colors(
                            thumbColor = ZipSlideTheme.colors.accent,
                            activeTrackColor = ZipSlideTheme.colors.accent,
                            inactiveTrackColor = ZipSlideTheme.colors.surfaceHigh
                        )
                    )
                }
            }

            item {
                SettingRow(label = "Disk Cache Size") {
                    Text(text = cacheSize, style = ZipSlideTheme.typography.bodyM, color = ZipSlideTheme.colors.textSecondary)
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { showClearCacheConfirm = true }
                        .padding(horizontal = ZipSlideTheme.spacing.screenGutter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Clear thumbnail cache", style = ZipSlideTheme.typography.bodyM, color = ZipSlideTheme.colors.error)
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { showRegenerateConfirm = true }
                        .padding(horizontal = ZipSlideTheme.spacing.screenGutter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Regenerate all thumbnails", style = ZipSlideTheme.typography.bodyM, color = ZipSlideTheme.colors.accent)
                }
            }

            // ABOUT SECTION
            item {
                SectionHeader(title = "About")
            }

            item {
                SettingRow(label = "Version") {
                    Text(text = "1.0.0", style = ZipSlideTheme.typography.bodyM, color = ZipSlideTheme.colors.textSecondary)
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ZipSlideTheme.spacing.screenGutter, vertical = ZipSlideTheme.spacing.s12)
                ) {
                    Text(text = "The 1.jpg Rule", style = ZipSlideTheme.typography.label.copy(fontWeight = FontWeight.Bold), color = ZipSlideTheme.colors.accent)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ZipSlide always resolves the image named 1.jpg inside each zip as its thumbnail, preferring the root or shallowest depth. Never a generic file glyph.",
                        style = ZipSlideTheme.typography.body,
                        color = ZipSlideTheme.colors.textSecondary
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { showLicensesDialog = true }
                        .padding(horizontal = ZipSlideTheme.spacing.screenGutter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Open source licenses", style = ZipSlideTheme.typography.body)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ZipSlideTheme.colors.textTertiary)
                }
            }
        }
    }

    if (showClearCacheConfirm) {
        ConfirmDialog(
            title = "Clear Thumbnail Cache",
            message = "This will remove all cached cover thumbnails from disk. Thumbnails will be re-generated on the next scan.",
            confirmText = "Clear",
            isDestructive = true,
            onConfirm = {
                showClearCacheConfirm = false
                onEvent(SettingsEvent.ClearThumbnailCache)
                scope.launch { snackbarHostState.showSnackbar("Cache cleared") }
            },
            onDismiss = { showClearCacheConfirm = false }
        )
    }

    if (showRegenerateConfirm) {
        ConfirmDialog(
            title = "Regenerate All Thumbnails",
            message = "Re-inspect all zip archives and re-decode cover thumbnails at the current quality setting?",
            confirmText = "Regenerate",
            onConfirm = {
                showRegenerateConfirm = false
                onEvent(SettingsEvent.RegenerateThumbnails)
                scope.launch { snackbarHostState.showSnackbar("Regenerating thumbnails…") }
            },
            onDismiss = { showRegenerateConfirm = false }
        )
    }

    if (showLicensesDialog) {
        ConfirmDialog(
            title = "Open Source Licenses",
            message = "ZipSlide is built with Android Jetpack Compose, Material 3, Room Database, DataStore Preferences, and Kotlin Coroutines. Under Apache 2.0.",
            confirmText = "Close",
            onConfirm = { showLicensesDialog = false },
            onDismiss = { showLicensesDialog = false }
        )
    }
}

@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = ZipSlideTheme.spacing.screenGutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = ZipSlideTheme.typography.body, color = ZipSlideTheme.colors.textPrimary)
        content()
    }
}

@Composable
fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = ZipSlideTheme.spacing.screenGutter, vertical = ZipSlideTheme.spacing.s8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = ZipSlideTheme.spacing.s12)) {
            Text(text = label, style = ZipSlideTheme.typography.body, color = ZipSlideTheme.colors.textPrimary)
            if (description != null) {
                Text(
                    text = description,
                    style = ZipSlideTheme.typography.caption,
                    color = ZipSlideTheme.colors.textTertiary
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = ZipSlideTheme.colors.accent,
                uncheckedThumbColor = ZipSlideTheme.colors.textSecondary,
                uncheckedTrackColor = ZipSlideTheme.colors.surfaceHigh
            )
        )
    }
}

@Composable
fun SegmentedButtons(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(ZipSlideTheme.radii.full))
            .background(ZipSlideTheme.colors.surfaceHigh)
            .padding(2.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(ZipSlideTheme.radii.full))
                    .background(if (isSelected) ZipSlideTheme.colors.accentSoft else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    style = ZipSlideTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isSelected) ZipSlideTheme.colors.accent else ZipSlideTheme.colors.textSecondary
                )
            }
        }
    }
}
