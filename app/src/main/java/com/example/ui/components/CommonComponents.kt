package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.BrowserFilter
import com.example.design.ZipSlideTheme

@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    radius: Dp = ZipSlideTheme.radii.md
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(ZipSlideTheme.colors.surfaceHigh.copy(alpha = alpha))
    )
}

@Composable
fun VolumeBadge(
    volumeId: String,
    modifier: Modifier = Modifier
) {
    val label = when {
        volumeId.contains("sd", ignoreCase = true) -> "SD"
        volumeId.contains("usb", ignoreCase = true) -> "USB"
        else -> "I"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(ZipSlideTheme.radii.full))
            .background(ZipSlideTheme.colors.surfaceHigh)
            .padding(horizontal = ZipSlideTheme.spacing.s8, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = ZipSlideTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = ZipSlideTheme.colors.textSecondary
        )
    }
}

@Composable
fun FilterChipRow(
    selectedFilter: BrowserFilter,
    onFilterSelected: (BrowserFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = ZipSlideTheme.spacing.screenGutter),
        horizontalArrangement = Arrangement.spacedBy(ZipSlideTheme.spacing.s8)
    ) {
        FilterChip(
            label = "All",
            isSelected = selectedFilter == BrowserFilter.ALL,
            onClick = { onFilterSelected(BrowserFilter.ALL) },
            testTag = "filter_chip_all"
        )
        FilterChip(
            label = "Favorites",
            isSelected = selectedFilter == BrowserFilter.FAVORITES,
            onClick = { onFilterSelected(BrowserFilter.FAVORITES) },
            testTag = "filter_chip_favorites"
        )
        FilterChip(
            label = "⚠ No 1.jpg",
            isSelected = selectedFilter == BrowserFilter.MISSING_COVER,
            onClick = { onFilterSelected(BrowserFilter.MISSING_COVER) },
            testTag = "filter_chip_missing_cover"
        )
    }
}

@Composable
fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String = ""
) {
    val bgColor = if (isSelected) ZipSlideTheme.colors.accentSoft else ZipSlideTheme.colors.surfaceHigh
    val textColor = if (isSelected) ZipSlideTheme.colors.accent else ZipSlideTheme.colors.textSecondary
    val borderModifier = if (isSelected) {
        Modifier.border(1.dp, ZipSlideTheme.colors.accent, RoundedCornerShape(ZipSlideTheme.radii.full))
    } else {
        Modifier
    }

    Surface(
        modifier = Modifier
            .then(borderModifier)
            .clip(RoundedCornerShape(ZipSlideTheme.radii.full))
            .clickable(onClick = onClick)
            .testTag(testTag),
        color = bgColor,
        shape = RoundedCornerShape(ZipSlideTheme.radii.full)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = ZipSlideTheme.spacing.s16, vertical = ZipSlideTheme.spacing.s8),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = ZipSlideTheme.typography.label,
                color = textColor
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    buttonText: String? = null,
    onButtonClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ZipSlideTheme.spacing.sectionGap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(ZipSlideTheme.colors.surfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ZipSlideTheme.colors.accent,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s20))
        }

        Text(
            text = title,
            style = ZipSlideTheme.typography.titleM,
            color = ZipSlideTheme.colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s8))
        Text(
            text = body,
            style = ZipSlideTheme.typography.body,
            color = ZipSlideTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )

        if (buttonText != null && onButtonClick != null) {
            Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s24))
            Button(
                onClick = onButtonClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ZipSlideTheme.colors.accent,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(ZipSlideTheme.radii.full),
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    text = buttonText,
                    style = ZipSlideTheme.typography.label.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title.uppercase(),
        style = ZipSlideTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
        color = ZipSlideTheme.colors.textTertiary,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = ZipSlideTheme.spacing.screenGutter,
                end = ZipSlideTheme.spacing.screenGutter,
                top = ZipSlideTheme.spacing.sectionGap,
                bottom = ZipSlideTheme.spacing.s8
            )
    )
}
