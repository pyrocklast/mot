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

    init {
        // Existing installations without an explicit status-bar preference must retain their
        // historical hidden default. Only a genuinely new installation starts with it shown.
        // This runs before migrate(), which writes its own preferences on first launch.
        if (!prefs.contains("status_bar_default_v2")) {
            val hadExistingData = prefs.all.isNotEmpty() || names.all.isNotEmpty()
            prefs.edit {
                putBoolean("status_bar_initial_default", defaultStatusBarForInstall(hadExistingData))
                putBoolean("status_bar_default_v2", true)
            }
        }
    }

    // Home screen

    /** Legacy visible-slot count retained for upgrades; the home now shows assigned apps only. */
    var homeAppCount: Int
        get() = prefs.getInt("home_app_count", 5)
        set(value) = prefs.edit { putInt("home_app_count", value.coerceIn(0, MAX_HOME_APPS)) }

    fun homeApp(slot: Int): AppTarget? = AppTarget.decode(prefs.getString("home_app_$slot", null))

    fun setHomeApp(slot: Int, target: AppTarget?) = prefs.edit { putTarget("home_app_$slot", target) }

    val homeApps: List<AppTarget?> get() = (0 until MAX_HOME_APPS).map { homeApp(it) }

    /** Assigned apps, in legacy slot order. Empty slots are never displayed. */
    val assignedHomeApps: List<AppTarget> get() = HomeSelection.assigned(homeApps)

    /** Archived assignments were outside the former visible-slot count. Never reveal implicitly. */
    private fun archivedHomeEntries(): List<Pair<Int, AppTarget>> =
        (0 until MAX_HOME_APPS).mapNotNull { index ->
            AppTarget.decode(prefs.getString("archived_home_app_$index", null))
                ?.let { index to it }
        }

    val archivedHomeAppCount: Int get() = archivedHomeEntries().size

    /**
     * A deliberate user action. Restore only what fits, atomically, and leave all remaining
     * assignments archived. Drawer hiding preferences and user-profile state are untouched.
     */
    fun restoreArchivedHomeApps(): Int {
        val recovery = HomeSelection.recover(assignedHomeApps, archivedHomeEntries(), MAX_HOME_APPS)
        if (recovery.restoredIndices.isEmpty()) return 0
        prefs.edit(commit = true) {
            (0 until MAX_HOME_APPS).forEach { index ->
                putTarget("home_app_$index", recovery.visible.getOrNull(index))
            }
            recovery.restoredIndices.forEach { index -> remove("archived_home_app_$index") }
            putInt("home_app_count", recovery.visible.size)
        }
        return recovery.restoredIndices.size
    }

    /** Append only after the user actually chooses an app; cancelling changes nothing. */
    fun appendHomeApp(target: AppTarget): Boolean {
        val next = HomeSelection.append(homeApps, target, MAX_HOME_APPS) ?: return false
        saveHomeAssignments(next)
        return true
    }

    /** Move an assigned app between stored slots, preserving all targets and legacy gaps. */
    fun moveHomeAppBySlot(fromSlot: Int, toSlot: Int) {
        val next = HomeSelection.moveBySlot(homeApps, fromSlot, toSlot) ?: return
        saveHomeAssignments(next)
    }

    private fun saveHomeAssignments(apps: List<AppTarget>) = prefs.edit {
        (0 until MAX_HOME_APPS).forEach { index -> putTarget("home_app_$index", apps.getOrNull(index)) }
        // Legacy counter is no longer a display constraint, but keep it coherent for backups.
        putInt("home_app_count", apps.size)
    }


    // Historical home alignment/bottom keys remain on disk for migration compatibility,
    // but the fixed start-aligned layout no longer exposes or reads them.

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

    /** Explicit opt-in; searching must not launch apps unexpectedly by default. */
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
            // Version 0.1 could follow the phone's dark mode; that's what matching does with a
            // wallpaper that says nothing about itself
            .let { if (it == LEGACY_FOLLOW_DARK_MODE) TEXT_MATCH_WALLPAPER else it }
        set(value) = prefs.edit { putInt("text_colour", value) }

    /** Whether the text takes its colour from the wallpaper, rather than being plain white or black. */
    var tintText: Boolean
        get() = prefs.getBoolean("tint_text", true)
        set(value) = prefs.edit { putBoolean("tint_text", value) }

    // App names (home screen and drawer), the time and the date each have a font, a weight and a
    // size. Weights are CSS-style numbers: 100 thin, 300 light, 400 regular, 500 medium, 700 bold.

    var nameFont: Int
        get() = prefs.getInt("name_font", FONT_SANS)
        set(value) = prefs.edit { putInt("name_font", value) }

    // Version 0.1's "Bolder app names" switch meant regular instead of light
    var nameWeight: Int
        get() = prefs.getInt("name_weight", if (prefs.getBoolean("bolder_names", false)) 400 else 300)
        set(value) = prefs.edit { putInt("name_weight", value) }

    /** Displayed size stays in the 80–180% range, including older saved preferences. */
    var appNameScale: Float
        get() = prefs.getFloat("app_name_scale", 1f).coerceIn(MIN_APP_NAME_SCALE, MAX_APP_NAME_SCALE)
        set(value) = prefs.edit {
            putFloat("app_name_scale", value.coerceIn(MIN_APP_NAME_SCALE, MAX_APP_NAME_SCALE))
        }

    // The time's style is saved as the "clock" style, which the date shared until it got its own
    var timeFont: Int
        get() = prefs.getInt("clock_font", FONT_SANS)
        set(value) = prefs.edit { putInt("clock_font", value) }

    var timeWeight: Int
        get() = prefs.getInt("clock_weight", 300)
        set(value) = prefs.edit { putInt("clock_weight", value) }

    var timeScale: Float
        get() = prefs.getFloat("clock_scale", 1f)
        set(value) = prefs.edit { putFloat("clock_scale", value) }

    var dateFont: Int
        get() = prefs.getInt("date_font", FONT_SANS)
        set(value) = prefs.edit { putInt("date_font", value) }

    var dateWeight: Int
        get() = prefs.getInt("date_weight", 300)
        set(value) = prefs.edit { putInt("date_weight", value) }

    var dateScale: Float
        get() = prefs.getFloat("date_scale", 1f)
        set(value) = prefs.edit { putFloat("date_scale", value) }

    /** Visible by default on new installs; explicit or historic choices are preserved. */
    var showStatusBar: Boolean
        get() = prefs.getBoolean("show_status_bar", prefs.getBoolean("status_bar_initial_default", false))
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
     */
    fun recordCrash() {
        val now = System.currentTimeMillis()
        val recent = now - prefs.getLong("last_crash", 0) < CRASHES_WITHIN_MS
        prefs.edit {
            putInt("crashes", if (recent) prefs.getInt("crashes", 0) + 1 else 1)
            putLong("last_crash", now)
        }
    }

    /** The launcher got going again: the crashes before it don't count towards a reset. */
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
        prefs.edit {
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

    /** Brings settings saved by an older version up to date. Runs when the app starts. */
    fun migrate() {
        migrateAppKeys()
        splitDateStyle()
        migrateHomeVisibility()
    }

    /**
     * The old slider counted visible home slots. Later stored assignments must NOT suddenly
     * appear merely because the new UI removed that slider. Archive them on first upgrade.
     *
     * The already-tested compact-home experiment had its own migration marker; honour it
     * rather than reclassifying assignments added after that migration.
     */
    private fun migrateHomeVisibility() {
        if (prefs.getBoolean("home_visibility_migrated_v3", false)) return
        if (prefs.getBoolean("home_compacted_v2", false)) {
            prefs.edit(commit = true) { putBoolean("home_visibility_migrated_v3", true) }
            return
        }
        val visibleCount = prefs.getInt("home_app_count", 5).coerceIn(0, MAX_HOME_APPS)
        val split = HomeSelection.splitLegacy(homeApps, visibleCount)
        // One committed snapshot: never allow a crash to leave apps half-archived and visible.
        prefs.edit(commit = true) {
            (0 until MAX_HOME_APPS).forEach { index ->
                putTarget("home_app_$index", split.visible.getOrNull(index))
            }
            split.archived.forEach { (index, target) ->
                putTarget("archived_home_app_$index", target)
            }
            putInt("home_app_count", split.visible.size)
            putBoolean("home_visibility_migrated_v3", true)
        }
    }

    /** The date had the time's style until it got its own: it starts from the time's, so nothing changes. */
    private fun splitDateStyle() {
        if (prefs.contains("date_font")) return
        prefs.edit {
            putInt("date_font", timeFont)
            putInt("date_weight", timeWeight)
            putFloat("date_scale", timeScale)
        }
    }

    /**
     * Version 0.1 kept names and hidden apps by activity ("a:package/Class:user"). They're kept by
     * app now ("p:package:user"), so an app that changes its activity in an update keeps them.
     */
    private fun migrateAppKeys() {
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
        /** Separate pure policy so the first-install vs upgrade behavior has regression tests. */
        internal fun defaultStatusBarForInstall(hadExistingData: Boolean): Boolean = !hadExistingData

        const val MAX_HOME_APPS = 10

        private const val CRASHES_BEFORE_RESET = 3
        private const val CRASHES_WITHIN_MS = 60 * 1000L

        // Everything that decides how the home screen is drawn, and so what a crash while drawing
        // is likely to have come from
        private val LOOK_KEYS = listOf(
            "home_alignment", "home_at_bottom",
            "show_time", "show_date", "date_style", "clock_mode",
            "clock_font", "clock_weight", "clock_scale",
            "date_font", "date_weight", "date_scale",
            "name_font", "name_weight", "app_name_scale", "bolder_names",
            "text_colour", "tint_text", "show_status_bar", "drawer_alignment",
        )

        const val DATE_SHORT = 0
        const val DATE_FULL = 1
        const val DATE_NUMERIC = 2

        /** Dark text, for light wallpapers. */
        const val TEXT_DARK = 0
        /** Light text, for dark wallpapers. */
        const val TEXT_LIGHT = 1
        private const val LEGACY_FOLLOW_DARK_MODE = 2
        /** Dark or light text, whichever Android says suits the wallpaper. */
        const val TEXT_MATCH_WALLPAPER = 3

        const val MIN_APP_NAME_SCALE = 0.8f
        const val MAX_APP_NAME_SCALE = 1.8f

        const val FONT_SANS = 0
        const val FONT_SERIF = 1
        const val FONT_MONO = 2
        const val FONT_CONDENSED = 3
    }
}
