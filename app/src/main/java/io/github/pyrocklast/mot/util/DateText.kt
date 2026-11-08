package io.github.pyrocklast.mot.util

import android.content.Context
import android.icu.text.DateFormat
import io.github.pyrocklast.mot.data.Prefs
import java.util.Date
import java.util.Locale

/**
 * The home screen's date, in the app's language (or the phone's, if none is set) and that
 * language's usual order: short ("lun. 2 nov."), full ("lundi 2 novembre") or numeric
 * ("lun. 02/11"). Always with the weekday, never the year.
 */
object DateText {

    // Working a format out from its skeleton is the expensive part, and the home screen asks for
    // the same one every minute, so the last one is kept. Only ever used from the main thread,
    // which DateFormat needs.
    private var lastStyle = -1
    private var lastLocale: Locale? = null
    private var lastFormat: DateFormat? = null

    /** System-localized, compact date with weekday; no user-specific format setting. */
    fun date(context: Context, date: Date = Date()): String =
        format(Prefs.DATE_SHORT, locale(context)).format(date)

    /** Kept for old tests/imports; the launcher always uses the compact locale-aware style. */
    fun date(context: Context, style: Int, date: Date = Date()): String =
        format(style, locale(context)).format(date)

    private fun format(style: Int, locale: Locale): DateFormat {
        lastFormat?.let { if (style == lastStyle && locale == lastLocale) return it }
        val skeleton = when (style) {
            Prefs.DATE_FULL -> "EEEEdMMMM"
            Prefs.DATE_NUMERIC -> "EEEdM"
            else -> "EEEdMMM"
        }
        return DateFormat.getInstanceForSkeleton(skeleton, locale).also {
            lastStyle = style
            lastLocale = locale
            lastFormat = it
        }
    }

    // The app's resources already use the language chosen for mot launcher in Android's settings, if any
    private fun locale(context: Context) = context.resources.configuration.locales[0]
}
