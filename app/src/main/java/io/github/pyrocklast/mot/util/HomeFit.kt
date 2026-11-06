package io.github.pyrocklast.mot.util

import android.content.Context
import android.graphics.Paint
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.Prefs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * How much room the home screen's names take, and how much it has for them. Text sizes follow
 * Android's font size as well as your own, so this measures them rather than assuming: the names
 * sit right under the clock whatever size it is, the gaps between them close up when they would
 * otherwise not fit, and settings can say when a number of apps won't fit even then.
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

    /** Stands in for the system bars where they can't be asked for: in settings, away from the home screen. */
    private const val TYPICAL_STATUS_BAR_DP = 32

    /** How far the clock sits below the status bar, and the gap kept under the date. */
    private const val CLOCK_TOP_DP = 24
    private const val UNDER_CLOCK_DP = 20

    /**
     * How far down the clock and date sit: below the status bar and any cutout, by [topInset].
     * Hiding the status bar takes its inset away, so they move up into its space on their own.
     */
    fun clockTop(context: Context, topInset: Int): Int = topInset + dp(context, CLOCK_TOP_DP)

    /** One name's height at the current font, weight and size. */
    fun nameHeight(context: Context, prefs: Prefs): Float =
        lineHeight(context, R.dimen.app_name_size, prefs.appNameScale, prefs.nameFont, prefs.nameWeight)

    /**
     * Where the names start when nothing has been measured yet: under the clock and date as this
     * works them out, or below the status bar when both are hidden.
     */
    fun topPadding(context: Context, prefs: Prefs, topInset: Int): Int {
        val time = if (prefs.showTime) lineHeight(context, R.dimen.clock_size, prefs.timeScale, prefs.timeFont, prefs.timeWeight) else 0f
        val date = if (prefs.showDate) lineHeight(context, R.dimen.date_size, prefs.dateScale, prefs.dateFont, prefs.dateWeight) else 0f
        if (time == 0f && date == 0f) return clockTop(context, topInset)
        return clockTop(context, topInset) + ceil(time + date).toInt() + dp(context, UNDER_CLOCK_DP)
    }

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
     * Position the whole app group around the usable screen's midpoint, while keeping it
     * below the information group. All values are pixels, measured after text scaling.
     * The bottom limit is preferred whenever the two constraints can both be satisfied.
     */
    internal fun balancedTop(
        screenHeight: Int,
        topInset: Int,
        bottomInset: Int,
        minimumTop: Int,
        groupHeight: Int,
    ): Int {
        val usableBottom = (screenHeight - bottomInset).coerceAtLeast(topInset)
        val centered = topInset + (usableBottom - topInset - groupHeight) / 2
        val latest = (usableBottom - groupHeight).coerceAtLeast(topInset)
        return centered.coerceAtLeast(minimumTop).coerceAtMost(latest.coerceAtLeast(minimumTop))
    }

    /** How many assigned rows are actually visible on the fixed (never scrolling) home surface. */
    internal data class VisibleRows(val count: Int, val showAllApps: Boolean)

    /**
     * Preserve the prefix of assigned apps and their full touch targets. If the apps overflow,
     * reserve one complete row for the drawer shortcut. If even that won't fit, the existing
     * swipe-up gesture remains the only way into the drawer; don't render a clipped shortcut.
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

    /** How many names fit on screen, with their gaps closed up as far as they go. */
    fun namesThatFit(context: Context, prefs: Prefs): Int {
        val row = tightestRow(context, prefs)
        if (row <= 0f) return Prefs.MAX_HOME_APPS
        return floor(available(context, prefs) / row).toInt().coerceAtLeast(0)
    }

    // The shortest a name's row goes: easy to tap, or the text itself where that's taller
    private fun tightestRow(context: Context, prefs: Prefs): Float =
        maxOf(dp(context, TOUCH_TARGET_DP).toFloat(), nameHeight(context, prefs))

    // The room between the clock and the navigation bar. Only settings asks: it works from the
    // screen's height and a typical set of system bars, which is close enough for its note.
    private fun available(context: Context, prefs: Prefs): Float {
        val bars = dp(context, TYPICAL_STATUS_BAR_DP)
        val room = screenHeight(context) - topPadding(context, prefs, bars) - bottomPadding(context, bars)
        return room.toFloat().coerceAtLeast(0f)
    }

    private fun screenHeight(context: Context): Int = context.resources.displayMetrics.heightPixels

    // A line of text at one of the launcher's sizes, scaled as set. Android lays a line out a
    // little taller than this (it adds the font's own leading), so it's a floor, not the exact height.
    private fun lineHeight(context: Context, size: Int, scale: Float, font: Int, weight: Int): Float {
        val paint = Paint()
        paint.typeface = Look.typeface(font, weight)
        paint.textSize = context.resources.getDimension(size) * scale
        val metrics = paint.fontMetrics
        return metrics.bottom - metrics.top
    }

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
