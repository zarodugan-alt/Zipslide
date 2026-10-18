package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ZipItem
import com.example.design.DesignTokens
import com.example.design.ZipSlideTheme

@Composable
fun ZipCard(
    zipItem: ZipItem,
    showMultiVolumeBadge: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    thumbnailPx: Int,
    loadThumbnail: suspend () -> Bitmap?,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1.0f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 900f),
        label = "cardScale"
    )

    // The browser provides the repository thumbnail pipeline. The effect is cancelled when the
    // card leaves composition, while the repository deduplicates concurrent requests by cache key.
    var bitmap by remember(zipItem.path, zipItem.lastModified, thumbnailPx) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    var isLoadingThumb by remember(zipItem.path, zipItem.lastModified, thumbnailPx) { mutableStateOf(true) }

    LaunchedEffect(zipItem.path, zipItem.lastModified, zipItem.size, thumbnailPx) {
        bitmap = loadThumbnail()?.asImageBitmap()
        isLoadingThumb = false
    }

    val formattedSize = remember(zipItem.size) { formatBytes(zipItem.size) }
    val accessibleDescription = buildString {
        append(zipItem.name)
        append(", ")
        append(zipItem.imageCount)
        append(" images, ")
        append(formattedSize)
        if (zipItem.isFavorite) append(", favorite")
        if (!zipItem.hasCover) append(", missing 1.jpg cover")
        if (!zipItem.isMounted) append(", SD card not mounted")
    }

    val cardBorder = if (isSelected) {
        Modifier.border(2.dp, ZipSlideTheme.colors.accent, RoundedCornerShape(ZipSlideTheme.radii.lg))
    } else {
        Modifier.border(1.dp, ZipSlideTheme.colors.outline, RoundedCornerShape(ZipSlideTheme.radii.lg))
    }

    val cardAlpha = if (!zipItem.isMounted) 0.40f else 1.0f

    Box(
        modifier = modifier
            .aspectRatio(4f / 5f)
            .scale(scale)
            .then(cardBorder)
            .clip(RoundedCornerShape(ZipSlideTheme.radii.lg))
            .background(ZipSlideTheme.colors.surface)
            .alpha(cardAlpha)
            .semantics { contentDescription = accessibleDescription }
            .testTag("zip_card_${zipItem.name}")
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                    onTap = {
                        onClick()
                    }
                )
            }
    ) {
        // Thumbnail or Placeholder
        if (isLoadingThumb) {
            ShimmerBlock(modifier = Modifier.fillMaxSize(), radius = ZipSlideTheme.radii.lg)
        } else if (bitmap != null) {
            // Covers fade in rather than popping, and lift very slightly on press.
            val coverAlpha = remember(zipItem.path) { Animatable(0f) }
            LaunchedEffect(zipItem.path) {
                coverAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(DesignTokens.Motion.ExpressiveDurationMs, easing = DesignTokens.Motion.standardEasing)
                )
            }
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = coverAlpha.value
                        val zoom = if (isPressed) 1.03f else 1f
                        scaleX = zoom
                        scaleY = zoom
                    }
            )
        } else {
            // Placeholder glyph for zero-images, encrypted, corrupt, or missing
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ZipSlideTheme.colors.surfaceElev),
                contentAlignment = Alignment.Center
            ) {
                val icon = when {
                    zipItem.isEncrypted -> Icons.Default.Lock
                    zipItem.isCorrupt -> Icons.Default.BrokenImage
                    else -> Icons.Default.BrokenImage
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ZipSlideTheme.colors.textTertiary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Bottom gradient scrim: transparent -> rgba(0,0,0,0.75) over bottom 45%
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.45f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xBF000000)
                        )
                    )
                )
        )

        // Bottom Text Content: Filename & Metadata
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(ZipSlideTheme.spacing.cardInnerPadding)
        ) {
            Text(
                text = zipItem.name,
                style = ZipSlideTheme.typography.label.copy(fontWeight = FontWeight.SemiBold),
                color = ZipSlideTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!zipItem.isMounted) {
                    Text(
                        text = "SD card not mounted",
                        style = ZipSlideTheme.typography.caption,
                        color = ZipSlideTheme.colors.warning
                    )
                } else {
                    Text(
                        text = "${zipItem.imageCount} frames · $formattedSize",
                        style = ZipSlideTheme.typography.caption,
                        color = ZipSlideTheme.colors.textSecondary
                    )
                }
            }
        }

        // Resume rail: how far through this archive the viewer already is.
        if (zipItem.lastFrameIndex > 0 && zipItem.imageCount > 1) {
            val progress = (zipItem.lastFrameIndex.toFloat() / (zipItem.imageCount - 1).toFloat()).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.18f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(ZipSlideTheme.colors.accent)
                )
            }
        }

        // Top-left: Star badge if favorited + Volume badge
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(ZipSlideTheme.spacing.s8),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (zipItem.isFavorite) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Favorited",
                    tint = ZipSlideTheme.colors.accent,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = ZipSlideTheme.spacing.s4)
                )
            }
            if (showMultiVolumeBadge) {
                VolumeBadge(volumeId = zipItem.volumeId)
            }
        }

        // Top-right: Selection indicator or Warning dot if 1.jpg missing
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(ZipSlideTheme.spacing.s8)
        ) {
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) ZipSlideTheme.colors.accent else ZipSlideTheme.colors.surfaceHigh)
                        .border(1.dp, ZipSlideTheme.colors.outline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else if (zipItem.watchCount > 0) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Already viewed",
                        tint = ZipSlideTheme.colors.accent,
                        modifier = Modifier.size(13.dp)
                    )
                }
            } else if (!zipItem.hasCover && zipItem.imageCount > 0) {
                // 10dp warning dot indicating missing 1.jpg cover
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(ZipSlideTheme.colors.warning)
                )
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val num = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format("%.1f %s", num, units[digitGroups])
}
