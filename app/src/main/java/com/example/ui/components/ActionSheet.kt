package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ZipItem
import com.example.design.ZipSlideTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface ZipActions {
    fun onPlaySlideshow(zip: ZipItem)
    fun onOpenContents(zip: ZipItem)
    fun onToggleFavorite(zip: ZipItem)
    fun onRename(zip: ZipItem)
    fun onCopyTo(zip: ZipItem)
    fun onMoveTo(zip: ZipItem)
    fun onShare(zip: ZipItem)
    fun onExtractImages(zip: ZipItem)
    fun onInfo(zip: ZipItem)
    fun onDelete(zip: ZipItem)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(
    zipItem: ZipItem?,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    actions: ZipActions
) {
    if (zipItem == null) return

    var thumbBitmap by remember(zipItem.thumbnailPath) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(zipItem.thumbnailPath) {
        val path = zipItem.thumbnailPath
        if (path != null && File(path).exists()) {
            withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory.decodeFile(path)?.asImageBitmap()
                }.getOrNull()?.let { thumbBitmap = it }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = ZipSlideTheme.colors.surface,
        shape = RoundedCornerShape(
            topStart = ZipSlideTheme.radii.xl,
            topEnd = ZipSlideTheme.radii.xl
        ),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = ZipSlideTheme.spacing.s12, bottom = ZipSlideTheme.spacing.s8)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(ZipSlideTheme.radii.full))
                    .background(ZipSlideTheme.colors.outline)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = ZipSlideTheme.spacing.sectionGap)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: 44dp thumbnail (radius sm) + filename + meta
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZipSlideTheme.spacing.screenGutter, vertical = ZipSlideTheme.spacing.s12),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(ZipSlideTheme.radii.sm))
                        .background(ZipSlideTheme.colors.surfaceElev),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbBitmap != null) {
                        Image(
                            bitmap = thumbBitmap!!,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = ZipSlideTheme.colors.textTertiary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(ZipSlideTheme.spacing.s12))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = zipItem.name,
                        style = ZipSlideTheme.typography.titleM,
                        color = ZipSlideTheme.colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${zipItem.imageCount} images · ${formatBytes(zipItem.size)} · ${zipItem.volumeName}",
                        style = ZipSlideTheme.typography.caption,
                        color = ZipSlideTheme.colors.textSecondary
                    )
                    if (!zipItem.hasCover && zipItem.imageCount > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "⚠ No 1.jpg — using first image",
                            style = ZipSlideTheme.typography.caption,
                            color = ZipSlideTheme.colors.warning
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s8))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ZipSlideTheme.colors.outline)
            )

            // 10 Action Rows
            ActionRow(
                icon = Icons.Default.PlayArrow,
                label = if (zipItem.lastFrameIndex > 0) "Resume slideshow (frame ${zipItem.lastFrameIndex + 1})" else "Play slideshow",
                onClick = {
                    onDismissRequest()
                    actions.onPlaySlideshow(zipItem)
                },
                testTag = "action_play"
            )

            ActionRow(
                icon = Icons.Default.Folder,
                label = "Open contents",
                onClick = {
                    onDismissRequest()
                    actions.onOpenContents(zipItem)
                },
                testTag = "action_contents"
            )

            ActionRow(
                icon = if (zipItem.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                iconTint = if (zipItem.isFavorite) ZipSlideTheme.colors.accent else ZipSlideTheme.colors.textSecondary,
                label = if (zipItem.isFavorite) "Remove from favorites" else "Add to favorites",
                onClick = {
                    actions.onToggleFavorite(zipItem)
                },
                testTag = "action_favorite"
            )

            ActionRow(
                icon = Icons.Default.Edit,
                label = "Rename",
                onClick = {
                    onDismissRequest()
                    actions.onRename(zipItem)
                },
                testTag = "action_rename"
            )

            ActionRow(
                icon = Icons.Default.FileCopy,
                label = "Copy to…",
                onClick = {
                    onDismissRequest()
                    actions.onCopyTo(zipItem)
                },
                testTag = "action_copy"
            )

            ActionRow(
                icon = Icons.Default.DriveFileMove,
                label = "Move to…",
                onClick = {
                    onDismissRequest()
                    actions.onMoveTo(zipItem)
                },
                testTag = "action_move"
            )

            ActionRow(
                icon = Icons.Default.Share,
                label = "Share",
                onClick = {
                    onDismissRequest()
                    actions.onShare(zipItem)
                },
                testTag = "action_share"
            )

            ActionRow(
                icon = Icons.Default.FileDownload,
                label = "Extract images",
                onClick = {
                    onDismissRequest()
                    actions.onExtractImages(zipItem)
                },
                testTag = "action_extract"
            )

            ActionRow(
                icon = Icons.Default.Info,
                label = "Info",
                onClick = {
                    onDismissRequest()
                    actions.onInfo(zipItem)
                },
                testTag = "action_info"
            )

            ActionRow(
                icon = Icons.Default.Delete,
                iconTint = ZipSlideTheme.colors.error,
                label = "Delete",
                textColor = ZipSlideTheme.colors.error,
                onClick = {
                    onDismissRequest()
                    actions.onDelete(zipItem)
                },
                testTag = "action_delete"
            )
        }
    }
}

@Composable
fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    iconTint: androidx.compose.ui.graphics.Color = ZipSlideTheme.colors.textSecondary,
    textColor: androidx.compose.ui.graphics.Color = ZipSlideTheme.colors.textPrimary,
    testTag: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = ZipSlideTheme.spacing.screenGutter)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(ZipSlideTheme.spacing.s20))
        Text(
            text = label,
            style = ZipSlideTheme.typography.bodyM,
            color = textColor
        )
    }
}
