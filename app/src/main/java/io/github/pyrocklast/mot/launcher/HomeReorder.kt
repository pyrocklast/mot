package io.github.pyrocklast.mot.launcher

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * Moving a home app to another spot: long press it, then drag it where you want it. The drag
 * only starts once the finger has moved half a row, so a long press that wobbles a little still
 * just opens the menu. Letting go where it started changes nothing.
 *
 * Upright the names are one column and the others slide aside to make room. On a screen turned
 * on its side they can be several, and a name crossing from one to another has no such room to
 * open up — the names either side of a gap in a column aren't the ones either side of it in the
 * list — so the apps stay where they are until the finger lifts, and the one being carried
 * simply lands where it was taken.
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
    private var startX = 0f
    private var startY = 0f
    private var dragging = false
    private var tops = IntArray(0)
    private var lefts = IntArray(0)

    /**
     * A long press on the home app at [index], [rawY] being where the finger went down. With
     * [lift], the app picks up straight away (when the menu waits for the finger to lift).
     */
    fun arm(index: Int, rawX: Float, rawY: Float, lift: Boolean) {
        // A copy: the home screen's own list changes under us when it's redrawn, and comparing
        // against it is how a drag knows to let go
        views = ArrayList(apps())
        if (index !in views.indices) return
        from = index
        to = index
        startX = rawX
        startY = rawY
        dragging = false
        if (lift) fadeOthers(true)
    }

    /** Handles a touch while a long press is armed. Returns true once it's moving an app. */
    fun onTouch(event: MotionEvent): Boolean {
        if (from == NONE) return false
        // The home screen was redrawn under the finger (an app installed or removed, a setting
        // changed): these aren't the names that were picked up, so let go of them
        if (views != apps()) {
            cancel()
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - startX
                val dy = event.rawY - startY
                if (!dragging) {
                    // A single home app has nowhere to go: letting go still opens its menu
                    if (views.size < 2 || hypot(dx, dy) < views[from].height / 2f) return false
                    start()
                }
                follow(dx, dy)
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
        views.forEach { it.animate().translationX(0f).translationY(0f).setDuration(SETTLE_MS).start() }
        fadeOthers(false)
        reset()
    }

    private fun start() {
        dragging = true
        tops = IntArray(views.size) { views[it].top }
        lefts = IntArray(views.size) { views[it].left }
        views[from].performHapticFeedback(HapticFeedbackConstants.DRAG_START)
        fadeOthers(true)
        onStart()
    }

    // The app follows the finger, between the first and last spots; the others make room
    private fun follow(dx: Float, dy: Float) {
        val dragged = views[from]
        // The app goes no further than the spots themselves: there is nothing to drop it on
        // outside them
        val offsetX = dx.coerceIn((lefts.min() - lefts[from]).toFloat(), (lefts.max() - lefts[from]).toFloat())
        val offsetY = dy.coerceIn((tops.min() - tops[from]).toFloat(), (tops.max() - tops[from]).toFloat())
        dragged.translationX = offsetX
        dragged.translationY = offsetY
        // Its own left edge against theirs, which a column's names share, and its middle against
        // their middles
        val x = lefts[from] + offsetX
        val y = tops[from] + offsetY + dragged.height / 2f
        val spot = views.indices.minBy {
            hypot(lefts[it] - x, tops[it] + views[it].height / 2f - y)
        }
        if (spot == to) return
        to = spot
        dragged.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        makeRoom()
    }

    /**
     * The apps between the one being carried and where it would land slide up or down a row.
     * Only within a column: a gap opened in one column isn't where the app would land in
     * another, and the names either side of it aren't its neighbours in the list either.
     */
    private fun makeRoom() {
        val sameColumn = lefts[from] == lefts[to]
        views.forEachIndexed { i, view ->
            if (i == from) return@forEachIndexed
            val shift = when {
                !sameColumn -> 0
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
        moved.animate()
            .translationX((lefts[end] - lefts[start]).toFloat())
            .translationY((tops[end] - tops[start]).toFloat())
            .setDuration(SETTLE_MS).withEndAction {
                all.forEach {
                    it.animate().cancel()
                    it.translationX = 0f
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
        if (pressed in views.indices) onRelease(pressed)
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
