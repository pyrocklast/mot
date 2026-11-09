package io.github.pyrocklast.mot.util

import android.content.Context
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.Prefs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * How much room the home screen's names take, and how much it has for them. Text sizes follow
 * Android's font size and your settings. The launcher measures the names and the room it really
 * has, sits them in the middle of it, keeps every row easy to tap, and, as the home screen never
 * scrolls, ends with "All apps" when they don't all fit.
 */
object HomeFit {

    /** The gap above and below a name when there's room for it. */
    private const val USUAL_PADDING_DP = 8

    /** A name's row is never shorter than this, so it's always easy to tap, however small the text. */
    private const val TOUCH_TARGET_DP = 48

    /** The gap kept below the names, under the navigation bar wherever it ends. */
    private const val ABOVE_NAVIGATION_DP = 8

    /** Left spare when closing the gaps up, so the last name can't end up a hair too low. */
    private const val SAFETY_DP = 2

    /** How far the clock sits below the status bar, and the gap kept under the date. */
    private const val CLOCK_TOP_DP = 24
    private const val UNDER_CLOCK_DP = 20

    /**
     * How far down the clock and date sit: below the status bar and any cutout, by [topInset].
     * Hiding the status bar takes its inset away, so they move up into its space on their own.
     */
    fun clockTop(context: Context, topInset: Int): Int = topInset + dp(context, CLOCK_TOP_DP)

    /** App names' size, on the home screen and in the drawer: Android's font size, then yours. */
    fun appNameSizePx(context: Context, prefs: Prefs): Float =
        context.resources.getDimension(R.dimen.app_name_size) * prefs.appNameScale

    /** Where the names stop: clear of the navigation bar, wherever Android puts it. */
    fun bottomPadding(context: Context, bottomInset: Int): Int = bottomInset + dp(context, ABOVE_NAVIGATION_DP)

    /**
     * The gap above and below each name: the usual one, and never more. It closes up only when
     * [count] names of [nameHeight] pixels wouldn't otherwise fit in [room], and then only as far
     * as it takes, down to a row that's still easy to tap.
     *
     * The launcher measures [nameHeight] and [room] rather than working them out: a name laid out
     * a few pixels taller than expected would have the last one cut off.
     */
    fun namePadding(context: Context, count: Int, nameHeight: Float, room: Float): Int = namePadding(
        count = count,
        nameHeight = nameHeight,
        room = room,
        usual = dp(context, USUAL_PADDING_DP),
        touchTarget = ceil(context.resources.displayMetrics.density * TOUCH_TARGET_DP).toInt(),
        safety = dp(context, SAFETY_DP),
    )

    /**
     * The arithmetic behind the gap, in pixels, so it can be tested on its own. A row is always
     * at least [touchTarget] tall, so a small name is padded out to it even where that's more
     * than [usual]; a name taller than that needs no gap at all.
     */
    internal fun namePadding(count: Int, nameHeight: Float, room: Float, usual: Int, touchTarget: Int, safety: Int): Int {
        if (count <= 0 || nameHeight <= 0f) return usual
        val leastForTouch = ceil((maxOf(touchTarget.toFloat(), nameHeight) - nameHeight) / 2).toInt().coerceAtLeast(0)
        val toFit = floor(((room - safety) / count - nameHeight) / 2).toInt()
        return toFit.coerceIn(leastForTouch, maxOf(usual, leastForTouch))
    }

    /** Where the names start, under a clock and date that have been measured. */
    fun topPadding(context: Context, prefs: Prefs, topInset: Int, clockHeight: Int): Int {
        if (!prefs.showTime && !prefs.showDate) return clockTop(context, topInset)
        return clockTop(context, topInset) + clockHeight + dp(context, UNDER_CLOCK_DP)
    }

    /**
     * Where the names start so that they sit in the middle of the room under the clock and date,
     * in pixels. When they can't, they stay clear of the navigation bar first.
     */
    internal fun balancedTop(
        screenHeight: Int,
        topInset: Int,
        bottomInset: Int,
        minimumTop: Int,
        groupHeight: Int,
    ): Int {
        // The middle of the room under the clock and date, not of the whole screen, or a long
        // list of names would end up pressed against the date
        val usableTop = maxOf(topInset, minimumTop)
        val usableBottom = (screenHeight - bottomInset).coerceAtLeast(usableTop)
        val centered = usableTop + (usableBottom - usableTop - groupHeight) / 2
        val latest = (usableBottom - groupHeight).coerceAtLeast(usableTop)
        return centered.coerceIn(usableTop, latest)
    }

    /** How many names show on the home screen, and whether "All apps" ends the list. */
    internal data class VisibleRows(val count: Int, val showAllApps: Boolean)

    /**
     * As many names as fit, from the top, each at its full height. When they don't all fit, the
     * last row is "All apps" instead; if not even that fits, swiping up is still there.
     */
    internal fun visibleRows(rowHeights: List<Int>, availableHeight: Int, overflowHeight: Int): VisibleRows {
        val room = availableHeight.coerceAtLeast(0)
        val heights = rowHeights.map { it.coerceAtLeast(0) }
        if (heights.sumOf { it.toLong() } <= room.toLong()) return VisibleRows(heights.size, false)

        val showAllApps = overflowHeight > 0 && overflowHeight <= room
        val rowsRoom = if (showAllApps) room - overflowHeight else room
        var remaining = rowsRoom
        var visibleCount = 0
        for (height in heights) {
            if (height > remaining) break
            remaining -= height
            visibleCount++
        }
        return VisibleRows(visibleCount, showAllApps)
    }

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
