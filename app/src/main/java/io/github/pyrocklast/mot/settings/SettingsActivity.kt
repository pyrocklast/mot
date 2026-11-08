package io.github.pyrocklast.mot.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import io.github.pyrocklast.mot.BuildConfig
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.AppRepository
import io.github.pyrocklast.mot.data.AppTarget
import io.github.pyrocklast.mot.data.Prefs
import io.github.pyrocklast.mot.databinding.ActivitySettingsBinding
import io.github.pyrocklast.mot.launcher.Slot
import io.github.pyrocklast.mot.util.Apps
import kotlin.math.roundToInt

/**
 * mot launcher's settings, grouped by the part of the home screen each setting changes. Rows
 * aren't saved by the list itself: each row shows a value from [Prefs] and writes changes back.
 */
class SettingsActivity : SettingsScreen() {

    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository

    private lateinit var setAsHome: Row
    private lateinit var changeHome: Row
    private lateinit var homeApps: Row
    private lateinit var archivedHomeApps: Row
    private lateinit var showTime: SwitchRow
    private lateinit var showDate: SwitchRow
    private lateinit var timeAction: Row
    private lateinit var dateAction: Row
    private lateinit var drawerAlignment: Row
    private lateinit var hiddenApps: Row
    private lateinit var privateSpace: Row
    private lateinit var textColour: Row
    private lateinit var tintText: SwitchRow
    private lateinit var nameSize: SliderRow
    private lateinit var statusBar: SwitchRow
    private lateinit var swipeLeft: SplitSwitchRow
    private lateinit var swipeRight: SplitSwitchRow

    private val alignments = intArrayOf(Gravity.START, Gravity.CENTER, Gravity.END)
    private val textColours = intArrayOf(Prefs.TEXT_MATCH_WALLPAPER, Prefs.TEXT_LIGHT, Prefs.TEXT_DARK)

    private val homeRoleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpFrame(binding.root, binding.appBar, binding.scroll, getString(R.string.settings_title, getString(R.string.app_name)))

        prefs = Prefs(this)
        repo = AppRepository.get(this)
        // The first-run tips on the home screen have done their job
        prefs.settingsOpened = true

        addRows(Rows(binding.rows))
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun addRows(rows: Rows) = with(rows) {
        // First, until mot launcher is your Home app; afterwards changing it is rare, so it moves under About
        group {
            setAsHome = item(R.string.set_as_home) {
                Apps.homeRoleRequest(this@SettingsActivity)?.let { homeRoleRequest.launch(it) }
                    ?: Apps.openHomeSettings(this@SettingsActivity)
            }
        }

        group(R.string.home_screen) {
            homeApps = item(R.string.home_apps) { manageHomeApps() }
            archivedHomeApps = item(R.string.restore_previous_home_apps) {
                confirmRestoreHomeApps()
            }
            showTime = switch(R.string.show_time, prefs.showTime) {
                prefs.showTime = it
                refresh()
            }
            showDate = switch(R.string.show_date, prefs.showDate) {
                prefs.showDate = it
                refresh()
            }
        }

        group(R.string.app_drawer) {
            drawerAlignment = item(R.string.alignment) {
                choose(R.string.drawer_alignment, alignments, prefs.drawerAlignment, ::alignmentLabel) { prefs.drawerAlignment = it }
            }
            switch(R.string.show_keyboard, prefs.autoKeyboard) { prefs.autoKeyboard = it }
            switch(R.string.auto_open_unique_search_result, prefs.autoOpenUniqueSearchResult) {
                prefs.autoOpenUniqueSearchResult = it
            }
            hiddenApps = item(R.string.hidden_apps) { startActivity(AppListActivity.hiddenApps(this@SettingsActivity)) }
            // Private Space is set up in Android's settings; its apps show at the end of the drawer
            privateSpace = item(R.string.private_space) { Apps.openPrivateSpaceSettings(this@SettingsActivity) }
        }

        // One deliberate typography system. Only the shared app-name size is adjustable.
        group(R.string.appearance, summary = R.string.appearance_summary) {
            textColour = item(R.string.text_colour) {
                choose(R.string.text_colour, textColours, prefs.textColour, ::textColourLabel) { prefs.textColour = it }
            }
            tintText = switch(R.string.tint_text, prefs.tintText) {
                prefs.tintText = it
                refresh()
            }
            nameSize = slider(
                R.string.app_names_size, 0, 10,
                ((prefs.appNameScale - 0.5f) * 10).roundToInt().coerceIn(0, 10),
            ) { prefs.appNameScale = 0.5f + it / 10f }
            nameSize.formatValue = { "${50 + it * 10} %" }
            statusBar = switch(R.string.show_status_bar, prefs.showStatusBar) {
                prefs.showStatusBar = it
                refresh()
            }
        }

        // One row per swipe: the text chooses the app, the switch turns the swipe on or off
        group(R.string.gestures) {
            timeAction = item(R.string.tap_time) {
                chooseApp(R.string.tap_time, R.string.default_clock_app, prefs.clockApp, Slot.Clock)
            }
            dateAction = item(R.string.tap_date) {
                chooseApp(R.string.tap_date, R.string.default_calendar_app, prefs.calendarApp, Slot.Calendar)
            }
            swipeLeft = splitSwitch(R.string.swipe_left, prefs.swipeLeftOn, onChange = { prefs.swipeLeftOn = it }) {
                chooseApp(R.string.swipe_left_opens, R.string.default_camera_app, prefs.swipeLeftApp, Slot.SwipeLeft)
            }
            swipeRight = splitSwitch(R.string.swipe_right, prefs.swipeRightOn, onChange = { prefs.swipeRightOn = it }) {
                chooseApp(R.string.swipe_right_opens, R.string.default_phone_app, prefs.swipeRightApp, Slot.SwipeRight)
            }
        }

        group(R.string.about) {
            changeHome = item(R.string.change_home) { Apps.openHomeSettings(this@SettingsActivity) }
            item(R.string.app_info) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", BuildConfig.APPLICATION_ID, null))
                runCatching { startActivity(intent) }
            }.summary = getString(R.string.version, BuildConfig.VERSION_NAME)
        }
    }

    /** Shows the current value of every row. */
    private fun refresh() {
        val isHome = Apps.isDefaultLauncher(this)
        setAsHome.isVisible = !isHome
        changeHome.isVisible = isHome

        val assignedCount = prefs.assignedHomeApps.size
        // Never show legacy dormant assignments on Home without explicit consent.
        archivedHomeApps.isVisible = prefs.archivedHomeAppCount > 0
        homeApps.summary = if (assignedCount == Prefs.MAX_HOME_APPS) {
            getString(R.string.home_apps_full)
        } else {
            resources.getQuantityString(R.plurals.app_count, assignedCount, assignedCount)
        }

        showTime.isChecked = prefs.showTime
        showDate.isChecked = prefs.showDate
        timeAction.isVisible = prefs.showTime
        dateAction.isVisible = prefs.showDate
        timeAction.summary = appName(repo, prefs.clockApp, R.string.default_clock_app)
        dateAction.summary = appName(repo, prefs.calendarApp, R.string.default_calendar_app)

        drawerAlignment.summary = getString(alignmentLabel(prefs.drawerAlignment))
        // Only apps still installed (the list keeps apps you uninstalled while hidden)
        val hiddenKeys = prefs.hiddenApps
        val hidden = repo.apps.value?.mapNotNullTo(HashSet()) { it.appKey.takeIf { key -> key in hiddenKeys } }?.size
            ?: hiddenKeys.size
        hiddenApps.summary = if (hidden == 0) getString(R.string.none) else resources.getQuantityString(R.plurals.app_count, hidden, hidden)
        // Android only shows Private Space to the Home app, so it's unknown until mot launcher is the Home app
        privateSpace.summary = repo.privateSpace.value?.let {
            getString(if (it.locked) R.string.private_space_locked_summary else R.string.private_space_unlocked_summary)
        } ?: if (isHome) getString(R.string.not_set_up) else null

        textColour.summary = getString(textColourLabel(prefs.textColour))
        tintText.isChecked = prefs.tintText
        tintText.summary = getString(if (prefs.tintText) R.string.tint_text_on else R.string.tint_text_off)
        nameSize.value = ((prefs.appNameScale - 0.5f) * 10).roundToInt().coerceIn(0, 10)
        // The status bar can be hidden without changing the date or drawing fake indicators.
        statusBar.isChecked = prefs.showStatusBar
        statusBar.summaryOff = getString(R.string.status_bar_off_summary)

        swipeLeft.isChecked = prefs.swipeLeftOn
        swipeLeft.summary = appName(repo, prefs.swipeLeftApp, R.string.default_camera_app)
        swipeRight.isChecked = prefs.swipeRightOn
        swipeRight.summary = appName(repo, prefs.swipeRightApp, R.string.default_phone_app)
    }

    /** Recovery is opt-in because old hidden home slots can contain sensitive app choices. */
    private fun confirmRestoreHomeApps() {
        if (prefs.archivedHomeAppCount == 0) return
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(R.string.restore_previous_home_apps)
            .setMessage(R.string.restore_previous_home_apps_prompt)
            .setPositiveButton(R.string.restore_home_apps_action) { _, _ ->
                val restored = prefs.restoreArchivedHomeApps()
                if (restored == 0) {
                    android.widget.Toast.makeText(this, R.string.home_apps_full,
                        android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(this,
                        resources.getQuantityString(R.plurals.home_apps_restored, restored, restored),
                        android.widget.Toast.LENGTH_SHORT).show()
                }
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        showDialog(dialog)
        dialog.show()
    }

    /**
     * Also manages saved apps that do not currently fit on the fixed home screen. Slot indexes
     * are retained: changing or removing a legacy assignment cannot affect its neighbours.
     */
    private fun manageHomeApps() {
        val assigned = prefs.homeApps.mapIndexedNotNull { index, target ->
            target?.let { index to it }
        }
        val options = assigned.map { (_, target) ->
            repo.nameFor(target) ?: getString(
                if (repo.isLockedPrivateSpace(target.userSerial)) R.string.private_space_locked
                else R.string.app_not_found,
            )
        }.toMutableList()
        if (assigned.size < Prefs.MAX_HOME_APPS) options += getString(R.string.add_to_home)
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(R.string.home_apps)
            .setItems(options.toTypedArray()) { _, selected ->
                if (selected == assigned.size) {
                    startActivity(AppListActivity.chooseApp(this, Slot.AddHome))
                } else {
                    showHomeAppActions(assigned[selected].first)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        showDialog(dialog)
        dialog.show()
    }

    private fun showHomeAppActions(slot: Int) {
        val dialog = android.app.AlertDialog.Builder(this)
            .setItems(arrayOf(
                getString(R.string.action_open),
                getString(R.string.change_app),
                getString(R.string.remove_from_home),
            )) { _, selected ->
                when (selected) {
                    // An assigned app hidden from the drawer can still be opened from this
                    // explicit management screen, without making it visible in the drawer.
                    0 -> prefs.homeApp(slot)?.let { Apps.open(this, it) }
                    1 -> startActivity(AppListActivity.chooseApp(this, Slot.Home(slot)))
                    else -> {
                        prefs.setHomeApp(slot, null)
                        refresh()
                        manageHomeApps()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        showDialog(dialog)
        dialog.show()
    }

    // Dialogs

    /** Android's default app for something, or one you choose from the list of apps. */
    private fun chooseApp(@StringRes title: Int, @StringRes defaultLabel: Int, current: AppTarget?, slot: Slot) =
        appChoiceDialog(prefs, title, defaultLabel, current, slot) { refresh() }

    private fun choose(@StringRes title: Int, values: IntArray, current: Int, label: (Int) -> Int, onChoice: (Int) -> Unit) {
        val labels = Array<CharSequence>(values.size) { getString(label(values[it])) }
        choiceDialog(title, labels, values.indexOf(current)) {
            onChoice(values[it])
            refresh()
        }
    }

    // Labels

    @StringRes
    private fun alignmentLabel(gravity: Int) = when (gravity) {
        Gravity.CENTER -> R.string.centre
        Gravity.END -> R.string.right
        else -> R.string.left
    }

    @StringRes
    private fun textColourLabel(colour: Int) = when (colour) {
        Prefs.TEXT_DARK -> R.string.dark_text
        Prefs.TEXT_LIGHT -> R.string.light_text
        else -> R.string.text_matches_wallpaper
    }
}
