package io.github.pyrocklast.mot.util

import android.content.Context
import android.icu.text.DateFormat
import android.icu.text.NumberFormat
import android.os.BatteryManager
import io.github.pyrocklast.mot.data.Prefs
import java.util.Date

/**
 * The home screen's date, in the app's language (or the phone's, if none is set) and that
 * language's usual order: short ("lun. 2 nov."), full ("lundi 2 novembre") or numeric
 * ("lun. 02/11"). Always with the weekday, never the year.
 */
object DateText {

    fun date(context: Context, style: Int, date: Date = Date()): String {
        val skeleton = when (style) {
            Prefs.DATE_FULL -> "EEEEdMMMM"
            Prefs.DATE_NUMERIC -> "EEEdM"
            else -> "EEEdMMM"
        }
        return DateFormat.getInstanceForSkeleton(skeleton, locale(context)).format(date)
    }

    /** The battery level, e.g. "85 %" in French, for when the status bar is hidden. */
    fun battery(context: Context): String? {
        val level = context.getSystemService(BatteryManager::class.java)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (level !in 0..100) return null
        return NumberFormat.getPercentInstance(locale(context)).format(level / 100.0)
    }

    // The app's resources already use the language chosen for Mot in Android's settings, if any
    private fun locale(context: Context) = context.resources.configuration.locales[0]
}
