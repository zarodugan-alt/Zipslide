package com.example.ui.storage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.VolumeRepository
import com.example.data.model.ZipItem
import com.example.design.ZipSlideTheme
import com.example.ui.components.SectionHeader
import com.example.ui.components.formatBytes

@Composable
fun StorageOverviewScreen(
    volumeRepository: VolumeRepository,
    zips: List<ZipItem>,
    onBack: () -> Unit
) {
    val volumes = volumeRepository.volumes.value

    // Group zips by top level folders
    val folderStats = remember(zips) {
        zips.groupBy { it.parentFolder.ifEmpty { "Root" } }
            .map { (folder, items) ->
                Triple(folder, items.size, items.sumOf { it.size })
            }
            .sortedByDescending { it.third }
    }

    BackHandler {
        onBack()
    }

    Scaffold(
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
                    text = "Storage Overview",
                    style = ZipSlideTheme.typography.titleM,
                    color = ZipSlideTheme.colors.textPrimary
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = ZipSlideTheme.spacing.screenGutter,
                vertical = ZipSlideTheme.spacing.s8
            )
        ) {
            item {
                SectionHeader(title = "Detected Volumes", modifier = Modifier.padding(horizontal = 0.dp))
            }

            items(volumes, key = { it.id }) { vol ->
                val volZips = remember(zips, vol.id) { zips.filter { it.volumeId == vol.id } }
                val totalZipSize = remember(volZips) { volZips.sumOf { it.size } }

                Card(
                    shape = RoundedCornerShape(ZipSlideTheme.radii.lg),
                    colors = CardDefaults.cardColors(containerColor = ZipSlideTheme.colors.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = ZipSlideTheme.spacing.s8)
                ) {
                    Column(modifier = Modifier.padding(ZipSlideTheme.spacing.s16)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val icon = when {
                                vol.isSdCard -> Icons.Default.SdCard
                                vol.isUsb -> Icons.Default.Usb
                                else -> Icons.Default.Storage
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = ZipSlideTheme.colors.accent,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(ZipSlideTheme.spacing.s12))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = vol.name,
                                    style = ZipSlideTheme.typography.titleM,
                                    color = ZipSlideTheme.colors.textPrimary
                                )
                                Text(
                                    text = vol.rootFile?.absolutePath ?: "Root",
                                    style = ZipSlideTheme.typography.caption,
                                    color = ZipSlideTheme.colors.textTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s16))

                        // Capacity bar
                        val usedPercent = if (vol.totalBytes > 0) {
                            (vol.usedBytes.toFloat() / vol.totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        LinearProgressIndicator(
                            progress = { usedPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = ZipSlideTheme.colors.accent,
                            trackColor = ZipSlideTheme.colors.surfaceHigh,
                        )

                        Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s8))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${formatBytes(vol.usedBytes)} used of ${formatBytes(vol.totalBytes)}",
                                style = ZipSlideTheme.typography.caption,
                                color = ZipSlideTheme.colors.textSecondary
                            )
                            Text(
                                text = "${volZips.size} zips · ${formatBytes(totalZipSize)}",
                                style = ZipSlideTheme.typography.caption,
                                color = ZipSlideTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s16))
                SectionHeader(title = "Folders with Slideshows", modifier = Modifier.padding(horizontal = 0.dp))
            }

            items(folderStats) { (folder, count, size) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = ZipSlideTheme.colors.accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(ZipSlideTheme.spacing.s16))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.substringAfterLast('/'),
                            style = ZipSlideTheme.typography.label.copy(fontWeight = FontWeight.SemiBold),
                            color = ZipSlideTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = folder,
                            style = ZipSlideTheme.typography.caption,
                            color = ZipSlideTheme.colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "$count zips · ${formatBytes(size)}",
                        style = ZipSlideTheme.typography.caption,
                        color = ZipSlideTheme.colors.textSecondary
                    )
                }
            }
        }
    }
}
