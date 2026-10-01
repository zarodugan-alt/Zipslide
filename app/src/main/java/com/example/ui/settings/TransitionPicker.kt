package com.example.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.SlideTransition
import com.example.design.DesignTokens
import com.example.design.ZipSlideTheme
import com.example.ui.slideshow.applyTransition

/**
 * Picks a slideshow transition and plays it, on loop, on two stand-in frames.
 *
 * The preview runs the exact production maths from `applyTransition`, so what is shown here is
 * what the viewer will do — there is no second, drifting implementation to keep in sync.
 */
@Composable
fun TransitionPicker(
    selected: SlideTransition,
    durationMs: Int,
    onSelect: (SlideTransition) -> Unit,
    modifier: Modifier = Modifier
) {
    // Resolve "Surprise me" to something drawable, re-rolled on every loop of the preview.
    val previewStyle = remember(selected) {
        if (selected.isRandom) SlideTransition.randomConcrete() else selected
    }

    val loop = rememberInfiniteTransition(label = "transitionPreview")
    val holdMs = 900
    val progress by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = durationMs + holdMs
                0f at 0 using DesignTokens.Motion.standardEasing
                1f at durationMs using LinearEasing
                1f at durationMs + holdMs
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "transitionProgress"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZipSlideTheme.spacing.screenGutter)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(ZipSlideTheme.radii.md))
                .background(Color.Black)
        ) {
            // Frame A leaves (pager offset 0 → 1), frame B arrives (offset -1 → 0).
            PreviewFrame(style = previewStyle, pageOffset = progress, index = 1)
            PreviewFrame(style = previewStyle, pageOffset = progress - 1f, index = 2)

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(ZipSlideTheme.spacing.s8)
                    .clip(RoundedCornerShape(ZipSlideTheme.radii.full))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = ZipSlideTheme.spacing.s8, vertical = 2.dp)
            ) {
                androidx.compose.material3.Text(
                    text = if (selected.isRandom) "${selected.label} · ${previewStyle.label}" else selected.label,
                    style = ZipSlideTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
            }
        }

        Spacer(Modifier.height(ZipSlideTheme.spacing.s12))

        // One shared horizontal scroll across three rows keeps 20+ styles reachable in a thumb
        // sweep instead of an endless single line.
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .horizontalScroll(scroll)
                .padding(horizontal = ZipSlideTheme.spacing.screenGutter),
            verticalArrangement = Arrangement.spacedBy(ZipSlideTheme.spacing.s8)
        ) {
            val rows = SlideTransition.entries.chunked((SlideTransition.entries.size + 2) / 3)
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(ZipSlideTheme.spacing.s8)) {
                    row.forEach { option ->
                        TransitionChip(
                            label = option.label,
                            isSelected = option == selected,
                            onClick = { onSelect(option) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewFrame(style: SlideTransition, pageOffset: Float, index: Int) {
    val accent = ZipSlideTheme.colors.accent
    val gradient = if (index == 1) {
        Brush.linearGradient(listOf(Color(0xFF2A2A33), Color(0xFF14141A)))
    } else {
        Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), Color(0xFF3A2E18)))
    }

    Box(
        // Outer layer reproduces the pager's placement; the inner layer is the transition itself.
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationX = -pageOffset * size.width }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { applyTransition(style, pageOffset) }
                .background(gradient),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Text(
                text = index.toString(),
                style = ZipSlideTheme.typography.display,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun TransitionChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(ZipSlideTheme.radii.full))
            .background(if (isSelected) ZipSlideTheme.colors.accent else ZipSlideTheme.colors.surfaceElev)
            .clickable { onClick() }
            .padding(horizontal = ZipSlideTheme.spacing.s12, vertical = ZipSlideTheme.spacing.s8)
    ) {
        androidx.compose.material3.Text(
            text = label,
            style = ZipSlideTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = if (isSelected) Color.Black else ZipSlideTheme.colors.textSecondary
        )
    }
}
