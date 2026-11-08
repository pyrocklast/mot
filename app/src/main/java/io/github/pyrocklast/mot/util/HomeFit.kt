package io.github.pyrocklast.mot.util

import android.content.Context
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.Prefs
import kotlin.math.ceil

/**
 * How much room the home screen's names take, and how much it has for them. Text sizes follow
 * Android's font size and saved settings. The launcher measures actual rows and available space,
 * centers the visible group and never draws an app row that cannot fit.
 */
object HomeFit {

    /** The gap above and below a name when there's room for it. */
    private const val USUAL_PADDING_DP = 8

    /** A name's row is never shorter than this, so it's always easy to tap, however small the text. */
    private const val TOUCH_TARGET_DP = 48

    /** The gap kept below the names, under the navigation bar wherever it ends. */
    private const val ABOVE_NAVIGATION_DP = 8

    /** How far the clock sits below the status bar, and the gap kept under the date. */
    private const val CLOCK_TOP_DP = 24
    private const val UNDER_CLOCK_DP = 20

    /**
     * How far down the clock and date sit: below the status bar and any cutout, by [topInset].
     * Hiding the status bar takes its inset away, so they move up into its space on their own.
     */
    fun clockTop(context: Context, topInset: Int): Int = topInset + dp(context, CLOCK_TOP_DP)

    /** Shared displayed app-name size for home and drawer, including user and system scaling. */
    fun appNameSizePx(context: Context, prefs: Prefs): Float =
        context.resources.getDimension(R.dimen.app_name_size) * prefs.appNameScale

    /** Fixed, locale-independent typography that still follows Android system font scaling. */
    fun timeSizePx(context: Context): Float =
        context.resources.getDimension(R.dimen.clock_size)

    fun dateSizePx(context: Context): Float =
        context.resources.getDimension(R.dimen.date_size)

    /** Where the names stop: clear of the navigation bar, wherever Android puts it. */
    fun bottomPadding(context: Context, bottomInset: Int): Int = bottomInset + dp(context, ABOVE_NAVIGATION_DP)

    /** Fixed, comfortable padding: it never shrinks to squeeze additional apps onto Home. */
    fun rowPadding(context: Context, textHeight: Float): Int = rowPadding(
        textHeight,
        usual = dp(context, USUAL_PADDING_DP),
        touchTarget = ceil(context.resources.displayMetrics.density * TOUCH_TARGET_DP).toInt(),
    )

    internal fun rowPadding(textHeight: Float, usual: Int, touchTarget: Int): Int =
        maxOf(usual, ceil((touchTarget - textHeight) / 2f).toInt().coerceAtLeast(0))

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
        // Balance *below* the information block, not around the whole display midpoint.
        // Otherwise the clock can push a large app group high up against itself.
        val usableTop = maxOf(topInset, minimumTop)
        val usableBottom = (screenHeight - bottomInset).coerceAtLeast(usableTop)
        val centered = usableTop + (usableBottom - usableTop - groupHeight) / 2
        val latest = (usableBottom - groupHeight).coerceAtLeast(usableTop)
        return centered.coerceIn(usableTop, latest)
    }

    /** All rows are complete; there is no overflow shortcut and no compressed spacing. */
    internal fun fittingCount(heights: List<Int>, room: Int): Int {
        var left = room.coerceAtLeast(0).toLong()
        var count = 0
        for (height in heights) {
            if (height <= 0 || height > left) break
            left -= height
            count++
        }
        return count
    }

    /** Number of normal-height one-line rows that can fit at the current measured font size. */
    internal fun capacity(room: Int, rowHeight: Int): Int =
        if (rowHeight <= 0) 0 else room.coerceAtLeast(0) / rowHeight

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
