package com.scrollpause.app.service.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollpause.app.BuildConfig

/**
 * Manages a small floating bubble overlay using TYPE_ACCESSIBILITY_OVERLAY.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * Why TYPE_ACCESSIBILITY_OVERLAY?
 * ─────────────────────────────────────────────────────────────────────────────
 * TYPE_ACCESSIBILITY_OVERLAY is available to apps holding the
 * BIND_ACCESSIBILITY_SERVICE permission (granted implicitly when the user
 * enables the service). It does NOT require SYSTEM_ALERT_WINDOW, which needs
 * an additional user-facing Settings prompt and is more invasive.
 *
 * Limitations:
 *   - The overlay is removed when the AccessibilityService is unbound.
 *   - On some Android versions, TYPE_ACCESSIBILITY_OVERLAY Z-order may differ.
 *   - The window cannot receive IME focus.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * Touch handling:
 *   - The overlay uses FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL so it never
 *     intercepts system gestures or traps the user.
 *   - Drag is handled by the Compose view itself via detectDragGestures.
 *   - Drag position is persisted to DataStore through [onPositionChanged].
 * ─────────────────────────────────────────────────────────────────────────────
 */
class OverlayManager(
    private val context: Context,
    private val onPositionChanged: (x: Float, y: Float) -> Unit = { _, _ -> },
) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlayView: BubbleView? = null
    private var isShowing = false

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Show the bubble with [count]. Creates the window if not yet shown.
     * Safe to call repeatedly — updates count if already visible.
     */
    fun show(count: Int, savedX: Float, savedY: Float) {
        if (isShowing) {
            overlayView?.updateCount(count)
            return
        }
        createOverlay(count, savedX, savedY)
    }

    /** Update the count without changing visibility. */
    fun updateCount(count: Int) {
        overlayView?.updateCount(count)
    }

    /** Remove the overlay window completely. */
    fun hide() {
        if (!isShowing) return
        try {
            overlayView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            log("Error removing overlay: ${e.message}")
        }
        overlayView = null
        isShowing = false
    }

    /** Must be called in AccessibilityService.onServiceDestroyed() */
    fun release() {
        hide()
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun createOverlay(initialCount: Int, savedX: Float, savedY: Float) {
        val params = buildLayoutParams(savedX, savedY)

        val view = BubbleView(context).apply {
            updateCount(initialCount)
            setOnDragListener { newX, newY ->
                // Update params position and re-layout
                try {
                    params.x = newX.toInt()
                    params.y = newY.toInt()
                    windowManager.updateViewLayout(this, params)
                    onPositionChanged(newX, newY)
                } catch (e: Exception) {
                    log("Drag update failed: ${e.message}")
                }
            }
        }

        try {
            windowManager.addView(view, params)
            overlayView = view
            isShowing = true
        } catch (e: Exception) {
            log("Failed to add overlay: ${e.message}")
        }
    }

    private fun buildLayoutParams(savedX: Float, savedY: Float): WindowManager.LayoutParams {
        @Suppress("DEPRECATION")
        val overlayType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

        return WindowManager.LayoutParams(
            BUBBLE_SIZE_PX,
            BUBBLE_SIZE_PX,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (savedX >= 0) savedX.toInt() else DEFAULT_X_PX
            y = if (savedY >= 0) savedY.toInt() else DEFAULT_Y_PX
        }
    }

    private fun log(msg: String) {
        if (BuildConfig.DEBUG) Log.d("ScrollPause/Overlay", msg)
    }

    companion object {
        private const val BUBBLE_SIZE_PX = 160  // ~56dp at mdpi
        private const val DEFAULT_X_PX = 40
        private const val DEFAULT_Y_PX = 300
    }
}

/**
 * A minimal Compose view used as the overlay bubble.
 *
 * We use [AbstractComposeView] rather than a full Compose Activity/Fragment
 * because we need to attach a Compose UI to a [WindowManager] window that
 * is not part of any Activity.
 */
private class BubbleView(context: Context) : AbstractComposeView(context) {

    private var count by mutableIntStateOf(0)
    private var dragListener: ((Float, Float) -> Unit)? = null
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    fun updateCount(newCount: Int) {
        count = newCount
    }

    fun setOnDragListener(listener: (Float, Float) -> Unit) {
        dragListener = listener
    }

    @androidx.compose.runtime.Composable
    override fun Content() {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    color = Color(0xFF2DD4BF),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (count > 999) "999+" else count.toString(),
                color = Color.White,
                fontSize = if (count > 99) 12.sp else 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragOffsetX = event.rawX
                dragOffsetY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - dragOffsetX
                val dy = event.rawY - dragOffsetY
                dragOffsetX = event.rawX
                dragOffsetY = event.rawY
                val lp = layoutParams as? WindowManager.LayoutParams
                if (lp != null) {
                    dragListener?.invoke(
                        (lp.x + dx).coerceAtLeast(0f),
                        (lp.y + dy).coerceAtLeast(0f),
                    )
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
