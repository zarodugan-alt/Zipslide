package com.example.ui.contents

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ZipRepository
import com.example.data.model.ZipEntryItem
import com.example.data.model.ZipItem
import com.example.design.ZipSlideTheme
import com.example.ui.components.ProgressSheet
import com.example.ui.components.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ZipContentsScreen(
    zipPath: String,
    zipRepository: ZipRepository,
    onNavigateToSlideshow: (zipPath: String, frameIndex: Int) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var entries by remember { mutableStateOf<List<ZipEntryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var zipItem by remember { mutableStateOf<ZipItem?>(null) }
    var expandOtherFiles by remember { mutableStateOf(false) }

    // Progress sheet for extraction
    var progressTitle by remember { mutableStateOf<String?>(null) }
    var progressCurrent by remember { mutableStateOf(0) }
    var progressTotal by remember { mutableStateOf(0) }
    var progressCurrentName by remember { mutableStateOf("") }

    // Thumbnails memory cache for content items
    val thumbs = remember { mutableStateMapOf<String, androidx.compose.ui.graphics.ImageBitmap>() }

    LaunchedEffect(zipPath) {
        withContext(Dispatchers.IO) {
            val fileName = zipPath.substringAfterLast(File.separatorChar)
            val file = File(zipPath)
            val item = ZipItem(
                path = zipPath,
                name = fileName,
                size = if (file.exists()) file.length() else 0L,
                lastModified = if (file.exists()) file.lastModified() else 0L,
                volumeId = "internal",
                volumeName = "Storage",
                isSaf = zipPath.startsWith("content://")
            )
            zipItem = item
            entries = zipRepository.getZipEntries(item)
            isLoading = false
        }
    }

    val imageEntries = remember(entries) { entries.filter { it.isImage } }
    val otherEntries = remember(entries) { entries.filter { !it.isImage } }

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
                    text = zipPath.substringAfterLast(File.separatorChar),
                    style = ZipSlideTheme.typography.titleM,
                    color = ZipSlideTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ZipSlideTheme.colors.accent)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = ZipSlideTheme.spacing.screenGutter,
                        end = ZipSlideTheme.spacing.screenGutter,
                        bottom = ZipSlideTheme.spacing.s48
                    )
                ) {
                    // Header Block
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = ZipSlideTheme.spacing.s16)
                        ) {
                            val totalSize = zipItem?.size ?: 0L
                            Text(
                                text = "${imageEntries.size} images · ${formatBytes(totalSize)}",
                                style = ZipSlideTheme.typography.body,
                                color = ZipSlideTheme.colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s16))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ZipSlideTheme.spacing.s12)
                            ) {
                                Button(
                                    onClick = {
                                        if (imageEntries.isNotEmpty()) {
                                            onNavigateToSlideshow(zipPath, 0)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ZipSlideTheme.colors.accent,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(ZipSlideTheme.radii.full),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(ZipSlideTheme.spacing.s8))
                                    Text(
                                        text = "Play slideshow",
                                        style = ZipSlideTheme.typography.label.copy(fontWeight = FontWeight.Bold)
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        val item = zipItem ?: return@OutlinedButton
                                        scope.launch {
                                            progressTitle = "Extracting all images…"
                                            progressCurrent = 0
                                            progressTotal = imageEntries.size
                                            val result = zipRepository.extractImages(item) { cur, tot, name ->
                                                progressCurrent = cur
                                                progressTotal = tot
                                                progressCurrentName = name
                                            }
                                            progressTitle = null
                                            if (result.isSuccess) {
                                                snackbarHostState.showSnackbar("Extracted to ${result.getOrNull()?.name}")
                                            } else {
                                                snackbarHostState.showSnackbar("Extraction failed: ${result.exceptionOrNull()?.message}")
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(ZipSlideTheme.radii.full),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = ZipSlideTheme.colors.accent, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(ZipSlideTheme.spacing.s8))
                                    Text(
                                        text = "Extract all",
                                        style = ZipSlideTheme.typography.label.copy(fontWeight = FontWeight.Bold),
                                        color = ZipSlideTheme.colors.accent
                                    )
                                }
                            }
                        }
                    }

                    // Image Entries List
                    itemsIndexed(imageEntries, key = { _, item -> item.entryPath }) { index, item ->
                        // Load thumbnail asynchronously
                        LaunchedEffect(item.entryPath) {
                            if (!thumbs.containsKey(item.entryPath)) {
                                withContext(Dispatchers.IO) {
                                    val bmp = zipRepository.loadFrameBitmap(zipPath, item.entryPath, 128)
                                    if (bmp != null) {
                                        thumbs[item.entryPath] = bmp.asImageBitmap()
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clip(RoundedCornerShape(ZipSlideTheme.radii.md))
                                .clickable { onNavigateToSlideshow(zipPath, index) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(ZipSlideTheme.radii.sm))
                                    .background(ZipSlideTheme.colors.surfaceElev),
                                contentAlignment = Alignment.Center
                            ) {
                                val thumbBmp = thumbs[item.entryPath]
                                if (thumbBmp != null) {
                                    Image(
                                        bitmap = thumbBmp,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.InsertDriveFile,
                                        contentDescription = null,
                                        tint = ZipSlideTheme.colors.textTertiary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(ZipSlideTheme.spacing.s16))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.basename,
                                    style = ZipSlideTheme.typography.label.copy(fontWeight = FontWeight.SemiBold),
                                    color = ZipSlideTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (item.folderPath.isNotEmpty()) "${item.folderPath} · ${formatBytes(item.size)}" else formatBytes(item.size),
                                    style = ZipSlideTheme.typography.caption,
                                    color = ZipSlideTheme.colors.textSecondary
                                )
                            }
                        }
                    }

                    // Non-image entries collapsed section
                    if (otherEntries.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s16))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(ZipSlideTheme.radii.md))
                                    .clickable { expandOtherFiles = !expandOtherFiles }
                                    .padding(horizontal = ZipSlideTheme.spacing.s8),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${otherEntries.size} other files",
                                    style = ZipSlideTheme.typography.bodyM,
                                    color = ZipSlideTheme.colors.textSecondary
                                )
                                Icon(
                                    imageVector = if (expandOtherFiles) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = ZipSlideTheme.colors.textSecondary
                                )
                            }
                        }

                        if (expandOtherFiles) {
                            itemsIndexed(otherEntries, key = { _, item -> item.entryPath }) { _, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .padding(horizontal = ZipSlideTheme.spacing.s8, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.InsertDriveFile,
                                        contentDescription = null,
                                        tint = ZipSlideTheme.colors.textTertiary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(ZipSlideTheme.spacing.s12))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.basename,
                                            style = ZipSlideTheme.typography.label,
                                            color = ZipSlideTheme.colors.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = formatBytes(item.size),
                                            style = ZipSlideTheme.typography.caption,
                                            color = ZipSlideTheme.colors.textTertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Progress sheet for extraction
            if (progressTitle != null) {
                ProgressSheet(
                    title = progressTitle!!,
                    current = progressCurrent,
                    total = progressTotal,
                    currentFileName = progressCurrentName,
                    onCancel = { progressTitle = null }
                )
            }
        }
    }
}
