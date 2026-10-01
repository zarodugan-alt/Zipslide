package com.example.ui.storage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.VolumeRepository
import com.example.data.model.ZipItem
import com.example.design.ZipSlideTheme
import com.example.ui.components.InfoRow

@Composable
fun VolumeDiagnosticsScreen(
    volumeRepository: VolumeRepository,
    zips: List<ZipItem>,
    onBack: () -> Unit
) {
    val volumes = volumeRepository.volumes.value

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
                    text = "Volume Diagnostics",
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
            contentPadding = PaddingValues(horizontal = ZipSlideTheme.spacing.screenGutter, vertical = ZipSlideTheme.spacing.s16)
        ) {
            item {
                Text(
                    text = "This screen displays low-level mount points, readable statuses, and access permissions for internal and removable storage devices.",
                    style = ZipSlideTheme.typography.body,
                    color = ZipSlideTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s16))
            }

            items(volumes, key = { it.id }) { vol ->
                val volZipCount = remember(zips, vol.id) { zips.count { it.volumeId == vol.id } }

                Card(
                    shape = RoundedCornerShape(ZipSlideTheme.radii.lg),
                    colors = CardDefaults.cardColors(containerColor = ZipSlideTheme.colors.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = ZipSlideTheme.spacing.s8)
                ) {
                    Column(modifier = Modifier.padding(ZipSlideTheme.spacing.s16)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = vol.name,
                                style = ZipSlideTheme.typography.titleM.copy(fontWeight = FontWeight.Bold),
                                color = ZipSlideTheme.colors.textPrimary
                            )
                            if (vol.isMounted && vol.readableDirect) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Ready",
                                        tint = ZipSlideTheme.colors.success,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Active",
                                        style = ZipSlideTheme.typography.caption,
                                        color = ZipSlideTheme.colors.success
                                    )
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = "Issue",
                                        tint = ZipSlideTheme.colors.warning,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Unmounted / Restricted",
                                        style = ZipSlideTheme.typography.caption,
                                        color = ZipSlideTheme.colors.warning
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s12))

                        InfoRow(label = "Mount Path", value = vol.rootFile?.absolutePath ?: "N/A")
                        InfoRow(label = "Volume ID", value = vol.id)
                        InfoRow(
                            label = "Readable (Direct File)",
                            value = if (vol.readableDirect) "Yes" else "No",
                            valueColor = if (vol.readableDirect) ZipSlideTheme.colors.success else ZipSlideTheme.colors.error
                        )
                        InfoRow(
                            label = "Permission Backend",
                            value = if (vol.readableDirect) "Direct (All Files)" else "SAF (Fallback)",
                            valueColor = ZipSlideTheme.colors.accent
                        )
                        InfoRow(label = "Discovered Zips", value = "$volZipCount zips")
                        InfoRow(label = "Storage Type", value = if (vol.isSdCard) "Removable MicroSD" else if (vol.isUsb) "USB OTG" else "Internal Shared")
                    }
                }
            }
        }
    }
}
