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

    /** Closed up, a name is still this easy to tap, unless the text itself is taller. */
    private const val TOUCH_TARGET_DP = 50

    /** The gap kept below the names, clear of the navigation bar. */
    private const val ABOVE_NAVIGATION_DP = 48

    /** Left spare when closing the gaps up, so the last name can't end up a hair too low. */
    private const val SAFETY_DP = 2

    /** How far down the clock sits, with the status bar shown and hidden, and the gap kept under it. */
    private const val CLOCK_TOP_DP = 56
    private const val CLOCK_TOP_NO_STATUS_BAR_DP = 32
    private const val UNDER_CLOCK_DP = 16

    /** How far down the clock and date sit: higher up when the status bar is hidden. */
    fun clockTop(context: Context, prefs: Prefs): Int =
        dp(context, if (prefs.showStatusBar) CLOCK_TOP_DP else CLOCK_TOP_NO_STATUS_BAR_DP)

    /** One name's height at the current font, weight and size. */
    fun nameHeight(context: Context, prefs: Prefs): Float =
        lineHeight(context, R.dimen.app_name_size, prefs.appNameScale, prefs.nameFont, prefs.nameWeight)

    /**
     * Where the names start when nothing has been measured yet: under the clock and date as this
     * works them out, or near the top of the screen when both are hidden.
     */
    fun topPadding(context: Context, prefs: Prefs): Int {
        val time = if (prefs.showTime) lineHeight(context, R.dimen.clock_size, prefs.timeScale, prefs.timeFont, prefs.timeWeight) else 0f
        val date = if (prefs.showDate) lineHeight(context, R.dimen.date_size, prefs.dateScale, prefs.dateFont, prefs.dateWeight) else 0f
        if (time == 0f && date == 0f) return dp(context, ABOVE_NAVIGATION_DP)
        return clockTop(context, prefs) + ceil(time + date).toInt() + dp(context, UNDER_CLOCK_DP)
    }

    fun bottomPadding(context: Context): Int = dp(context, ABOVE_NAVIGATION_DP)

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
        touchTarget = dp(context, TOUCH_TARGET_DP),
        safety = dp(context, SAFETY_DP),
    )

    /**
     * The arithmetic behind the gap, in pixels, so it can be tested on its own. Small names stay
     * under [usual] rather than being padded out to [touchTarget]: the row is then shorter than
     * that, which is what asking for small names means.
     */
    internal fun namePadding(count: Int, nameHeight: Float, room: Float, usual: Int, touchTarget: Int, safety: Int): Int {
        if (count <= 0 || nameHeight <= 0f) return usual
        val toFit = floor(((room - safety) / count - nameHeight) / 2).toInt()
        val tightest = floor((maxOf(touchTarget.toFloat(), nameHeight) - nameHeight) / 2)
            .toInt().coerceIn(0, usual)
        return toFit.coerceIn(tightest, usual)
    }

    /** Where the names start, under a clock and date that have been measured. */
    fun topPadding(context: Context, prefs: Prefs, clockHeight: Int): Int {
        if (!prefs.showTime && !prefs.showDate) return dp(context, ABOVE_NAVIGATION_DP)
        return clockTop(context, prefs) + clockHeight + dp(context, UNDER_CLOCK_DP)
    }

    /** How many names fit on screen, with their gaps closed up as far as they go. */
    fun namesThatFit(context: Context, prefs: Prefs): Int {
        val row = tightestRow(context, prefs)
        if (row <= 0f) return Prefs.MAX_HOME_APPS
        return floor(available(context, prefs) / row).toInt().coerceAtLeast(0)
    }

    // The shortest a name's row goes: still easy to tap, or the text itself where that's taller
    private fun tightestRow(context: Context, prefs: Prefs): Float =
        maxOf(dp(context, TOUCH_TARGET_DP).toFloat(), nameHeight(context, prefs))

    // The room between the clock and the navigation bar: the launcher knows how tall its own
    // window is, and settings works from the screen's, which is close enough
    private fun available(context: Context, prefs: Prefs, height: Int = 0): Float {
        val screen = if (height > 0) height else screenHeight(context)
        return (screen - topPadding(context, prefs) - bottomPadding(context)).toFloat().coerceAtLeast(0f)
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
