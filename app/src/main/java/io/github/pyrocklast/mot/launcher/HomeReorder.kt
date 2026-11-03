package io.github.pyrocklast.mot.launcher

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Moving a home app to another spot: long press it, then drag it up or down. The drag only
 * starts once the finger has moved half a row, so a long press that wobbles a little still just
 * opens the menu. The other apps slide aside to make room; letting go where it started changes
 * nothing.
 *
 * The home screen's gesture detector handles the long press and calls [arm]; from then on, until
 * the finger lifts, the home screen passes each touch to [onTouch] first.
 */
class HomeReorder(
    /** The home apps' views, top to bottom. */
    private val apps: () -> List<View>,
    /** What fades while an app is moving: everything on the home screen but the app. */
    private val others: () -> List<View>,
    /** The drag has started: close the menu. */
    private val onStart: () -> Unit,
    /** Let go without dragging, with the spot that was pressed. */
    private val onRelease: (Int) -> Unit,
    /** Let go somewhere else: move the app from one spot to the other. */
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    private var views: List<View> = emptyList()
    private var from = NONE
    private var to = NONE
    private var startY = 0f
    private var dragging = false
    private var tops = IntArray(0)

    /**
     * A long press on the home app at [index], [rawY] being where the finger went down. With
     * [lift], the app picks up straight away (when the menu waits for the finger to lift).
     */
    fun arm(index: Int, rawY: Float, lift: Boolean) {
        views = apps()
        if (index !in views.indices || views.size < 2) return
        from = index
        to = index
        startY = rawY
        dragging = false
        if (lift) fadeOthers(true)
    }

    /** Handles a touch while a long press is armed. Returns true once it's moving an app. */
    fun onTouch(event: MotionEvent): Boolean {
        if (from == NONE) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dy = event.rawY - startY
                if (!dragging) {
                    if (abs(dy) < views[from].height / 2f) return false
                    start()
                }
                follow(dy)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val wasDragging = dragging
                if (wasDragging) drop() else release()
                return wasDragging
            }
            MotionEvent.ACTION_CANCEL -> {
                val wasDragging = dragging
                cancel()
                return wasDragging
            }
        }
        return dragging
    }

    /** Puts everything back where it was, e.g. when the home screen is left mid-drag. */
    fun cancel() {
        if (from == NONE) return
        views.forEach { it.animate().translationY(0f).setDuration(SETTLE_MS).start() }
        fadeOthers(false)
        reset()
    }

    private fun start() {
        dragging = true
        tops = IntArray(views.size) { views[it].top }
        views[from].performHapticFeedback(HapticFeedbackConstants.DRAG_START)
        fadeOthers(true)
        onStart()
    }

    // The app follows the finger, between the first and last spots; the others make room
    private fun follow(dy: Float) {
        val last = views.lastIndex
        val offset = dy.coerceIn((tops[0] - tops[from]).toFloat(), (tops[last] - tops[from]).toFloat())
        val dragged = views[from]
        dragged.translationY = offset
        val centre = tops[from] + offset + dragged.height / 2f
        val spot = views.indices.minBy { abs(tops[it] + views[it].height / 2f - centre) }
        if (spot == to) return
        to = spot
        dragged.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        views.forEachIndexed { i, view ->
            if (i == from) return@forEachIndexed
            val shift = when {
                i in from + 1..to -> tops[i - 1] - tops[i]
                i in to until from -> tops[i + 1] - tops[i]
                else -> 0
            }
            view.animate().translationY(shift.toFloat()).setDuration(MAKE_ROOM_MS).start()
        }
    }

    // The app settles into its new spot, then the move is saved and the home screen redrawn
    private fun drop() {
        val moved = views[from]
        val start = from
        val end = to
        val all = views
        reset()
        moved.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
        moved.animate().translationY((tops[end] - tops[start]).toFloat()).setDuration(SETTLE_MS).withEndAction {
            all.forEach {
                it.animate().cancel()
                it.translationY = 0f
            }
            if (end != start) onMove(start, end)
        }.start()
        fadeOthers(false, all)
    }

    private fun release() {
        val pressed = from
        fadeOthers(false)
        reset()
        onRelease(pressed)
    }

    private fun reset() {
        from = NONE
        to = NONE
        dragging = false
    }

    private fun fadeOthers(fade: Boolean, appViews: List<View> = views) {
        val moving = appViews.getOrNull(from)
        val faded = others() + appViews.filter { it !== moving }
        faded.forEach { it.animate().alpha(if (fade) FADED else 1f).setDuration(SETTLE_MS).start() }
        if (!fade) moving?.alpha = 1f
    }

    private companion object {
        const val NONE = -1
        const val FADED = 0.35f
        const val MAKE_ROOM_MS = 120L
        const val SETTLE_MS = 120L
    }
}
