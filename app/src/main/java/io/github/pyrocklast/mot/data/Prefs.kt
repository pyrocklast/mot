package io.github.pyrocklast.mot.data

import android.content.Context
import android.view.Gravity
import androidx.core.content.edit

/**
 * All of Mot's settings. Names chosen for apps live in their own file, keyed by [AppTarget.key].
 * Nothing here stores a copy of an app's name: names always come from [AppRepository].
 */
class Prefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mot", Context.MODE_PRIVATE)
    private val names = context.applicationContext.getSharedPreferences("mot_names", Context.MODE_PRIVATE)

    // Home screen

    var homeAppCount: Int
        get() = prefs.getInt("home_app_count", 4)
        set(value) = prefs.edit { putInt("home_app_count", value.coerceIn(0, MAX_HOME_APPS)) }

    fun homeApp(slot: Int): AppTarget? = AppTarget.decode(prefs.getString("home_app_$slot", null))

    fun setHomeApp(slot: Int, target: AppTarget?) = prefs.edit { putTarget("home_app_$slot", target) }

    val homeApps: List<AppTarget?> get() = (0 until MAX_HOME_APPS).map { homeApp(it) }

    var homeAlignment: Int
        get() = prefs.getInt("home_alignment", Gravity.START)
        set(value) = prefs.edit { putInt("home_alignment", value) }

    var homeAtBottom: Boolean
        get() = prefs.getBoolean("home_at_bottom", false)
        set(value) = prefs.edit { putBoolean("home_at_bottom", value) }

    // Clock and date

    var clockMode: Int
        get() = prefs.getInt("clock_mode", CLOCK_TIME_AND_DATE)
        set(value) = prefs.edit { putInt("clock_mode", value) }

    val showsTime: Boolean get() = clockMode == CLOCK_TIME_AND_DATE
    val showsDate: Boolean get() = clockMode != CLOCK_OFF

    var dateStyle: Int
        get() = prefs.getInt("date_style", DATE_SHORT)
        set(value) = prefs.edit { putInt("date_style", value) }

    var clockScale: Float
        get() = prefs.getFloat("clock_scale", 1f)
        set(value) = prefs.edit { putFloat("clock_scale", value) }

    /** The app tapping the time opens; null for Android's default clock app. */
    var clockApp: AppTarget?
        get() = AppTarget.decode(prefs.getString("clock_app", null))
        set(value) = prefs.edit { putTarget("clock_app", value) }

    /** The app tapping the date opens; null for Android's default calendar app. */
    var calendarApp: AppTarget?
        get() = AppTarget.decode(prefs.getString("calendar_app", null))
        set(value) = prefs.edit { putTarget("calendar_app", value) }

    // App drawer

    var drawerAlignment: Int
        get() = prefs.getInt("drawer_alignment", Gravity.START)
        set(value) = prefs.edit { putInt("drawer_alignment", value) }

    var autoKeyboard: Boolean
        get() = prefs.getBoolean("auto_keyboard", true)
        set(value) = prefs.edit { putBoolean("auto_keyboard", value) }

    /** Keys ([AppTarget.key]) of the apps hidden from the drawer. */
    var hiddenApps: Set<String>
        get() = prefs.getStringSet("hidden_apps", null).orEmpty().toSet()
        set(value) = prefs.edit { putStringSet("hidden_apps", value) }

    var hiddenBefore: Boolean
        get() = prefs.getBoolean("hidden_before", false)
        set(value) = prefs.edit { putBoolean("hidden_before", value) }

    // Appearance (home screen and drawer only)

    var textColour: Int
        get() = prefs.getInt("text_colour", TEXT_LIGHT)
        set(value) = prefs.edit { putInt("text_colour", value) }

    var appNameScale: Float
        get() = prefs.getFloat("app_name_scale", 1f)
        set(value) = prefs.edit { putFloat("app_name_scale", value) }

    var bolderNames: Boolean
        get() = prefs.getBoolean("bolder_names", false)
        set(value) = prefs.edit { putBoolean("bolder_names", value) }

    var showStatusBar: Boolean
        get() = prefs.getBoolean("show_status_bar", false)
        set(value) = prefs.edit { putBoolean("show_status_bar", value) }

    // Gestures

    var swipeLeftOn: Boolean
        get() = prefs.getBoolean("swipe_left_on", true)
        set(value) = prefs.edit { putBoolean("swipe_left_on", value) }

    var swipeRightOn: Boolean
        get() = prefs.getBoolean("swipe_right_on", true)
        set(value) = prefs.edit { putBoolean("swipe_right_on", value) }

    /** The app swiping left opens; null for Android's default camera app. */
    var swipeLeftApp: AppTarget?
        get() = AppTarget.decode(prefs.getString("swipe_left_app", null))
        set(value) = prefs.edit { putTarget("swipe_left_app", value) }

    /** The app swiping right opens; null for Android's default phone app. */
    var swipeRightApp: AppTarget?
        get() = AppTarget.decode(prefs.getString("swipe_right_app", null))
        set(value) = prefs.edit { putTarget("swipe_right_app", value) }

    // First run

    /** Until settings are opened once, the home screen shows how to reach the drawer and settings. */
    var settingsOpened: Boolean
        get() = prefs.getBoolean("settings_opened", false)
        set(value) = prefs.edit { putBoolean("settings_opened", value) }

    // Names

    /** The name you gave an app or shortcut, or null. */
    fun customName(key: String): String? = names.getString(key, null)

    fun setCustomName(key: String, name: String?) = names.edit {
        if (name.isNullOrBlank()) remove(key) else putString(key, name.trim())
    }

    val customNames: Map<String, String>
        get() = names.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()

    private fun android.content.SharedPreferences.Editor.putTarget(key: String, target: AppTarget?) {
        if (target == null) remove(key) else putString(key, target.encode())
    }

    companion object {
        const val MAX_HOME_APPS = 8

        const val CLOCK_TIME_AND_DATE = 0
        const val CLOCK_DATE_ONLY = 1
        const val CLOCK_OFF = 2

        const val DATE_SHORT = 0
        const val DATE_FULL = 1
        const val DATE_NUMERIC = 2

        /** Dark text, for light wallpapers. */
        const val TEXT_DARK = 0
        /** Light text, for dark wallpapers. */
        const val TEXT_LIGHT = 1
        const val TEXT_FOLLOW_DARK_MODE = 2
    }
}
