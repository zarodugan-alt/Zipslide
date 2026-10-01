package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ZipItem
import com.example.design.ZipSlideTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InfoDialog(
    zipItem: ZipItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val formattedModified = remember(zipItem.lastModified) { dateFormat.format(Date(zipItem.lastModified)) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(ZipSlideTheme.radii.xl),
            colors = CardDefaults.cardColors(containerColor = ZipSlideTheme.colors.surfaceElev),
            modifier = Modifier
                .width(320.dp)
                .padding(ZipSlideTheme.spacing.s16)
        ) {
            Column(modifier = Modifier.padding(ZipSlideTheme.spacing.s20)) {
                Text(
                    text = zipItem.name,
                    style = ZipSlideTheme.typography.titleM,
                    color = ZipSlideTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s4))
                Text(
                    text = zipItem.path,
                    style = ZipSlideTheme.typography.caption,
                    color = ZipSlideTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s16))

                InfoRow(label = "Total Size", value = formatBytes(zipItem.size))
                InfoRow(label = "Images", value = "${zipItem.imageCount} frames")
                InfoRow(
                    label = "1.jpg cover",
                    value = if (zipItem.hasCover) "✓ Present" else "✕ Missing (fallback)",
                    valueColor = if (zipItem.hasCover) ZipSlideTheme.colors.success else ZipSlideTheme.colors.warning
                )
                InfoRow(label = "Volume", value = zipItem.volumeName)
                InfoRow(label = "Modified", value = formattedModified)
                InfoRow(
                    label = "Permissions",
                    value = if (zipItem.isReadOnly) "Read-Only" else "Read / Write",
                    valueColor = if (zipItem.isReadOnly) ZipSlideTheme.colors.warning else ZipSlideTheme.colors.textSecondary
                )

                Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s20))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("Zip Path", zipItem.path))
                    }) {
                        Text(
                            text = "Copy path",
                            style = ZipSlideTheme.typography.label,
                            color = ZipSlideTheme.colors.accent
                        )
                    }

                    Spacer(modifier = Modifier.width(ZipSlideTheme.spacing.s8))

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ZipSlideTheme.colors.accent,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(ZipSlideTheme.radii.full)
                    ) {
                        Text(text = "Done", style = ZipSlideTheme.typography.label.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = ZipSlideTheme.colors.textPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = ZipSlideTheme.typography.caption, color = ZipSlideTheme.colors.textSecondary)
        Text(text = value, style = ZipSlideTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold), color = valueColor)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Confirm",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(ZipSlideTheme.radii.xl),
            colors = CardDefaults.cardColors(containerColor = ZipSlideTheme.colors.surfaceElev),
            modifier = Modifier.padding(ZipSlideTheme.spacing.s16)
        ) {
            Column(modifier = Modifier.padding(ZipSlideTheme.spacing.s20)) {
                Text(
                    text = title,
                    style = ZipSlideTheme.typography.titleM,
                    color = if (isDestructive) ZipSlideTheme.colors.error else ZipSlideTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s8))
                Text(
                    text = message,
                    style = ZipSlideTheme.typography.body,
                    color = ZipSlideTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s20))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = "Cancel", color = ZipSlideTheme.colors.textSecondary)
                    }
                    Spacer(modifier = Modifier.width(ZipSlideTheme.spacing.s8))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDestructive) ZipSlideTheme.colors.error else ZipSlideTheme.colors.accent,
                            contentColor = if (isDestructive) Color.White else Color.Black
                        ),
                        shape = RoundedCornerShape(ZipSlideTheme.radii.full)
                    ) {
                        Text(text = confirmText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialBase = remember(currentName) { currentName.removeSuffix(".zip") }
    var text by remember { mutableStateOf(initialBase) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(ZipSlideTheme.radii.xl),
            colors = CardDefaults.cardColors(containerColor = ZipSlideTheme.colors.surfaceElev),
            modifier = Modifier.padding(ZipSlideTheme.spacing.s16)
        ) {
            Column(modifier = Modifier.padding(ZipSlideTheme.spacing.s20)) {
                Text(
                    text = "Rename zip",
                    style = ZipSlideTheme.typography.titleM,
                    color = ZipSlideTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s12))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        errorMessage = null
                    },
                    label = { Text("Filename") },
                    suffix = { Text(".zip") },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = {
                        if (errorMessage != null) {
                            Text(errorMessage!!, color = ZipSlideTheme.colors.error)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZipSlideTheme.colors.accent,
                        unfocusedBorderColor = ZipSlideTheme.colors.outline,
                        focusedTextColor = ZipSlideTheme.colors.textPrimary,
                        unfocusedTextColor = ZipSlideTheme.colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s16))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = "Cancel", color = ZipSlideTheme.colors.textSecondary)
                    }
                    Spacer(modifier = Modifier.width(ZipSlideTheme.spacing.s8))
                    Button(
                        onClick = {
                            val trimmed = text.trim()
                            if (trimmed.isEmpty()) {
                                errorMessage = "Filename cannot be empty"
                            } else {
                                onConfirm(trimmed)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ZipSlideTheme.colors.accent,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(ZipSlideTheme.radii.full)
                    ) {
                        Text(text = "Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressSheet(
    title: String,
    current: Int,
    total: Int,
    currentFileName: String,
    onCancel: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onCancel,
        containerColor = ZipSlideTheme.colors.surface,
        shape = RoundedCornerShape(topStart = ZipSlideTheme.radii.xl, topEnd = ZipSlideTheme.radii.xl)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZipSlideTheme.spacing.screenGutter)
                .padding(bottom = ZipSlideTheme.spacing.sectionGap)
        ) {
            Text(
                text = title,
                style = ZipSlideTheme.typography.titleM,
                color = ZipSlideTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s8))
            Text(
                text = if (total > 0) "$current of $total frames" else "Working…",
                style = ZipSlideTheme.typography.bodyM,
                color = ZipSlideTheme.colors.textSecondary
            )
            Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s4))
            Text(
                text = currentFileName,
                style = ZipSlideTheme.typography.caption,
                color = ZipSlideTheme.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s16))

            val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = ZipSlideTheme.colors.accent,
                trackColor = ZipSlideTheme.colors.surfaceHigh,
            )

            Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s20))

            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ZipSlideTheme.colors.surfaceHigh,
                    contentColor = ZipSlideTheme.colors.textPrimary
                ),
                shape = RoundedCornerShape(ZipSlideTheme.radii.full),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(text = "Cancel", style = ZipSlideTheme.typography.label)
            }
        }
    }
}
