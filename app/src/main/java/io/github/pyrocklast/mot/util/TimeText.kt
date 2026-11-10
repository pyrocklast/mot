package io.github.pyrocklast.mot.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.icu.text.SimpleDateFormat
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.format.DateFormat
import android.text.style.MetricAffectingSpan
import android.text.style.ReplacementSpan
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The home screen's time, in the phone's 12- or 24-hour setting and the app's language:
 * "20:06", or "9:41 pm" and "12:59 PM". The colon goes up to the middle of the figures, which it
 * sits below in the font. AM/PM follows on the baseline in the date's size and weight, as Android
 * writes it for the language, and as far after the time as the date is below it.
 */
object TimeText {

    // Patterns are kept, as DateText keeps its format: the home screen asks every minute.
    // Only ever used from the main thread.
    private var lastKey: Pair<Locale, Boolean>? = null
    private var lastFormats: Pair<SimpleDateFormat, SimpleDateFormat?>? = null

    /**
     * The time [date] as the home screen shows it. [colonLift] and [markerGap] are in pixels,
     * [markerSize] the date's size.
     */
    fun time(
        context: Context,
        colonLift: Float,
        markerSize: Float,
        markerGap: Int,
        date: Date = Date(),
    ): CharSequence {
        val (time, marker) = formats(locale(context), DateFormat.is24HourFormat(context))
        val text = SpannableStringBuilder(time.format(date))
        val colon = text.indexOf(':')
        if (colon >= 0) text.setSpan(Raise(colonLift), colon, colon + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (marker != null) {
            val start = text.length
            // A space, so TalkBack reads "9:41 pm"; the span draws the gap itself
            text.append(' ').append(marker.format(date))
            text.setSpan(Marker(markerSize, markerGap), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return text
    }

    /**
     * The time's pattern for [locale] ("HH:mm", "h:mm"), and for 12-hour time, AM/PM's ("a"). For
     * English and French, the only languages the app has, AM/PM comes after the time.
     */
    private fun formats(locale: Locale, is24: Boolean): Pair<SimpleDateFormat, SimpleDateFormat?> {
        val key = locale to is24
        lastFormats?.let { if (key == lastKey) return it }
        val pattern = DateFormat.getBestDateTimePattern(locale, if (is24) "Hm" else "hm")
        val time = pattern.replace("a", "").trim { it.isWhitespace() || it == ' ' }
        val formats = SimpleDateFormat(time, locale) to (if (is24) null else SimpleDateFormat("a", locale))
        lastKey = key
        lastFormats = formats
        return formats
    }

    private fun locale(context: Context) = context.resources.configuration.locales[0]

    /** Moves text up by [lift] pixels: the colon, to the middle of the figures. */
    private class Raise(private val lift: Float) : MetricAffectingSpan() {
        override fun updateDrawState(paint: TextPaint) = raise(paint)
        override fun updateMeasureState(paint: TextPaint) = raise(paint)
        private fun raise(paint: TextPaint) {
            paint.baselineShift -= lift.roundToInt()
        }
    }

    /**
     * AM/PM: [gap] pixels of space, then the text in the date's [size] and regular weight, on the
     * time's baseline, in the time's colour and shadow.
     */
    private class Marker(private val size: Float, private val gap: Int) : ReplacementSpan() {
        private val regular = Typeface.create(Typeface.SANS_SERIF, 400, false)

        private fun paint(time: Paint) = TextPaint(time).apply {
            typeface = regular
            textSize = size
            letterSpacing = 0f
            fontFeatureSettings = null
        }

        // The space before AM/PM isn't drawn: the gap stands for it
        private fun marker(text: CharSequence, start: Int, end: Int) = text.subSequence(start, end).trim()

        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
            // The line keeps the time's height
            if (fm != null) paint.getFontMetricsInt(fm)
            val marker = marker(text, start, end)
            return gap + paint(paint).measureText(marker, 0, marker.length).roundToInt()
        }

        override fun draw(
            canvas: Canvas, text: CharSequence, start: Int, end: Int,
            x: Float, top: Int, y: Int, bottom: Int, paint: Paint,
        ) {
            val marker = marker(text, start, end)
            canvas.drawText(marker, 0, marker.length, x + gap, y.toFloat(), paint(paint))
        }
    }
}
