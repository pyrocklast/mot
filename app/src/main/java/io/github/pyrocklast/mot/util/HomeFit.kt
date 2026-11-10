package io.github.pyrocklast.mot.util

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.WindowInsets
import android.widget.TextView
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.Prefs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The home screen's layout, worked out from the screen alone: where the time and date go, how
 * tall each app's row is, and how many rows there's room for. Being only arithmetic, it also
 * tells settings and the drawer how many apps fit, without the home screen.
 *
 * Everything is in dp, so the home screen follows the screen rather than Android's text size:
 * - The time and date have one size for a 360 dp wide screen, and grow with wider ones. They're
 *   spaced by what you see, the letters and figures themselves, not the room the font keeps
 *   around them: the figures' tops 32 dp under the status bar, the date's capitals 16 dp under
 *   the time, and the names 24 dp under the date's descenders.
 * - App names have your size, 24 to 48 dp.
 * - Each row is a 48 dp touch target with 8 dp to the next, so rows are never under 56 dp
 *   apart, and more if the names need it: 8 dp above and below the text.
 */
object HomeFit {

    /** The time and date at [REFERENCE_WIDTH_DP]; wider screens get them up to [MAX_GROWTH] times bigger. */
    private const val TIME_DP = 64f
    private const val DATE_DP = 21f
    private const val REFERENCE_WIDTH_DP = 360f
    private const val MAX_GROWTH = 1.25f

    /** From the status bar to the top of the figures (or of the date's capitals, without the time). */
    private const val CLOCK_TOP_DP = 32

    /**
     * From the time's baseline to the top of the date's capitals, and from the time to AM/PM.
     * It grows with the time and date.
     */
    private const val CLOCK_GAP_DP = 16f

    /** From the bottom of the date's descenders (or of the figures, without the date) to where names may start. */
    private const val UNDER_CLOCK_DP = 24

    /** The room kept above the navigation bar. */
    private const val ABOVE_NAVIGATION_DP = 8

    /** A 48 dp touch target and 8 dp to the next: rows are never closer than this. */
    private const val MIN_ROW_DP = 56

    /** Above and below a name, inside its row. */
    private const val NAME_PADDING_DP = 8

    /** Left over at the bottom, so the last row can't end up a hair too low. */
    private const val SAFETY_DP = 2

    /** The "All apps" row, smaller than the names so it doesn't read as an app. */
    const val ALL_APPS_DP = 18f

    /** Where things go on a screen, in pixels. */
    data class Layout(
        val timeSize: Float,
        val dateSize: Float,
        val nameSize: Float,
        /** From the top of the screen to the top of the time's view (or the date's, without the time). */
        val clockTop: Int,
        /** Above the date's view, to bring its capitals [CLOCK_GAP_DP] under the time; usually negative. */
        val dateMargin: Int,
        /** Between the time and AM/PM. */
        val markerGap: Int,
        /** How far up the colon goes, so it sits in the middle of the figures. */
        val colonLift: Float,
        /** Where the names may start, under the time and date. */
        val namesTop: Int,
        /** Kept free at the bottom, above the navigation bar. */
        val bottom: Int,
        /** Each row's height. */
        val row: Int,
        /** How many rows the screen has room for. */
        val capacity: Int,
    )

    /**
     * The layout of a home screen [height] pixels tall, with the status bar (and any cutout)
     * [topInset] tall and the navigation bar [bottomInset], and app names [nameSizeDp] tall.
     */
    fun layout(
        context: Context, prefs: Prefs, height: Int, topInset: Int, bottomInset: Int,
        nameSizeDp: Int = prefs.appNameSize,
    ): Layout {
        val density = context.resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).roundToInt()
        val growth = growth(context.resources.configuration.smallestScreenWidthDp)
        val timeSize = TIME_DP * growth * density
        val dateSize = DATE_DP * growth * density
        val nameSize = nameSizeDp * density

        val time = metrics(context, R.style.Mot_Text_Clock)
        val date = metrics(context, R.style.Mot_Text_Date)
        val name = metrics(context, R.style.Mot_Text_AppName)

        val gap = (CLOCK_GAP_DP * growth * density).roundToInt()
        val clock = placeClock(
            inkTop = topInset + px(CLOCK_TOP_DP),
            showTime = prefs.showTime,
            showDate = prefs.showDate,
            // A TextView puts one line's baseline the font's top below its own top, and ends the
            // font's bottom below it
            timeBaseline = ceil(time.top * timeSize).toInt(),
            timeBottom = ceil(time.bottom * timeSize).toInt(),
            figures = (time.figures * timeSize).roundToInt(),
            dateBaseline = ceil(date.top * dateSize).toInt(),
            capitals = (date.cap * dateSize).roundToInt(),
            descenders = (date.descenders * dateSize).roundToInt(),
            gap = gap,
            underClock = px(UNDER_CLOCK_DP),
        )
        val bottom = bottomInset + px(ABOVE_NAVIGATION_DP)
        val row = rowHeight(ceil((name.top + name.bottom) * nameSize).toInt(), px(MIN_ROW_DP), px(NAME_PADDING_DP))
        return Layout(
            timeSize = timeSize,
            dateSize = dateSize,
            nameSize = nameSize,
            clockTop = clock.top,
            dateMargin = clock.dateMargin,
            markerGap = gap,
            colonLift = time.colonLift * timeSize,
            namesTop = clock.namesTop,
            bottom = bottom,
            row = row,
            capacity = slots(height - clock.namesTop - bottom, row, px(SAFETY_DP)),
        )
    }

    /** Where the time and date's views go, and where the names may start. */
    internal data class ClockPlace(val top: Int, val dateMargin: Int, val namesTop: Int)

    /**
     * The arithmetic of placing the time and date, in pixels, so it can be tested on its own.
     * [inkTop] is where the first thing you see starts. The time's view has its baseline
     * [timeBaseline] below its top and ends [timeBottom] below the baseline; its figures are
     * [figures] tall. The date's view has its baseline [dateBaseline] below its top, capitals
     * [capitals] tall and descenders [descenders] deep.
     */
    internal fun placeClock(
        inkTop: Int, showTime: Boolean, showDate: Boolean,
        timeBaseline: Int, timeBottom: Int, figures: Int,
        dateBaseline: Int, capitals: Int, descenders: Int,
        gap: Int, underClock: Int,
    ): ClockPlace = when {
        showTime -> {
            val top = inkTop - (timeBaseline - figures)
            val baseline = top + timeBaseline
            if (showDate) {
                // From the bottom of the time's view to the date's view, so that the date's
                // capitals start [gap] under the time's baseline
                val dateMargin = gap + capitals - timeBottom - dateBaseline
                ClockPlace(top, dateMargin, baseline + gap + capitals + descenders + underClock)
            } else {
                ClockPlace(top, 0, baseline + underClock)
            }
        }
        showDate -> {
            val top = inkTop - (dateBaseline - capitals)
            ClockPlace(top, 0, top + dateBaseline + descenders + underClock)
        }
        else -> ClockPlace(inkTop, 0, inkTop)
    }

    /**
     * How many apps the home screen has room for, worked out from [activity]'s window, as the
     * home screen would be with the status bar shown or hidden as set, and app names
     * [nameSizeDp] tall: settings asks this as you move the size slider.
     */
    fun capacity(activity: Activity, prefs: Prefs, nameSizeDp: Int = prefs.appNameSize): Int {
        val window = activity.windowManager.currentWindowMetrics
        val insets = window.windowInsets
        val cutout = insets.getInsets(WindowInsets.Type.displayCutout()).top
        val statusBar = insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
        val top = if (prefs.showStatusBar) maxOf(statusBar, cutout) else cutout
        val bottom = insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom
        return layout(activity, prefs, window.bounds.height(), top, bottom, nameSizeDp).capacity
    }

    /** App names' size, on the home screen and in the drawer: yours, in dp. */
    fun appNameSizePx(context: Context, prefs: Prefs): Float =
        prefs.appNameSize * context.resources.displayMetrics.density

    /** How much bigger than on a 360 dp screen the time and date are on one [widthDp] wide. */
    internal fun growth(widthDp: Int): Float = (widthDp / REFERENCE_WIDTH_DP).coerceIn(1f, MAX_GROWTH)

    /** A row: the name and the padding around it, or the least a row may be, whichever is more. */
    internal fun rowHeight(nameHeight: Int, minRow: Int, padding: Int): Int = maxOf(minRow, nameHeight + 2 * padding)

    /** How many rows of [row] fit in [room], keeping [safety] spare. */
    internal fun slots(room: Int, row: Int, safety: Int): Int =
        if (row <= 0) 0 else ((room - safety) / row).coerceAtLeast(0)

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
     * [count] names in [slots] rows. When there are more names than rows, which can happen after
     * making names bigger, say, the last row is "All apps" instead.
     */
    internal fun visibleRows(count: Int, slots: Int): VisibleRows = when {
        count <= slots -> VisibleRows(count, false)
        slots <= 0 -> VisibleRows(0, false)
        else -> VisibleRows(slots - 1, true)
    }

    /**
     * A font's proportions, in ems: the room it keeps above the baseline and below it, how tall
     * its capitals and figures are, how deep its descenders go, and how far its colon sits below
     * the middle of its figures.
     */
    private class Metrics(
        val top: Float,
        val bottom: Float,
        val cap: Float,
        val figures: Float,
        val descenders: Float,
        val colonLift: Float,
    )

    private val measured = HashMap<Int, Metrics>()

    // Read once from a TextView in the launcher's style, so they're exactly what the home
    // screen lays out, whatever Android does with the font
    private fun metrics(context: Context, style: Int): Metrics = measured.getOrPut(style) {
        val view = TextView(ContextThemeWrapper(context, R.style.Theme_Mot_Launcher), null, 0, style)
        val size = 1000f
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        val font = view.paint.fontMetrics
        fun ink(text: String) = Rect().also { view.paint.getTextBounds(text, 0, text.length, it) }
        val capital = ink("H")
        val figures = ink("0123456789")
        val colon = ink(":")
        Metrics(
            top = -font.top / size,
            bottom = font.bottom / size,
            cap = -capital.top / size,
            figures = -figures.top / size,
            descenders = ink("gjpqy").bottom / size,
            colonLift = ((colon.top + colon.bottom) - (figures.top + figures.bottom)) / 2f / size,
        )
    }
}
