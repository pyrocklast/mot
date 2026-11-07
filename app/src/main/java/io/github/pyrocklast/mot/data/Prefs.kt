package io.github.pyrocklast.mot.data

import android.content.Context
import android.view.Gravity
import androidx.core.content.edit
import io.github.pyrocklast.mot.util.HomeSlots

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

    /** Only assigned apps count; there are no blank slots in the new home layout. */
    val homeAppCount: Int get() = prefs.getInt("home_app_count", 0).coerceIn(0, MAX_HOME_APPS)

    fun homeApp(slot: Int): AppTarget? =
        if (slot in 0 until homeAppCount) AppTarget.decode(prefs.getString("home_app_$slot", null))
        else null

    val homeApps: List<AppTarget?> get() = (0 until MAX_HOME_APPS).map { homeApp(it) }

    /** Write all active slots in one transaction. The archive is intentionally untouched. */
    private fun saveActiveHome(apps: List<AppTarget>) {
        require(apps.size <= MAX_HOME_APPS)
        prefs.edit {
            for (slot in 0 until MAX_HOME_APPS) putTarget("home_app_$slot", apps.getOrNull(slot))
            putInt("home_app_count", apps.size)
        }
    }

    /** Replaces an existing app, removes it and closes the gap, or appends at the end.
     * A stale or out-of-range slot cannot overwrite an unrelated assignment. */
    fun setHomeApp(slot: Int, target: AppTarget?) {
        val active = (0 until homeAppCount).mapNotNull(::homeApp).toMutableList()
        if (target == null) {
            if (slot !in active.indices) return
            saveActiveHome(HomeSlots.removeAt(active, slot))
        } else {
            when {
                slot in active.indices -> {
                    active[slot] = target
                    saveActiveHome(active)
                }
                slot == active.size && active.size < MAX_HOME_APPS -> {
                    active.add(target)
                    saveActiveHome(active)
                }
            }
        }
    }

    /** Drawer action: append only if this target is not already on the active home. */
    fun nextHomeSlot(target: AppTarget): Int? =
        HomeSlots.nextAddSlot(homeApps, homeAppCount, target)

    fun addHomeApp(target: AppTarget): Boolean {
        val slot = nextHomeSlot(target) ?: return false
        setHomeApp(slot, target)
        return true
    }

    /** Reorder existing rows only, never the separately archived assignments. */
    fun moveHomeApp(from: Int, to: Int, count: Int) {
        val active = (0 until homeAppCount).mapNotNull(::homeApp).toMutableList()
        if (count != active.size || from == to ||
            from !in active.indices || to !in active.indices) return
        active.add(to, active.removeAt(from))
        saveActiveHome(active)
    }

    /** Assignments previously outside the user's selected legacy slot count stay private
     * until the user explicitly asks for them to be restored. */
    private fun archivedHomeEntries(): List<Pair<Int, AppTarget>> =
        (0 until MAX_HOME_APPS).mapNotNull { slot ->
            AppTarget.decode(prefs.getString("archived_home_app_$slot", null))?.let { slot to it }
        }

    val archivedHomeAppCount: Int get() = archivedHomeEntries().size

    /** An explicit recovery action, adding as many archived rows as space allows. Remaining
     * archives survive for a later retry. No names or assignments are revealed automatically. */
    fun restoreArchivedHomeApps(): Int {
        val active = (0 until homeAppCount).mapNotNull(::homeApp)
        val result = HomeSlots.restore(active, archivedHomeEntries(), MAX_HOME_APPS)
        if (result.restoredIndices.isEmpty()) return 0
        prefs.edit {
            for (slot in 0 until MAX_HOME_APPS) putTarget("home_app_$slot", result.active.getOrNull(slot))
            putInt("home_app_count", result.active.size)
            result.restoredIndices.forEach { legacyIndex -> remove("archived_home_app_$legacyIndex") }
        }
        return result.restoredIndices.size
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

    var appNameScale: Float
        get() = prefs.getFloat("app_name_scale", 1f)
        set(value) = prefs.edit { putFloat("app_name_scale", value) }

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
        migrateCompactHome()
    }

    /** One-time, atomic migration. Visible assignments compact in their original order;
     * dormant slots are archived under separate keys with their original positions intact.
     * An interrupted migration is retried, and never uses archive entries as active defaults. */
    private fun migrateCompactHome() {
        if (prefs.getBoolean("home_compacted_v2", false)) return
        val count = prefs.getInt("home_app_count", 5).coerceIn(0, MAX_HOME_APPS)
        val legacy = (0 until MAX_HOME_APPS).map { slot ->
            AppTarget.decode(prefs.getString("home_app_$slot", null))
        }
        val compact = HomeSlots.compact(legacy, count)
        prefs.edit(commit = true) {
            for (slot in 0 until MAX_HOME_APPS) putTarget("home_app_$slot", compact.active.getOrNull(slot))
            compact.dormant.forEach { (slot, target) ->
                putTarget("archived_home_app_$slot", target)
            }
            putInt("home_app_count", compact.active.size)
            putBoolean("home_compacted_v2", true)
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

        const val FONT_SANS = 0
        const val FONT_SERIF = 1
        const val FONT_MONO = 2
        const val FONT_CONDENSED = 3
    }
}
