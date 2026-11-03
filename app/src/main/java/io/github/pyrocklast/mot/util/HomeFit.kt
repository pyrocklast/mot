package io.github.pyrocklast.mot.util

import android.content.Context
import android.graphics.Paint
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.Prefs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * How much room the home screen's names need, and how much it has. The sizes follow Android's
 * font size as well as your own, so what fits changes from phone to phone: this works it out
 * rather than assuming, so the home screen can close up the gaps between names when they're
 * tight, and settings can say when a number of apps won't fit.
 */
object HomeFit {

    /** The gap above and below a name when there's room to spare, and the least it closes up to. */
    private const val ROOM_TO_SPARE_PADDING_DP = 10
    private const val TIGHTEST_PADDING_DP = 2

    /** Where the names start when the clock is shown, and the gap kept below them. */
    private const val BELOW_CLOCK_DP = 112
    private const val ABOVE_NAVIGATION_DP = 48

    /** How far down the clock sits, with the status bar shown and hidden, and the gap kept under it. */
    private const val CLOCK_TOP_DP = 56
    private const val CLOCK_TOP_NO_STATUS_BAR_DP = 32
    private const val UNDER_CLOCK_DP = 8

    /** How far down the clock and date sit: higher up when the status bar is hidden. */
    fun clockTop(context: Context, prefs: Prefs): Int =
        dp(context, if (prefs.showStatusBar) CLOCK_TOP_DP else CLOCK_TOP_NO_STATUS_BAR_DP)

    /** One name's height at the current font, weight and size. */
    fun nameHeight(context: Context, prefs: Prefs): Float =
        lineHeight(context, R.dimen.app_name_size, prefs.appNameScale, prefs.nameFont, prefs.nameWeight)

    /**
     * Where the names start: under the clock and date, or near the top of the screen when they're
     * hidden. Never lower than it would be with the clock at its usual size.
     */
    fun topPadding(context: Context, prefs: Prefs): Int {
        val time = if (prefs.showTime) lineHeight(context, R.dimen.clock_size, prefs.timeScale, prefs.timeFont, prefs.timeWeight) else 0f
        val date = if (prefs.showDate) lineHeight(context, R.dimen.date_size, prefs.dateScale, prefs.dateFont, prefs.dateWeight) else 0f
        if (time == 0f && date == 0f) return dp(context, ABOVE_NAVIGATION_DP)
        val under = clockTop(context, prefs) + ceil(time + date).toInt() + dp(context, UNDER_CLOCK_DP)
        return minOf(dp(context, BELOW_CLOCK_DP), under)
    }

    fun bottomPadding(context: Context): Int = dp(context, ABOVE_NAVIGATION_DP)

    /**
     * The gap above and below each name: the usual one, closed up as far as [TIGHTEST_PADDING_DP]
     * when [count] names wouldn't otherwise fit in [height] pixels of screen.
     */
    fun namePadding(context: Context, prefs: Prefs, count: Int, height: Int): Int {
        val roomToSpare = dp(context, ROOM_TO_SPARE_PADDING_DP)
        if (count <= 0) return roomToSpare
        val room = available(context, prefs, height)
        val fits = floor((room / count - nameHeight(context, prefs)) / 2).toInt()
        return fits.coerceIn(dp(context, TIGHTEST_PADDING_DP), roomToSpare)
    }

    /** How many names fit on screen, with the gaps between them closed up as far as they go. */
    fun namesThatFit(context: Context, prefs: Prefs): Int {
        val row = nameHeight(context, prefs) + 2 * dp(context, TIGHTEST_PADDING_DP)
        if (row <= 0f) return Prefs.MAX_HOME_APPS
        return floor(available(context, prefs, screenHeight(context)) / row).toInt().coerceAtLeast(0)
    }

    // The screen between the clock and the navigation bar. Settings works from the screen's own
    // height, which is close enough to the launcher's window.
    private fun available(context: Context, prefs: Prefs, height: Int): Float {
        val screen = if (height > 0) height else screenHeight(context)
        return (screen - topPadding(context, prefs) - bottomPadding(context)).toFloat().coerceAtLeast(0f)
    }

    private fun screenHeight(context: Context): Int = context.resources.displayMetrics.heightPixels

    // A line of text at one of the launcher's sizes, scaled as set
    private fun lineHeight(context: Context, size: Int, scale: Float, font: Int, weight: Int): Float {
        val paint = Paint()
        paint.typeface = Look.typeface(font, weight)
        paint.textSize = context.resources.getDimension(size) * scale
        val metrics = paint.fontMetrics
        return metrics.descent - metrics.ascent
    }

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
