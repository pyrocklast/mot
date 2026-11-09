package io.github.pyrocklast.mot.data

import android.content.Context
import androidx.core.content.edit

/**
 * All of mot launcher's settings. Names chosen for apps live in their own file, keyed by [AppTarget.appKey].
 * Nothing here stores a copy of an app's name: names always come from [AppRepository].
 */
class Prefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mot", Context.MODE_PRIVATE)
    private val names = context.applicationContext.getSharedPreferences("mot_names", Context.MODE_PRIVATE)

    // Home screen

    /** The apps on the home screen, top to bottom: never more than [MAX_HOME_APPS], never twice the same. */
    var homeApps: List<AppTarget>
        get() = prefs.getString("home_apps", null)
            ?.split(LIST_SEPARATOR)
            ?.mapNotNull { AppTarget.decode(it) }
            ?.distinct()
            ?.take(MAX_HOME_APPS)
            .orEmpty()
        set(value) = prefs.edit {
            val apps = value.distinct().take(MAX_HOME_APPS)
            if (apps.isEmpty()) remove("home_apps")
            else putString("home_apps", apps.joinToString(LIST_SEPARATOR) { it.encode() })
        }

    /** Puts an app at the end of the home screen. False when it's already there or there's no more room. */
    fun addHomeApp(target: AppTarget): Boolean {
        val apps = homeApps
        if (apps.size >= MAX_HOME_APPS || target in apps) return false
        homeApps = apps + target
        return true
    }

    /**
     * [old] makes way for [new] in the same place, or is taken off the home screen when [new] is
     * null. If [old] has gone in the meantime, [new] goes at the end.
     */
    fun replaceHomeApp(old: AppTarget, new: AppTarget?) {
        val apps = homeApps.toMutableList()
        val index = apps.indexOf(old)
        when {
            index < 0 -> if (new != null) addHomeApp(new)
            new == null -> {
                apps.removeAt(index)
                homeApps = apps
            }
            else -> {
                apps[index] = new
                homeApps = apps
            }
        }
    }

    fun removeHomeApp(target: AppTarget) {
        homeApps = homeApps - target
    }

    /** Moves the home app at [from] to [to], the others making room. */
    fun moveHomeApp(from: Int, to: Int) {
        val apps = homeApps.toMutableList()
        if (from !in apps.indices || to !in apps.indices || from == to) return
        apps.add(to, apps.removeAt(from))
        homeApps = apps
    }

    // Clock and date

    var showTime: Boolean
        get() = prefs.getBoolean("show_time", true)
        set(value) = prefs.edit { putBoolean("show_time", value) }

    var showDate: Boolean
        get() = prefs.getBoolean("show_date", true)
        set(value) = prefs.edit { putBoolean("show_date", value) }

    /** The app tapping the time opens; null for Android's default clock app. */
    var clockApp: AppTarget?
        get() = AppTarget.decode(prefs.getString("clock_app", null))
        set(value) = prefs.edit { putTarget("clock_app", value) }

    /** The app tapping the date opens; null for Android's default calendar app. */
    var calendarApp: AppTarget?
        get() = AppTarget.decode(prefs.getString("calendar_app", null))
        set(value) = prefs.edit { putTarget("calendar_app", value) }

    // App drawer

    var autoKeyboard: Boolean
        get() = prefs.getBoolean("auto_keyboard", true)
        set(value) = prefs.edit { putBoolean("auto_keyboard", value) }

    /** Off unless you turn it on: typing shouldn't open an app you didn't tap. */
    var autoOpenUniqueSearchResult: Boolean
        get() = prefs.getBoolean("auto_open_unique_search_result", false)
        set(value) = prefs.edit { putBoolean("auto_open_unique_search_result", value) }

    /** The apps hidden from the drawer, by [AppTarget.appKey]. */
    var hiddenApps: Set<String>
        get() = prefs.getStringSet("hidden_apps", null).orEmpty().toSet()
        set(value) = prefs.edit { putStringSet("hidden_apps", value) }

    var hiddenBefore: Boolean
        get() = prefs.getBoolean("hidden_before", false)
        set(value) = prefs.edit { putBoolean("hidden_before", value) }

    // Appearance (home screen and drawer only)

    /** Light text, dark text, or whichever the wallpaper suits. */
    var textColour: Int
        get() = prefs.getInt("text_colour", TEXT_MATCH_WALLPAPER)
            .let { if (it == TEXT_DARK || it == TEXT_LIGHT) it else TEXT_MATCH_WALLPAPER }
        set(value) = prefs.edit { putInt("text_colour", value) }

    /** Whether the text takes its colour from the wallpaper, rather than being plain white or black. */
    var tintText: Boolean
        get() = prefs.getBoolean("tint_text", true)
        set(value) = prefs.edit { putBoolean("tint_text", value) }

    /**
     * How big app names are, on the home screen and in the drawer, from 80 to 180 % of their usual
     * size, which itself follows Android's font size. The time and the date have one size each.
     */
    var appNameScale: Float
        get() = prefs.getFloat("app_name_scale", 1f).coerceIn(MIN_NAME_SCALE, MAX_NAME_SCALE)
        set(value) = prefs.edit { putFloat("app_name_scale", value.coerceIn(MIN_NAME_SCALE, MAX_NAME_SCALE)) }

    var showStatusBar: Boolean
        get() = prefs.getBoolean("show_status_bar", true)
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

    // Crashes

    /**
     * A Home app that crashes as it draws takes the phone with it: it comes back, crashes again,
     * and there's no way out but installing another launcher. So each crash is counted, and
     * [resetLookAfterCrashes] gives the look back its defaults once they repeat.
     *
     * Saved straight away rather than in the background: the process ends right after this.
     */
    fun recordCrash() {
        val now = System.currentTimeMillis()
        val recent = now - prefs.getLong("last_crash", 0) < CRASHES_WITHIN_MS
        prefs.edit(commit = true) {
            putInt("crashes", if (recent) prefs.getInt("crashes", 0) + 1 else 1)
            putLong("last_crash", now)
        }
    }

    /** The launcher got a frame on screen: the crashes before it don't count towards a reset. */
    fun crashesSurvived() {
        if (prefs.getInt("crashes", 0) == 0) return
        prefs.edit { remove("crashes") }
    }

    /**
     * After [CRASHES_BEFORE_RESET] crashes in a row, puts the look back to its defaults, which is
     * where a crash while drawing comes from. Your apps, names and gestures are left alone.
     * Returns whether it did, so the launcher can say so.
     */
    fun resetLookAfterCrashes(): Boolean {
        if (prefs.getInt("crashes", 0) < CRASHES_BEFORE_RESET) return false
        prefs.edit(commit = true) {
            remove("crashes")
            LOOK_KEYS.forEach { remove(it) }
        }
        return true
    }

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
        const val MAX_HOME_APPS = 10

        // Between the home apps in their one saved line. AppTarget uses the unit separator inside
        // each one, so this is the next control character along, which can't appear in either.
        private const val LIST_SEPARATOR = "\u001E"

        private const val CRASHES_BEFORE_RESET = 3
        private const val CRASHES_WITHIN_MS = 60 * 1000L

        // Everything that decides how the home screen is drawn, and so what a crash while drawing
        // is likely to have come from
        private val LOOK_KEYS = listOf(
            "show_time", "show_date", "app_name_scale", "text_colour", "tint_text", "show_status_bar",
        )

        const val MIN_NAME_SCALE = 0.8f
        const val MAX_NAME_SCALE = 1.8f

        /** Dark text, for light wallpapers. */
        const val TEXT_DARK = 0
        /** Light text, for dark wallpapers. */
        const val TEXT_LIGHT = 1
        /** Dark or light text, whichever suits the wallpaper. */
        const val TEXT_MATCH_WALLPAPER = 3
    }
}
