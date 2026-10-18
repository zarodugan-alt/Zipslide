package com.example.ui.slideshow

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.util.lerp
import com.example.data.model.SlideTransition
import kotlin.math.abs

/**
 * Every slideshow transition, expressed as a transform on one page.
 *
 * ### Coordinate contract
 * [pageOffset] is Compose's pager convention: `(currentPage - page) + currentPageOffsetFraction`.
 * The classic ViewPager `PageTransformer` convention is the negation of that, so this file keeps a
 * local `position = -pageOffset` and can then port the well-known transformer maths directly:
 *
 * - a page is laid out at `x = position * width`;
 * - therefore `translationX = -position * width` (i.e. `pageOffset * width`) **pins** a page in
 *   place and cancels the pager's own slide — the trick every "non-sliding" style relies on.
 *
 * Nothing here allocates and nothing reads composition state, so the whole catalogue runs in the
 * draw phase at display refresh rate.
 */
internal fun GraphicsLayerScope.applyTransition(style: SlideTransition, pageOffset: Float) {
    val position = -pageOffset
    val distance = abs(position).coerceIn(0f, 1f)
    val width = size.width
    val height = size.height

    /** Cancels the pager's placement so the page holds still while it transforms. */
    fun pin() {
        translationX = -position * width
    }

    when (style) {
        // The pager's own motion, untouched: a clean, instant-feeling slide.
        SlideTransition.PUSH -> Unit

        SlideTransition.FADE -> {
            pin()
            alpha = 1f - distance
        }

        SlideTransition.SLIDE -> {
            // Content trails the gesture at 65 % speed and shrinks slightly as it leaves.
            translationX = -position * width * 0.35f
            alpha = 1f - distance * 0.45f
            val shrink = lerp(0.93f, 1f, 1f - distance)
            scaleX = shrink
            scaleY = shrink
        }

        SlideTransition.PARALLAX -> {
            translationX = -position * width * 0.5f
            alpha = 1f - distance * 0.6f
        }

        SlideTransition.ZOOM -> {
            // Google's ZoomOutPageTransformer, pinned so the shrink reads as depth not drift.
            pin()
            alpha = 1f - distance
            val shrink = lerp(0.84f, 1f, 1f - distance)
            scaleX = shrink
            scaleY = shrink
        }

        SlideTransition.ZOOM_IN -> {
            pin()
            alpha = 1f - distance
            val grow = lerp(1.3f, 1f, 1f - distance)
            scaleX = grow
            scaleY = grow
        }

        SlideTransition.DEPTH -> {
            // Incoming page slides in normally; outgoing page sinks away behind it.
            if (position <= 0f) {
                pin()
                alpha = 1f - distance
                val shrink = lerp(1f, 0.72f, distance)
                scaleX = shrink
                scaleY = shrink
            }
        }

        SlideTransition.CUBE_IN -> {
            transformOrigin = TransformOrigin(if (position > 0f) 0f else 1f, 0.5f)
            cameraDistance = 24f * density
            rotationY = -90f * position
        }

        SlideTransition.CUBE_OUT -> {
            transformOrigin = TransformOrigin(if (position < 0f) 1f else 0f, 0.5f)
            cameraDistance = 24f * density
            rotationY = 90f * position
        }

        SlideTransition.FLIP -> {
            pin()
            cameraDistance = 32f * density
            rotationY = -180f * position
            // Hide the mirrored back face at the halfway point.
            alpha = if (distance > 0.5f) 0f else 1f
        }

        SlideTransition.FLIP_VERTICAL -> {
            pin()
            cameraDistance = 32f * density
            rotationX = 180f * position
            alpha = if (distance > 0.5f) 0f else 1f
        }

        SlideTransition.ROTATE_UP -> {
            pin()
            transformOrigin = TransformOrigin(0.5f, 1f)
            rotationZ = -18f * position
            alpha = 1f - distance
        }

        SlideTransition.ROTATE_DOWN -> {
            pin()
            transformOrigin = TransformOrigin(0.5f, 0f)
            rotationZ = 18f * position
            alpha = 1f - distance
        }

        SlideTransition.STACK -> {
            // The next frame slides over a frame that never moves.
            if (position > 0f) {
                pin()
                val shrink = lerp(1f, 0.94f, distance)
                scaleX = shrink
                scaleY = shrink
                alpha = 1f - distance * 0.35f
            }
        }

        SlideTransition.FAN -> {
            transformOrigin = TransformOrigin(0f, 0.5f)
            translationX = -position * width * 0.4f
            rotationZ = -14f * position
            alpha = 1f - distance * 0.6f
        }

        SlideTransition.GATE -> {
            // Two doors swinging open from the centre line.
            pin()
            cameraDistance = 28f * density
            transformOrigin = TransformOrigin(if (position < 0f) 1f else 0f, 0.5f)
            rotationY = -90f * position
            alpha = 1f - distance * 0.4f
        }

        SlideTransition.ACCORDION -> {
            pin()
            transformOrigin = TransformOrigin(if (position < 0f) 1f else 0f, 0.5f)
            scaleX = 1f - distance
        }

        SlideTransition.TABLET -> {
            cameraDistance = 40f * density
            transformOrigin = TransformOrigin(0.5f, 0f)
            rotationY = (if (position < 0f) 30f else -30f) * distance
        }

        SlideTransition.PULL_BACK -> {
            // Outgoing frame recedes; incoming frame arrives flat.
            if (position <= 0f) {
                pin()
                val shrink = lerp(1f, 0.5f, distance)
                scaleX = shrink
                scaleY = shrink
                alpha = 1f - distance
            }
        }

        SlideTransition.PUSH_FORWARD -> {
            // Incoming frame grows out of the background.
            if (position >= 0f) {
                pin()
                val grow = lerp(1f, 0.5f, distance)
                scaleX = grow
                scaleY = grow
                alpha = 1f - distance
            }
        }

        SlideTransition.VERTICAL -> {
            pin()
            translationY = position * height
        }

        SlideTransition.SHUTTER -> {
            pin()
            scaleY = 1f - distance
            alpha = 1f - distance * 0.5f
        }

        SlideTransition.CAROUSEL -> {
            // Frames ride a shallow cylinder: rotated, pushed back and dimmed at the edges.
            cameraDistance = 30f * density
            rotationY = -45f * position
            val shrink = lerp(0.8f, 1f, 1f - distance)
            scaleX = shrink
            scaleY = shrink
            alpha = 1f - distance * 0.5f
        }

        // Resolved to a concrete style by the caller before it ever reaches the layer.
        SlideTransition.RANDOM -> {
            pin()
            alpha = 1f - distance
        }
    }
}
