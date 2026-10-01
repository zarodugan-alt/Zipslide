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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.data.AppSettings
import com.example.data.ZipRepository
import com.example.data.model.SlideTransition
import com.example.data.model.ZipEntryItem
import com.example.data.model.ZipItem
import com.example.data.toUserMessage
import com.example.design.DesignTokens
import com.example.design.ZipSlideTheme
import com.example.util.VolumeKey
import com.example.util.VolumeKeyDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.random.Random

/** Frames held around the current page. Three on screen, two warmed in each direction. */
private const val PRELOAD_RADIUS = 2
private const val CHROME_TIMEOUT_MS = 3_500L

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
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    var localZipItem by remember { mutableStateOf<ZipItem?>(null) }
    var localEntries by remember { mutableStateOf<List<ZipEntryItem>>(emptyList()) }
    var localLoadingEntries by remember { mutableStateOf(true) }
    val zipItem = state?.zip ?: localZipItem
    val entries = state?.images ?: localEntries
    val isLoadingEntries = state?.loading ?: localLoadingEntries
    val effectiveSettings = state?.settings ?: settings

    // Playback controls mirror the persisted defaults and write straight back through the
    // view model, so a preference changed mid-show survives the next launch.
    var isPlaying by remember { mutableStateOf(true) }
    var isShuffle by remember(effectiveSettings.slideShuffle) { mutableStateOf(effectiveSettings.slideShuffle) }
    var isLoop by remember(effectiveSettings.slideLoop) { mutableStateOf(effectiveSettings.slideLoop) }
    var fitToScreen by remember(effectiveSettings.slideFitToScreen) { mutableStateOf(effectiveSettings.slideFitToScreen) }
    var intervalSeconds by remember(effectiveSettings.slideIntervalMs) { mutableFloatStateOf(effectiveSettings.slideshowInterval) }

    val transition = effectiveSettings.slideTransition
    val transitionMs = effectiveSettings.slideTransitionMs
    val tapZonesEnabled = effectiveSettings.slideTapZones
    val hapticsEnabled = effectiveSettings.slideHaptics

    var chromeVisible by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    // Decoded frames, keyed by page. Bounded by the preload window below.
    val bitmapCache = remember { mutableStateMapOf<Int, ImageBitmap>() }

    // Decode at the real panel resolution, capped so huge archives stay inside the frame cache.
    val targetPx = remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        with(density) {
            val widthPx = configuration.screenWidthDp.dp.toPx().toInt()
            val heightPx = configuration.screenHeightDp.dp.toPx().toInt()
            maxOf(widthPx, heightPx).coerceIn(720, 2560)
        }
    }

    LaunchedEffect(zipPath, state != null) {
        if (state != null) {
            onEvent(SlideshowEvent.Load(zipPath))
        } else {
            val item = zipRepository.findZipItem(zipPath) ?: ZipItem(
                path = zipPath, name = zipPath.substringAfterLast('/'),
                size = 0L, lastModified = 0L,
                volumeId = "primary", volumeName = "Internal storage", isSaf = zipPath.startsWith("content://")
            )
            zipRepository.beginPlayback(item)
            localZipItem = item
            localEntries = zipRepository.getZipEntries(item).filter { it.isImage }
            localLoadingEntries = false
        }
    }

    LaunchedEffect(state?.error) {
        state?.error?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    DisposableEffect(effectiveSettings.slideKeepScreenOn, isPlaying) {
        if (effectiveSettings.slideKeepScreenOn && isPlaying) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // True cinema mode: system bars disappear with the chrome and slide back with it.
    DisposableEffect(effectiveSettings.slideImmersive, chromeVisible) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (controller != null && effectiveSettings.slideImmersive) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (chromeVisible) controller.show(WindowInsetsCompat.Type.systemBars())
            else controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    if (isLoadingEntries) {
        SlideshowMessage(title = zipPath.substringAfterLast('/'), loading = true)
        return
    }

    if (entries.isEmpty()) {
        SlideshowMessage(title = "No images in this archive", loading = false, onBack = onBack)
        return
    }

    val displayIndices = remember(entries.size, isShuffle, zipPath) {
        if (isShuffle) entries.indices.shuffled(Random(zipPath.hashCode())) else entries.indices.toList()
    }

    val pageCount = displayIndices.size
    val startIndex = remember(pageCount) {
        val resumeAt = displayIndices.indexOf(initialFrameIndex).takeIf { it >= 0 } ?: initialFrameIndex
        resumeAt.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    }
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { pageCount })

    val currentPage = pagerState.currentPage
    val isSettled by remember {
        derivedStateOf { !pagerState.isScrollInProgress && pagerState.currentPageOffsetFraction == 0f }
    }

    fun persistFrame(page: Int) {
        val frame = displayIndices.getOrNull(page) ?: return
        if (state != null) onEvent(SlideshowEvent.FrameChanged(zipPath, frame))
        else scope.launch { zipRepository.updateLastFrame(zipPath, frame) }
    }

    // One write per settled frame instead of a write on every recomposition.
    LaunchedEffect(pagerState, pageCount) {
        snapshotFlow { pagerState.settledPage }.collect { persistFrame(it) }
    }

    DisposableEffect(zipPath) {
        onDispose {
            val frame = displayIndices.getOrNull(pagerState.currentPage) ?: 0
            if (state != null) onEvent(SlideshowEvent.Finish(zipPath, frame))
            else scope.launch {
                zipRepository.finishWatching(zipPath, frame)
                zipRepository.endPlayback(zipPath)
            }
        }
    }

    fun tick() {
        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun goTo(page: Int, animate: Boolean = true, durationMs: Int = transitionMs) {
        if (pageCount == 0) return
        val target = page.coerceIn(0, pageCount - 1)
        scope.launch {
            if (animate) {
                pagerState.animateScrollToPage(
                    page = target,
                    animationSpec = tween(durationMs, easing = DesignTokens.Motion.standardEasing)
                )
            } else {
                pagerState.scrollToPage(target)
            }
        }
    }

    /**
     * @param auto true for the interval timer, which gets a slightly longer, calmer curve.
     * @param instant skips the animation entirely; used while a volume key auto-repeats so a held
     *   key flies through frames instead of queueing hundreds of overlapping animations.
     */
    fun advance(auto: Boolean = false, instant: Boolean = false) {
        val next = pagerState.currentPage + 1
        val duration = if (auto) (transitionMs * 1.4f).toInt() else transitionMs
        when {
            next < pageCount -> goTo(next, animate = !instant, durationMs = duration)
            isLoop && pageCount > 1 -> goTo(0, animate = !instant, durationMs = duration)
            else -> {
                isPlaying = false
                finished = true
                chromeVisible = true
            }
        }
    }

    fun previous(instant: Boolean = false) {
        val prev = pagerState.currentPage - 1
        when {
            prev >= 0 -> goTo(prev, animate = !instant)
            isLoop && pageCount > 1 -> goTo(pageCount - 1, animate = !instant)
        }
    }

    // ── Remaining-time rail ────────────────────────────────────────────────────────────────
    // A single linear animation per frame, cancelled the instant playback pauses or the user
    // touches the pager, so the bar always reflects the real time left on screen.
    val timer = remember { Animatable(1f) }
    LaunchedEffect(isPlaying, currentPage, intervalSeconds, pageCount, isLoop, pagerState.isScrollInProgress) {
        if (!isPlaying || pagerState.isScrollInProgress) {
            if (!isPlaying) timer.snapTo(1f)
            return@LaunchedEffect
        }
        val durationMs = (intervalSeconds * 1000f).toInt().coerceAtLeast(500)
        timer.snapTo(1f)
        timer.animateTo(0f, tween(durationMs, easing = LinearEasing))
        advance(auto = true)
    }

    LaunchedEffect(currentPage) {
        if (currentPage < pageCount - 1) finished = false
    }

    LaunchedEffect(chromeVisible, isPlaying) {
        if (chromeVisible && isPlaying && !overflowOpen) {
            delay(CHROME_TIMEOUT_MS)
            chromeVisible = false
        }
    }

    // ── Frame preloading ───────────────────────────────────────────────────────────────────
    LaunchedEffect(currentPage, pageCount, targetPx, effectiveSettings.slideHighQuality) {
        // Settle first so a fast scrub does not queue a decode for every page it flies past.
        if (bitmapCache.containsKey(currentPage)) delay(40)
        val wanted = buildList {
            add(currentPage)
            for (step in 1..PRELOAD_RADIUS) {
                add(currentPage + step)
                add(currentPage - step)
            }
        }.filter { it in 0 until pageCount }

        bitmapCache.keys.filter { it !in wanted }.forEach { bitmapCache.remove(it) }

        for (page in wanted) {
            if (bitmapCache.containsKey(page)) continue
            val entry = entries.getOrNull(displayIndices[page]) ?: continue
            val bitmap = zipRepository.loadFrameBitmap(
                zipPath = zipPath,
                entryName = entry.entryPath,
                maxDim = targetPx,
                highQuality = effectiveSettings.slideHighQuality
            )
            if (bitmap != null) bitmapCache[page] = bitmap.asImageBitmap()
        }
    }

    // Directional tap feedback, the way a reader app confirms a page turn.
    var flashSide by remember { mutableIntStateOf(0) }
    val flash = remember { Animatable(0f) }
    fun pulse(side: Int) {
        flashSide = side
        scope.launch {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(420, easing = DesignTokens.Motion.standardEasing))
        }
    }

    // ── Hardware volume keys ───────────────────────────────────────────────────────────────
    // Down is forward, up is back. Holding a key auto-repeats, and repeats jump without an
    // animation so a 300-frame archive can be crossed in a couple of seconds.
    val volumeKeysEnabled = effectiveSettings.slideVolumeKeys
    val onVolumeKey by rememberUpdatedState<(VolumeKey, Boolean) -> Boolean> { key, repeat ->
        if (!volumeKeysEnabled) {
            false
        } else {
            if (!repeat && hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            when (key) {
                VolumeKey.DOWN -> {
                    if (!repeat) pulse(1)
                    advance(instant = repeat)
                }
                VolumeKey.UP -> {
                    if (!repeat) pulse(-1)
                    previous(instant = repeat)
                }
            }
            true
        }
    }
    DisposableEffect(Unit) {
        val handler = VolumeKeyDispatcher.Handler { key, repeat -> onVolumeKey(key, repeat) }
        VolumeKeyDispatcher.register(handler)
        onDispose { VolumeKeyDispatcher.unregister(handler) }
    }

    // ── Swipe down to dismiss ──────────────────────────────────────────────────────────────
    val dismissDrag = remember { Animatable(0f) }
    val dismissThresholdPx = with(density) { 160.dp.toPx() }
    val dismissFraction = (abs(dismissDrag.value) / (dismissThresholdPx * 2.2f)).coerceIn(0f, 1f)

    BackHandler { onBack() }

    fun saveCurrentFrameToGallery(pageIndex: Int) {
        val entry = entries.getOrNull(displayIndices.getOrNull(pageIndex) ?: return) ?: return
        scope.launch(Dispatchers.IO) {
            val bitmap = zipRepository.loadFrameBitmap(zipPath, entry.entryPath, 4096, highQuality = true)
            if (bitmap == null) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "That frame could not be read.", Toast.LENGTH_SHORT).show() }
                return@launch
            }
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "${entry.basename.substringBeforeLast('.')}_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ZipSlide")
                    }
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Saved to Pictures/ZipSlide", Toast.LENGTH_SHORT).show() }
                }
            } catch (error: Throwable) {
                withContext(Dispatchers.Main) { Toast.makeText(context, error.toUserMessage(), Toast.LENGTH_SHORT).show() }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer {
                translationY = dismissDrag.value
                val shrink = lerp(1f, 0.86f, dismissFraction)
                scaleX = shrink
                scaleY = shrink
            }
    ) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            key = { page -> page },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            var scale by remember(page) { mutableFloatStateOf(1f) }
            var pan by remember(page) { mutableStateOf(Offset.Zero) }
            val bitmap = bitmapCache[page]
            val isCurrent = page == currentPage

            LaunchedEffect(isCurrent) {
                if (!isCurrent && scale != 1f) {
                    scale = 1f
                    pan = Offset.Zero
                }
            }

            // Slow drift that keeps a still frame feeling alive. Disabled while zoomed.
            val kenBurns = remember(page) { Animatable(1f) }
            LaunchedEffect(isCurrent, isPlaying, effectiveSettings.slideKenBurns, intervalSeconds) {
                if (effectiveSettings.slideKenBurns && isCurrent && isPlaying) {
                    kenBurns.snapTo(1f)
                    kenBurns.animateTo(
                        targetValue = 1.055f,
                        animationSpec = tween((intervalSeconds * 1000f).toInt().coerceAtLeast(500) + transitionMs, easing = LinearEasing)
                    )
                } else {
                    kenBurns.snapTo(1f)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(pageCount, tapZonesEnabled) {
                        detectTapGestures(
                            onTap = { offset ->
                                val zone = offset.x / size.width
                                when {
                                    !tapZonesEnabled || scale > 1.05f -> chromeVisible = !chromeVisible
                                    zone >= 0.68f -> { tick(); pulse(1); advance(auto = false) }
                                    zone <= 0.32f -> { tick(); pulse(-1); previous() }
                                    else -> chromeVisible = !chromeVisible
                                }
                            },
                            onDoubleTap = { offset ->
                                if (scale > 1.05f) {
                                    scale = 1f
                                    pan = Offset.Zero
                                } else {
                                    scale = 2.5f
                                    pan = Offset(
                                        x = (size.width / 2f - offset.x) * 1.5f,
                                        y = (size.height / 2f - offset.y) * 1.5f
                                    )
                                }
                                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onLongPress = {
                                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                saveCurrentFrameToGallery(page)
                            }
                        )
                    }
                    .pointerInput(page) {
                        detectTransformGestures { _, panChange, zoom, _ ->
                            val next = (scale * zoom).coerceIn(1f, 5f)
                            scale = next
                            pan = if (next > 1f) {
                                val limitX = size.width * (next - 1f) / 2f
                                val limitY = size.height * (next - 1f) / 2f
                                Offset(
                                    x = (pan.x + panChange.x).coerceIn(-limitX, limitX),
                                    y = (pan.y + panChange.y).coerceIn(-limitY, limitY)
                                )
                            } else Offset.Zero
                        }
                    }
                    .pointerInput(page) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (abs(dismissDrag.value) > dismissThresholdPx) onBack()
                                    else dismissDrag.animateTo(0f, tween(240, easing = DesignTokens.Motion.standardEasing))
                                }
                            },
                            onDragCancel = { scope.launch { dismissDrag.animateTo(0f, tween(240)) } }
                        ) { _, dragAmount ->
                            if (scale <= 1.05f) {
                                scope.launch { dismissDrag.snapTo(dismissDrag.value + dragAmount * 0.6f) }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    // Letterboxing is pure black. An over-scaled copy of the frame used to sit
                    // back here, and its parallax during a page turn read as a second, laggy
                    // image rather than as depth.
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Frame ${page + 1} of $pageCount",
                        contentScale = if (fitToScreen) ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                applyTransition(transition, offset)
                                val zoom = scale * kenBurns.value
                                scaleX *= zoom
                                scaleY *= zoom
                                translationX += pan.x
                                translationY += pan.y
                            }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF101014), Color(0xFF050507))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = ZipSlideTheme.colors.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        // Directional tap acknowledgement
        if (flash.value > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.32f)
                    .align(if (flashSide > 0) Alignment.CenterEnd else Alignment.CenterStart)
                    .graphicsLayer { alpha = flash.value * 0.85f }
                    .background(
                        Brush.horizontalGradient(
                            colors = if (flashSide > 0) listOf(Color.Transparent, Color(0x33FFFFFF))
                            else listOf(Color(0x33FFFFFF), Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (flashSide > 0) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        // ── Remaining-time line, pinned above everything ───────────────────────────────────
        if (effectiveSettings.slideProgressLine) {
            val railAlpha by animateFloatAsState(
                targetValue = if (isPlaying) 1f else 0.35f,
                animationSpec = tween(DesignTokens.Motion.StandardDurationMs),
                label = "railAlpha"
            )
            val accent = ZipSlideTheme.colors.accent
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .drawBehind {
                        drawRect(color = Color.White.copy(alpha = 0.08f))
                        val width = size.width * timer.value
                        if (width > 0f) {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(accent.copy(alpha = 0.55f), accent),
                                    startX = 0f,
                                    endX = size.width
                                ),
                                size = Size(width, size.height),
                                alpha = railAlpha
                            )
                        }
                    }
            )
        }

        // ── Top chrome ─────────────────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(tween(DesignTokens.Motion.StandardDurationMs)) + slideInVertically { -it / 3 },
            exit = fadeOut(tween(DesignTokens.Motion.FastDurationMs)) + slideOutVertically { -it / 3 },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xE6000000), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = ZipSlideTheme.spacing.s8, vertical = ZipSlideTheme.spacing.s8)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(Modifier.width(ZipSlideTheme.spacing.s8))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = zipItem?.name?.removeSuffix(".zip") ?: zipPath.substringAfterLast('/'),
                            style = ZipSlideTheme.typography.titleM,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = entries.getOrNull(displayIndices.getOrNull(currentPage) ?: 0)?.basename.orEmpty(),
                            style = ZipSlideTheme.typography.caption,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    CounterChip(current = currentPage + 1, total = pageCount)
                    Box {
                        IconButton(onClick = { overflowOpen = true }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false },
                            modifier = Modifier.background(ZipSlideTheme.colors.surfaceElev)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Slower  ·  ${"%.1f".format(intervalSeconds)}s", color = ZipSlideTheme.colors.textPrimary) },
                                onClick = {
                                    intervalSeconds = (intervalSeconds + 0.5f).coerceAtMost(30f)
                                    onEvent(SlideshowEvent.SetInterval(intervalSeconds))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Faster  ·  ${"%.1f".format(intervalSeconds)}s", color = ZipSlideTheme.colors.textPrimary) },
                                onClick = {
                                    intervalSeconds = (intervalSeconds - 0.5f).coerceAtLeast(0.5f)
                                    onEvent(SlideshowEvent.SetInterval(intervalSeconds))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (fitToScreen) "Fill the screen" else "Fit to screen", color = ZipSlideTheme.colors.textPrimary) },
                                onClick = {
                                    overflowOpen = false
                                    fitToScreen = !fitToScreen
                                    onEvent(SlideshowEvent.SetFitToScreen(fitToScreen))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Transition · ${transition.label}", color = ZipSlideTheme.colors.textPrimary) },
                                onClick = {
                                    val next = SlideTransition.entries[(transition.ordinal + 1) % SlideTransition.entries.size]
                                    onEvent(SlideshowEvent.SetTransition(next))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save this frame", color = ZipSlideTheme.colors.textPrimary) },
                                onClick = {
                                    overflowOpen = false
                                    saveCurrentFrameToGallery(currentPage)
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── Bottom chrome: scrubber + compact toggles (no transport pill) ──────────────────
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(tween(DesignTokens.Motion.StandardDurationMs)) + slideInVertically { it / 3 },
            exit = fadeOut(tween(DesignTokens.Motion.FastDurationMs)) + slideOutVertically { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                    .navigationBarsPadding()
                    .padding(horizontal = ZipSlideTheme.spacing.screenGutter, vertical = ZipSlideTheme.spacing.s12)
            ) {
                FrameScrubber(
                    pageCount = pageCount,
                    currentPage = currentPage,
                    accent = ZipSlideTheme.colors.accent,
                    onSeek = { page ->
                        if (page != pagerState.currentPage) {
                            tick()
                            goTo(page, animate = false)
                        }
                    }
                )

                Spacer(Modifier.height(ZipSlideTheme.spacing.s12))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ZipSlideTheme.spacing.s8)
                ) {
                    GhostToggle(
                        selected = isPlaying,
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        label = if (isPlaying) "Pause" else "Play",
                        accent = ZipSlideTheme.colors.accent,
                        modifier = Modifier.testTag("slideshow_play_pause")
                    ) {
                        isPlaying = !isPlaying
                        finished = false
                    }
                    GhostToggle(
                        selected = isShuffle,
                        icon = Icons.Default.Shuffle,
                        label = "Shuffle",
                        accent = ZipSlideTheme.colors.accent
                    ) {
                        isShuffle = !isShuffle
                        onEvent(SlideshowEvent.SetShuffle(isShuffle))
                    }
                    GhostToggle(
                        selected = isLoop,
                        icon = Icons.Default.Repeat,
                        label = "Loop",
                        accent = ZipSlideTheme.colors.accent
                    ) {
                        isLoop = !isLoop
                        onEvent(SlideshowEvent.SetLoop(isLoop))
                    }
                    Spacer(Modifier.weight(1f))
                    GhostToggle(
                        selected = false,
                        icon = Icons.Default.Download,
                        label = "Save frame",
                        accent = ZipSlideTheme.colors.accent
                    ) { saveCurrentFrameToGallery(currentPage) }
                }
            }
        }

        // ── End of archive ─────────────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = finished && isSettled,
            enter = fadeIn(tween(DesignTokens.Motion.ExpressiveDurationMs)),
            exit = fadeOut(tween(DesignTokens.Motion.FastDurationMs)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(ZipSlideTheme.radii.xl))
                    .background(Color(0xCC0B0B0F))
                    .padding(horizontal = ZipSlideTheme.spacing.s32, vertical = ZipSlideTheme.spacing.s24),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("End of slideshow", style = ZipSlideTheme.typography.titleM, color = Color.White)
                Spacer(Modifier.height(ZipSlideTheme.spacing.s4))
                Text(
                    text = "$pageCount frames viewed",
                    style = ZipSlideTheme.typography.caption,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(ZipSlideTheme.spacing.s16))
                Row(horizontalArrangement = Arrangement.spacedBy(ZipSlideTheme.spacing.s12)) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(ZipSlideTheme.radii.full))
                            .background(ZipSlideTheme.colors.accent)
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    finished = false
                                    isPlaying = true
                                    goTo(0)
                                }
                            }
                            .padding(horizontal = ZipSlideTheme.spacing.s20, vertical = ZipSlideTheme.spacing.s12),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(ZipSlideTheme.spacing.s8))
                        Text("Replay", style = ZipSlideTheme.typography.bodyM, color = Color.Black)
                    }
                    Text(
                        text = "Close",
                        style = ZipSlideTheme.typography.bodyM,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(ZipSlideTheme.radii.full))
                            .pointerInput(Unit) { detectTapGestures { onBack() } }
                            .padding(horizontal = ZipSlideTheme.spacing.s20, vertical = ZipSlideTheme.spacing.s12)
                    )
                }
            }
        }
    }
}

/** Applies the selected page motion. Every style fully controls its own placement. */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.applyTransition(
    style: SlideTransition,
    pageOffset: Float
) {
    val distance = abs(pageOffset).coerceIn(0f, 1f)
    when (style) {
        SlideTransition.FADE -> {
            translationX = pageOffset * size.width
            alpha = 1f - distance
        }
        SlideTransition.SLIDE -> {
            translationX = pageOffset * size.width * 0.35f
            alpha = 1f - distance * 0.45f
            val shrink = lerp(0.93f, 1f, 1f - distance)
            scaleX = shrink
            scaleY = shrink
        }
        SlideTransition.ZOOM -> {
            translationX = pageOffset * size.width
            alpha = 1f - distance
            val shrink = lerp(0.84f, 1f, 1f - distance)
            scaleX = shrink
            scaleY = shrink
        }
        SlideTransition.DEPTH -> {
            if (pageOffset >= 0f) {
                translationX = pageOffset * size.width
                alpha = 1f - distance
                val shrink = lerp(1f, 0.72f, distance)
                scaleX = shrink
                scaleY = shrink
            } else {
                alpha = 1f
                scaleX = 1f
                scaleY = 1f
            }
        }
    }
}

@Composable
private fun CounterChip(current: Int, total: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(ZipSlideTheme.radii.full))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = ZipSlideTheme.spacing.s12, vertical = ZipSlideTheme.spacing.s4)
    ) {
        Text(
            text = "$current / $total",
            style = ZipSlideTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White
        )
    }
}

/**
 * A hairline rail the full width of the screen. Dragging it scrubs frames immediately, which is
 * far quicker on a 300-frame archive than tapping through.
 */
@Composable
private fun FrameScrubber(
    pageCount: Int,
    currentPage: Int,
    accent: Color,
    onSeek: (Int) -> Unit
) {
    val progress = if (pageCount > 1) currentPage.toFloat() / (pageCount - 1).toFloat() else 1f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(DesignTokens.Motion.StandardDurationMs, easing = DesignTokens.Motion.standardEasing),
        label = "scrubProgress"
    )

    fun pageFor(x: Float, width: Float): Int =
        ((x / width).coerceIn(0f, 1f) * (pageCount - 1)).toInt()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(pageCount) {
                detectTapGestures { offset -> onSeek(pageFor(offset.x, size.width.toFloat())) }
            }
            .pointerInput(pageCount) {
                detectHorizontalDragGestures { change, _ ->
                    onSeek(pageFor(change.position.x, size.width.toFloat()))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(ZipSlideTheme.radii.full))
                .background(Color.White.copy(alpha = 0.18f))
                .drawBehind {
                    drawRect(
                        brush = Brush.horizontalGradient(listOf(accent.copy(alpha = 0.7f), accent)),
                        size = Size(size.width * animatedProgress, size.height)
                    )
                }
        )
    }
}

@Composable
private fun GhostToggle(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val background by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(DesignTokens.Motion.FastDurationMs),
        label = "toggleBackground"
    )
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (selected) accent.copy(alpha = 0.18f + background * 0.08f)
                else Color.White.copy(alpha = 0.10f)
            )
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) accent else Color.White.copy(alpha = 0.9f),
            modifier = Modifier
                .size(20.dp)
                .alpha(if (selected) 1f else 0.9f)
        )
    }
}

@Composable
private fun SlideshowMessage(title: String, loading: Boolean, onBack: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (loading) {
                CircularProgressIndicator(color = ZipSlideTheme.colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            } else {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = null,
                    tint = ZipSlideTheme.colors.textTertiary,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(Modifier.height(ZipSlideTheme.spacing.s16))
            Text(
                text = title,
                style = if (loading) ZipSlideTheme.typography.caption else ZipSlideTheme.typography.titleM,
                color = if (loading) ZipSlideTheme.colors.textTertiary else ZipSlideTheme.colors.textPrimary
            )
            if (onBack != null) {
                Spacer(Modifier.height(ZipSlideTheme.spacing.s24))
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = ZipSlideTheme.colors.textSecondary
                    )
                }
            }
        }
    }
}
