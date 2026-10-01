package com.example.ui.slideshow

import android.app.Activity
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AppSettings
import com.example.data.ZipRepository
import com.example.data.toUserMessage
import com.example.data.model.ZipEntryItem
import com.example.data.model.ZipItem
import com.example.design.ZipSlideTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

@Composable
fun SlideshowScreen(
    zipPath: String,
    initialFrameIndex: Int = 0,
    settings: AppSettings,
    zipRepository: ZipRepository,
    onBack: () -> Unit,
    state: SlideshowState? = null,
    onEvent: (SlideshowEvent) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? Activity

    var localZipItem by remember { mutableStateOf<ZipItem?>(null) }
    var localEntries by remember { mutableStateOf<List<ZipEntryItem>>(emptyList()) }
    var localLoadingEntries by remember { mutableStateOf(true) }
    val zipItem = state?.zip ?: localZipItem
    val entries = state?.images ?: localEntries
    val isLoadingEntries = state?.loading ?: localLoadingEntries
    val effectiveSettings = state?.settings ?: settings

    // Playback controls are transient UI state; their persisted defaults arrive in state.
    var isPlaying by remember { mutableStateOf(true) }
    var isShuffle by remember(effectiveSettings.slideShuffle) { mutableStateOf(effectiveSettings.slideShuffle) }
    var isLoop by remember(effectiveSettings.slideLoop) { mutableStateOf(effectiveSettings.slideLoop) }
    var fitToScreen by remember(effectiveSettings.slideFitToScreen) { mutableStateOf(effectiveSettings.slideFitToScreen) }
    var intervalSeconds by remember(effectiveSettings.slideIntervalMs) { mutableFloatStateOf(effectiveSettings.slideshowInterval) }

    // Chrome visibility: zero chrome on entry, auto-hides after 3s
    var chromeVisible by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }

    // Bitmap cache: max 3 bitmaps in memory
    val bitmapCache = remember { mutableStateMapOf<Int, Bitmap>() }

    // Load real Room-backed metadata and archive entries through the screen state owner.
    LaunchedEffect(zipPath, state != null) {
        if (state != null) {
            onEvent(SlideshowEvent.Load(zipPath))
        } else {
            val item = zipRepository.findZipItem(zipPath) ?: run {
                val file = File(zipPath)
                ZipItem(
                    path = zipPath, name = file.name.ifBlank { zipPath.substringAfterLast('/') },
                    size = file.length(), lastModified = file.lastModified(),
                    volumeId = "primary", volumeName = "Internal storage", isSaf = zipPath.startsWith("content://")
                )
            }
            zipRepository.beginPlayback(item)
            localZipItem = item
            localEntries = zipRepository.getZipEntries(item).filter { it.isImage }
            localLoadingEntries = false
        }
    }

    LaunchedEffect(state?.error) {
        state?.error?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // Keep screen on while playing
    DisposableEffect(effectiveSettings.slideKeepScreenOn, isPlaying) {
        if (effectiveSettings.slideKeepScreenOn && isPlaying) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    if (isLoadingEntries) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = ZipSlideTheme.colors.accent)
                Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s16))
                Text(
                    text = zipPath.substringAfterLast(File.separatorChar),
                    style = ZipSlideTheme.typography.caption,
                    color = ZipSlideTheme.colors.textTertiary
                )
            }
        }
        return
    }

    if (entries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = null,
                    tint = ZipSlideTheme.colors.textTertiary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s16))
                Text(
                    text = "No images in this zip",
                    style = ZipSlideTheme.typography.titleM,
                    color = ZipSlideTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s24))
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ZipSlideTheme.colors.textSecondary
                    )
                }
            }
        }
        return
    }

    // Display order calculation (supports consistent shuffle)
    val displayIndices = remember(entries.size, isShuffle) {
        if (isShuffle) {
            val rng = Random(zipPath.hashCode())
            entries.indices.shuffled(rng)
        } else {
            entries.indices.toList()
        }
    }

    val startIndex = (initialFrameIndex.coerceIn(0, (displayIndices.size - 1).coerceAtLeast(0)))
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { displayIndices.size })

    // Save resume index
    DisposableEffect(pagerState.currentPage) {
        val currentFrame = displayIndices[pagerState.currentPage]
        if (state != null) onEvent(SlideshowEvent.FrameChanged(zipPath, currentFrame))
        else scope.launch { zipRepository.updateLastFrame(zipPath, currentFrame) }
        onDispose {
            if (state != null) onEvent(SlideshowEvent.FrameChanged(zipPath, currentFrame))
            else scope.launch { zipRepository.updateLastFrame(zipPath, currentFrame) }
        }
    }

    // Persist a single completed viewing session when this destination leaves the back stack.
    DisposableEffect(zipPath) {
        onDispose {
            val currentFrame = displayIndices.getOrNull(pagerState.currentPage) ?: 0
            if (state != null) onEvent(SlideshowEvent.Finish(zipPath, currentFrame))
            else scope.launch {
                zipRepository.finishWatching(zipPath, currentFrame)
                zipRepository.endPlayback(zipPath)
            }
        }
    }

    // Chrome auto-hide timer (3s)
    LaunchedEffect(chromeVisible, isPlaying) {
        if (chromeVisible) {
            delay(3000)
            chromeVisible = false
        }
    }

    // Auto-advance slideshow timer
    LaunchedEffect(isPlaying, pagerState.currentPage, intervalSeconds, isLoop) {
        if (isPlaying) {
            val delayMs = (intervalSeconds * 1000f).toLong().coerceAtLeast(500L)
            delay(delayMs)
            val nextPage = pagerState.currentPage + 1
            if (nextPage < displayIndices.size) {
                pagerState.animateScrollToPage(nextPage)
            } else if (isLoop) {
                pagerState.animateScrollToPage(0)
            } else {
                isPlaying = false
            }
        }
    }

    // Preload current +/- 1 frames (never more than 3 bitmaps)
    LaunchedEffect(pagerState.currentPage, displayIndices) {
        val cur = pagerState.currentPage
        val neededPages = listOf(cur - 1, cur, cur + 1).filter { it in displayIndices.indices }

        // Evict unneeded
        val toRemove = bitmapCache.keys.filter { it !in neededPages }
        toRemove.forEach { bitmapCache.remove(it) }

        // Fetch needed
        for (page in neededPages) {
            if (!bitmapCache.containsKey(page)) {
                val entryIndex = displayIndices[page]
                val entry = entries[entryIndex]
                val bmp = zipRepository.loadFrameBitmap(zipPath, entry.entryPath, 1600)
                if (bmp != null) {
                    bitmapCache[page] = bmp
                }
            }
        }
    }

    BackHandler {
        onBack()
    }

    // Save frame to gallery function
    fun saveCurrentFrameToGallery(pageIndex: Int) {
        val entryIndex = displayIndices[pageIndex]
        val entry = entries[entryIndex]
        scope.launch(Dispatchers.IO) {
            val bmp = bitmapCache[pageIndex] ?: zipRepository.loadFrameBitmap(zipPath, entry.entryPath, 2048)
            if (bmp != null) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "${entry.basename}_saved_${System.currentTimeMillis()}.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ZipSlide")
                        }
                    }
                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Saved frame to Pictures/ZipSlide", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, e.toUserMessage(), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Fullscreen Horizontal Pager with Zoom / Pan / Gestures
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

            val bitmap = bitmapCache[page]

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                chromeVisible = !chromeVisible
                            },
                            onDoubleTap = {
                                scale = if (scale > 1.2f) 1f else 2f
                                offset = Offset.Zero
                            },
                            onLongPress = {
                                saveCurrentFrameToGallery(page)
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            if (scale > 1f) {
                                offset += pan
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount > 60f && scale <= 1f) {
                                onBack()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Slideshow frame ${page + 1}",
                        contentScale = if (fitToScreen) ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                    )
                } else {
                    CircularProgressIndicator(color = ZipSlideTheme.colors.accent)
                }
            }
        }

        // Top Floating Bar with Scrim
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC000000),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = ZipSlideTheme.spacing.s8, vertical = ZipSlideTheme.spacing.s4)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = zipPath.substringAfterLast(File.separatorChar),
                            style = ZipSlideTheme.typography.titleM,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${pagerState.currentPage + 1} / ${displayIndices.size}",
                            style = ZipSlideTheme.typography.caption,
                            color = ZipSlideTheme.colors.textSecondary
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { overflowOpen = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Slideshow Options",
                                tint = Color.White
                            )
                        }

                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false },
                            modifier = Modifier.background(ZipSlideTheme.colors.surfaceElev)
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isShuffle) "Sequential order" else "Shuffle order", color = ZipSlideTheme.colors.textPrimary) },
                                onClick = {
                                    overflowOpen = false
                                    isShuffle = !isShuffle
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isLoop) "Looping: ON" else "Looping: OFF", color = ZipSlideTheme.colors.textPrimary) },
                                onClick = {
                                    overflowOpen = false
                                    isLoop = !isLoop
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (fitToScreen) "Fill screen" else "Fit to screen", color = ZipSlideTheme.colors.textPrimary) },
                                onClick = {
                                    overflowOpen = false
                                    fitToScreen = !fitToScreen
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save frame to Pictures", color = ZipSlideTheme.colors.textPrimary) },
                                onClick = {
                                    overflowOpen = false
                                    saveCurrentFrameToGallery(pagerState.currentPage)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Bottom Floating Bar with Pill Controls & Progress Bar
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xCC000000)
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(bottom = ZipSlideTheme.spacing.s16)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Centered pill on surface at 92% opacity, radius full, 12dp padding
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(ZipSlideTheme.radii.full))
                            .background(ZipSlideTheme.colors.surface.copy(alpha = 0.92f))
                            .padding(horizontal = ZipSlideTheme.spacing.s16, vertical = ZipSlideTheme.spacing.s8)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ZipSlideTheme.spacing.s12)
                        ) {
                            // Prev (48dp target)
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val prev = (pagerState.currentPage - 1).coerceAtLeast(0)
                                        pagerState.animateScrollToPage(prev)
                                    }
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous frame",
                                    tint = Color.White
                                )
                            }

                            // Play / Pause (56dp accent-filled circle, black glyph)
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(ZipSlideTheme.colors.accent)
                                    .pointerInput(Unit) {
                                        detectTapGestures {
                                            isPlaying = !isPlaying
                                        }
                                    }
                                    .testTag("slideshow_play_pause"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            // Next (48dp target)
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val next = (pagerState.currentPage + 1).coerceAtMost(displayIndices.size - 1)
                                        pagerState.animateScrollToPage(next)
                                    }
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next frame",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(ZipSlideTheme.spacing.s12))

                    // Progress bar 2dp, outline track, accent fill, gutters 20dp each side
                    val progress = if (displayIndices.size > 1) {
                        pagerState.currentPage.toFloat() / (displayIndices.size - 1).toFloat()
                    } else 1f

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ZipSlideTheme.spacing.screenGutter)
                            .height(2.dp),
                        color = ZipSlideTheme.colors.accent,
                        trackColor = ZipSlideTheme.colors.outline
                    )
                }
            }
        }
    }
}
