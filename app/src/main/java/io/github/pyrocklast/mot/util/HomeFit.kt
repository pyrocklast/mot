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
 * - The time and date have one size for a 360 dp wide screen, and grow with wider ones.
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

    /** From the time's baseline to the top of the date's capitals, in date sizes. */
    private const val DATE_GAP = 0.75f

    /** How far the time sits below the status bar, and the room kept under the date. */
    private const val CLOCK_TOP_DP = 24
    private const val UNDER_CLOCK_DP = 20

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
        /** From the top of the screen to the top of the time. */
        val clockTop: Int,
        /** Above the date, to bring its capitals [DATE_GAP] under the time; usually negative. */
        val dateMargin: Int,
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
     * [topInset] tall and the navigation bar [bottomInset].
     */
    fun layout(context: Context, prefs: Prefs, height: Int, topInset: Int, bottomInset: Int): Layout {
        val density = context.resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).roundToInt()
        val growth = growth(context.resources.configuration.smallestScreenWidthDp)
        val timeSize = TIME_DP * growth * density
        val dateSize = DATE_DP * growth * density
        val nameSize = appNameSizePx(context, prefs)

        val time = metrics(context, R.style.Mot_Text_Clock)
        val date = metrics(context, R.style.Mot_Text_Date)
        val name = metrics(context, R.style.Mot_Text_AppName)

        // Heights as a TextView lays out one line: from the font's top to its bottom
        val timeTop = ceil(time.top * timeSize).toInt()
        val timeBottom = ceil(time.bottom * timeSize).toInt()
        val dateTop = ceil(date.top * dateSize).toInt()
        val dateBottom = ceil(date.bottom * dateSize).toInt()
        val dateMargin = if (prefs.showTime) {
            ((DATE_GAP + date.cap) * dateSize).roundToInt() - timeBottom - dateTop
        } else 0
        val clock = (if (prefs.showTime) timeTop + timeBottom else 0) +
            (if (prefs.showDate) dateMargin + dateTop + dateBottom else 0)

        val clockTop = topInset + px(CLOCK_TOP_DP)
        val namesTop = clockTop + if (prefs.showTime || prefs.showDate) clock + px(UNDER_CLOCK_DP) else 0
        val bottom = bottomInset + px(ABOVE_NAVIGATION_DP)
        val row = rowHeight(ceil((name.top + name.bottom) * nameSize).toInt(), px(MIN_ROW_DP), px(NAME_PADDING_DP))
        return Layout(
            timeSize = timeSize,
            dateSize = dateSize,
            nameSize = nameSize,
            clockTop = clockTop,
            dateMargin = dateMargin,
            namesTop = namesTop,
            bottom = bottom,
            row = row,
            capacity = slots(height - namesTop - bottom, row, px(SAFETY_DP)),
        )
    }

    /**
     * How many apps the home screen has room for, worked out from [activity]'s window, as the
     * home screen would be with the status bar shown or hidden as set.
     */
    fun capacity(activity: Activity, prefs: Prefs): Int {
        val window = activity.windowManager.currentWindowMetrics
        val insets = window.windowInsets
        val cutout = insets.getInsets(WindowInsets.Type.displayCutout()).top
        val statusBar = insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
        val top = if (prefs.showStatusBar) maxOf(statusBar, cutout) else cutout
        val bottom = insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom
        return layout(activity, prefs, window.bounds.height(), top, bottom).capacity
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

    /** A font's proportions, in ems: above the baseline, below it, and its capitals' height. */
    private class Metrics(val top: Float, val bottom: Float, val cap: Float)

    private val measured = HashMap<Int, Metrics>()

    // Read once from a TextView in the launcher's style, so they're exactly what the home
    // screen lays out, whatever Android does with the font
    private fun metrics(context: Context, style: Int): Metrics = measured.getOrPut(style) {
        val view = TextView(ContextThemeWrapper(context, R.style.Theme_Mot_Launcher), null, 0, style)
        val size = 1000f
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        val font = view.paint.fontMetrics
        val capital = Rect().also { view.paint.getTextBounds("H", 0, 1, it) }
        Metrics(top = -font.top / size, bottom = font.bottom / size, cap = capital.height() / size)
    }
}
