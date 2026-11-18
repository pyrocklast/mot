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
 * - The drawer's search box sits on the time's baseline, so that opening the drawer puts
 *   "Search" on the line the time was on, whatever size names are.
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

    /** The least anything you tap may be. */
    private const val TOUCH_TARGET_DP = 48

    /** A 48 dp touch target and 8 dp to the next: rows are never closer than this. */
    private const val MIN_ROW_DP = 56

    /** Above and below a name, inside its row. */
    private const val NAME_PADDING_DP = 8

    /** Left over at the bottom, so the last row can't end up a hair too low. */
    private const val SAFETY_DP = 2

    /** Between one column of names and the next, and between the time and the first of them. */
    private const val COLUMN_GAP_DP = 56

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
        /** From the top of the screen to the first thing you see: the time's figures. */
        val inkTop: Int,
        /** Where the names may start, under the time and date. */
        val namesTop: Int,
        /** Between one column of names and the next, and between the time and the first. */
        val columnGap: Int,
        /** Left spare at the bottom of a column, so its last row can't end up a hair too low. */
        val safety: Int,
        /** Where the drawer's search box goes, so its text sits on the time's line. */
        val searchTop: Int,
        /** How tall the search box is: its text, or a 48 dp target, whichever is more. */
        val searchHeight: Int,
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
        val inkTop = topInset + px(CLOCK_TOP_DP)
        val clock = placeClock(
            inkTop = inkTop,
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
        val nameHeight = ceil((name.top + name.bottom) * nameSize).toInt()
        // Worth tapping even when names are small, to put the keyboard back after scrolling
        val searchHeight = maxOf(px(TOUCH_TARGET_DP), nameHeight)
        val row = rowHeight(nameHeight, px(MIN_ROW_DP), px(NAME_PADDING_DP))
        return Layout(
            timeSize = timeSize,
            dateSize = dateSize,
            nameSize = nameSize,
            clockTop = clock.top,
            dateMargin = clock.dateMargin,
            inkTop = inkTop,
            columnGap = px(COLUMN_GAP_DP),
            safety = px(SAFETY_DP),
            markerGap = gap,
            colonLift = time.colonLift * timeSize,
            namesTop = clock.namesTop,
            searchTop = searchTop(
                headerBaseline = clock.headerBaseline,
                inkTop = inkTop,
                baseline = ceil(name.top * nameSize).toInt(),
                capitals = (name.cap * nameSize).roundToInt(),
                textHeight = nameHeight,
                boxHeight = searchHeight,
            ),
            searchHeight = searchHeight,
            bottom = bottom,
            row = row,
            capacity = slots(height - clock.namesTop - bottom, row, px(SAFETY_DP)),
        )
    }

    /**
     * Where the time and date's views go, where the names may start, and the baseline of the
     * home screen's first line of text, which is null when neither the time nor the date shows.
     */
    internal data class ClockPlace(
        val top: Int, val dateMargin: Int, val namesTop: Int, val headerBaseline: Int?,
    )

    /**
     * Where the drawer's search box goes, in pixels. Its text sits on [headerBaseline], the
     * home screen's first line, so that opening the drawer puts "Search" where the time was.
     * With no time and no date there's no line to sit on, and the placeholder's capitals start
     * where the home screen's text would have started instead.
     *
     * The text has its baseline [baseline] below its own top, capitals [capitals] tall and
     * takes [textHeight]; the box around it is [boxHeight], which is more than the text at
     * small name sizes, to stay worth tapping. The text sits in the middle of it either way.
     */
    internal fun searchTop(
        headerBaseline: Int?, inkTop: Int, baseline: Int, capitals: Int, textHeight: Int, boxHeight: Int,
    ): Int {
        val textTop = if (headerBaseline != null) headerBaseline - baseline else inkTop - (baseline - capitals)
        return textTop - (boxHeight - textHeight) / 2
    }

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
                ClockPlace(top, dateMargin, baseline + gap + capitals + descenders + underClock, baseline)
            } else {
                ClockPlace(top, 0, baseline + underClock, baseline)
            }
        }
        showDate -> {
            val top = inkTop - (dateBaseline - capitals)
            ClockPlace(top, 0, top + dateBaseline + descenders + underClock, top + dateBaseline)
        }
        else -> ClockPlace(inkTop, 0, inkTop, null)
    }

    /**
     * The home screen's layout in [activity]'s window, with app names [nameSizeDp] tall. The
     * drawer asks for it to put its search box on the same line as the time, and settings asks
     * for it as you move the name size slider; the home screen measures its own view instead.
     */
    fun forWindow(activity: Activity, prefs: Prefs, nameSizeDp: Int = prefs.appNameSize): Layout {
        val window = activity.windowManager.currentWindowMetrics
        val insets = window.windowInsets
        val cutout = insets.getInsets(WindowInsets.Type.displayCutout()).top
        val statusBar = insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
        val top = maxOf(statusBar, cutout)
        val bottom = insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom
        return layout(activity, prefs, window.bounds.height(), top, bottom, nameSizeDp)
    }

    /** How many apps the home screen has room for: settings asks this as you move the slider. */
    fun capacity(activity: Activity, prefs: Prefs, nameSizeDp: Int = prefs.appNameSize): Int =
        forWindow(activity, prefs, nameSizeDp).capacity

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
        return centre(usableTop, usableBottom, groupHeight)
    }

    /** [height] in the middle of the room between [top] and [bottom]; [top] when it won't fit. */
    internal fun centre(top: Int, bottom: Int, height: Int): Int =
        top + ((bottom - top - height) / 2).coerceAtLeast(0)

    /**
     * The names' place: equal columns [columnWidth] wide from [left], each holding one of
     * [counts], all starting at the same [top] so the names line up across them. [overflow]
     * gives the last row of the last column to "All apps".
     */
    data class Names(
        val left: Int,
        val top: Int,
        val columnWidth: Int,
        val counts: List<Int>,
        val overflow: Boolean,
    ) {
        /** How many names show. */
        val shown: Int get() = counts.sum()
    }

    /**
     * How [count] names divide between columns holding [perColumn] each, there being room for
     * [maxColumns] of them.
     *
     * When they all fit they are spread evenly, so ten names in two columns are five and five
     * rather than six and four. When they don't, the columns are filled instead: a name you can
     * see is worth more than a tidy pair, and the last row goes to "All apps", which costs one
     * name its place.
     */
    internal fun arrange(count: Int, perColumn: Int, maxColumns: Int): Names =
        if (count <= 0 || perColumn <= 0 || maxColumns <= 0) {
            Names(0, 0, 0, emptyList(), false)
        } else if (count <= perColumn * maxColumns) {
            val columns = ceil(count.toFloat() / perColumn).toInt()
            val each = count / columns
            val overs = count % columns
            Names(0, 0, 0, List(columns) { each + if (it < overs) 1 else 0 }, false)
        } else {
            Names(0, 0, 0, List(maxColumns) { if (it == maxColumns - 1) perColumn - 1 else perColumn }, true)
        }

    /**
     * Where [count] names go on a screen: beside the time and date, or under them as they are on
     * a tall screen, whichever shows more of them — and the fewer columns of the two when they
     * show the same. A screen turned on its side has no room under the time for the names, but
     * has room beside it; a tall one is the other way about.
     *
     * The room runs from [left] to [right] and from [bottom] up to either [Layout.inkTop], where
     * the time starts, or [Layout.namesTop], under it. [clockWidth] is how wide the time and
     * date are, [nameWidth] the widest name.
     */
    fun names(fit: Layout, count: Int, nameWidth: Int, clockWidth: Int, left: Int, right: Int, bottom: Int): Names {
        val below = fill(left, right, fit.namesTop, bottom, fit, count, nameWidth, squeeze = true)
        val beside = fill(left + clockWidth + fit.columnGap, right, fit.inkTop, bottom, fit, count, nameWidth, squeeze = false)
        // Below first: on a tall screen both show every name, and under the time is where the
        // names belong
        return if (beside.shown > below.shown || (beside.shown == below.shown && beside.counts.size < below.counts.size)) {
            beside
        } else {
            below
        }
    }

    /**
     * One way of laying the names out, in the room from [left] to [right] and [top] to [bottom].
     * Under the time there is always room for a column, and a name too long for it is cut short,
     * there being nowhere else for it to go; beside the time a column only counts when a whole
     * name fits in it, or the names would be squeezed into a corner and clipped to get there.
     */
    private fun fill(
        left: Int, right: Int, top: Int, bottom: Int,
        fit: Layout, count: Int, nameWidth: Int, squeeze: Boolean,
    ): Names {
        val width = (right - left).coerceAtLeast(0)
        val wanted = (nameWidth + fit.columnGap).coerceAtLeast(1)
        val columnWidth = if (squeeze) minOf(wanted, maxOf(width, 1)) else wanted
        val columns = if (squeeze) maxOf(1, width / columnWidth) else width / columnWidth
        val spread = arrange(count, slots(bottom - top, fit.row, fit.safety), columns)
        val rows = spread.counts.mapIndexed { index, held ->
            held + if (spread.overflow && index == spread.counts.lastIndex) 1 else 0
        }
        val tallest = (rows.maxOrNull() ?: 0) * fit.row
        return spread.copy(left = left, top = centre(top, bottom, tallest), columnWidth = columnWidth)
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
