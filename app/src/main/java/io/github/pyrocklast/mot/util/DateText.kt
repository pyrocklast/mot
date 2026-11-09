package io.github.pyrocklast.mot.util

import android.content.Context
import android.icu.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * The home screen's date, short, in the app's language (or the phone's, if none is set) and that
 * language's usual order: "lun. 2 nov.", "Mon 2 Nov". Always with the weekday, never the year.
 */
object DateText {

    // Working a format out from its skeleton is the expensive part, and the home screen asks for
    // the same one every minute, so the last one is kept. Only ever used from the main thread,
    // which DateFormat needs.
    private var lastLocale: Locale? = null
    private var lastFormat: DateFormat? = null

    fun date(context: Context, date: Date = Date()): String = format(locale(context)).format(date)

    private fun format(locale: Locale): DateFormat {
        lastFormat?.let { if (locale == lastLocale) return it }
        return DateFormat.getInstanceForSkeleton("EEEdMMM", locale).also {
            lastLocale = locale
            lastFormat = it
        }
    }

    // The app's resources already use the language chosen for mot launcher in Android's settings, if any
    private fun locale(context: Context) = context.resources.configuration.locales[0]
}
