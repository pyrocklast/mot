package io.github.pyrocklast.mot.data

import android.content.Context
import android.view.Gravity
import androidx.core.content.edit

/**
 * All of mot launcher's settings. Names chosen for apps live in their own file, keyed by [AppTarget.appKey].
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

    /** Moves the home app at [from] to [to]; the ones in between shift by one spot. Empty spots move too. */
    fun moveHomeApp(from: Int, to: Int, count: Int) {
        if (from == to || from !in 0 until count || to !in 0 until count) return
        val apps = (0 until count).map { homeApp(it) }.toMutableList()
        apps.add(to, apps.removeAt(from))
        prefs.edit { apps.forEachIndexed { slot, target -> putTarget("home_app_$slot", target) } }
    }

    var homeAlignment: Int
        get() = prefs.getInt("home_alignment", Gravity.START)
        set(value) = prefs.edit { putInt("home_alignment", value) }

    var homeAtBottom: Boolean
        get() = prefs.getBoolean("home_at_bottom", false)
        set(value) = prefs.edit { putBoolean("home_at_bottom", value) }

    // Clock and date

    // Version 0.1 had one choice (time and date, date only, off); its value sets the defaults
    var showTime: Boolean
        get() = prefs.getBoolean("show_time", prefs.getInt("clock_mode", 0) == 0)
        set(value) = prefs.edit { putBoolean("show_time", value) }

    var showDate: Boolean
        get() = prefs.getBoolean("show_date", prefs.getInt("clock_mode", 0) != 2)
        set(value) = prefs.edit { putBoolean("show_date", value) }

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

    /** The apps hidden from the drawer, by [AppTarget.appKey]. */
    var hiddenApps: Set<String>
        get() = prefs.getStringSet("hidden_apps", null).orEmpty().toSet()
        set(value) = prefs.edit { putStringSet("hidden_apps", value) }

    var hiddenBefore: Boolean
        get() = prefs.getBoolean("hidden_before", false)
        set(value) = prefs.edit { putBoolean("hidden_before", value) }

    // Appearance (home screen and drawer only)

    var textColour: Int
        get() = prefs.getInt("text_colour", TEXT_MATCH_WALLPAPER)
        set(value) = prefs.edit { putInt("text_colour", value) }

    // App names (home screen and drawer) and the clock and date each have a font, a weight and a
    // size. Weights are CSS-style numbers: 100 thin, 300 light, 400 regular, 500 medium, 700 bold.

    var nameFont: Int
        get() = prefs.getInt("name_font", FONT_SANS)
        set(value) = prefs.edit { putInt("name_font", value) }

    // Version 0.1's "Bolder app names" switch meant regular instead of light
    var nameWeight: Int
        get() = prefs.getInt("name_weight", if (prefs.getBoolean("bolder_names", false)) 400 else 300)
        set(value) = prefs.edit { putInt("name_weight", value) }

    var appNameScale: Float
        get() = prefs.getFloat("app_name_scale", 1f)
        set(value) = prefs.edit { putFloat("app_name_scale", value) }

    var clockFont: Int
        get() = prefs.getInt("clock_font", FONT_SANS)
        set(value) = prefs.edit { putInt("clock_font", value) }

    var clockWeight: Int
        get() = prefs.getInt("clock_weight", 300)
        set(value) = prefs.edit { putInt("clock_weight", value) }

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

    /**
     * Version 0.1 kept names and hidden apps by activity ("a:package/Class:user"). They're kept by
     * app now ("p:package:user"), so an app that changes its activity in an update keeps them.
     */
    fun migrateAppKeys() {
        if (prefs.getBoolean("app_keys_v2", false)) return
        fun appKey(key: String): String {
            if (!key.startsWith("a:")) return key
            val packageName = key.removePrefix("a:").substringBefore('/')
            val serial = key.substringAfterLast(':')
            return "p:$packageName:$serial"
        }
        val oldNames = customNames
        names.edit {
            oldNames.forEach { (key, name) ->
                remove(key)
                putString(appKey(key), name)
            }
        }
        val hidden = hiddenApps.map(::appKey).toSet()
        prefs.edit {
            putStringSet("hidden_apps", hidden)
            putBoolean("app_keys_v2", true)
        }
    }

    private fun android.content.SharedPreferences.Editor.putTarget(key: String, target: AppTarget?) {
        if (target == null) remove(key) else putString(key, target.encode())
    }

    companion object {
        const val MAX_HOME_APPS = 8

        const val DATE_SHORT = 0
        const val DATE_FULL = 1
        const val DATE_NUMERIC = 2

        /** Dark text, for light wallpapers. */
        const val TEXT_DARK = 0
        /** Light text, for dark wallpapers. */
        const val TEXT_LIGHT = 1
        const val TEXT_FOLLOW_DARK_MODE = 2
        /** Dark or light text, whichever Android says suits the wallpaper. */
        const val TEXT_MATCH_WALLPAPER = 3

        const val FONT_SANS = 0
        const val FONT_SERIF = 1
        const val FONT_MONO = 2
        const val FONT_CONDENSED = 3
    }
}
