package io.github.pyrocklast.mot.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import io.github.pyrocklast.mot.BuildConfig
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.AppRepository
import io.github.pyrocklast.mot.data.Prefs
import io.github.pyrocklast.mot.databinding.ActivitySettingsBinding
import io.github.pyrocklast.mot.launcher.Slot
import io.github.pyrocklast.mot.util.Apps
import io.github.pyrocklast.mot.util.HomeFit

/**
 * mot launcher's settings, grouped by what you see: the home screen, how its text looks, the app
 * drawer, then gestures. Rows aren't saved by the list itself: each row shows a value from
 * [Prefs] and writes changes back.
 *
 * What can be done where it happens isn't here too: home apps are added, changed, moved and
 * removed on the home screen and in the drawer, and Private Space is Android's own.
 */
class SettingsActivity : SettingsScreen() {

    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository

    private lateinit var setAsHome: Row
    private lateinit var changeHome: Row
    private lateinit var showTime: SwitchRow
    private lateinit var showDate: SwitchRow
    private lateinit var statusBar: SwitchRow
    private lateinit var textColour: Row
    private lateinit var nameSize: SliderRow
    private lateinit var hiddenApps: Row
    private lateinit var tapTime: Row
    private lateinit var tapDate: Row
    private lateinit var swipeLeft: SplitSwitchRow
    private lateinit var swipeRight: SplitSwitchRow

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

        // What shows on the home screen, top to bottom, apart from your apps
        group(R.string.home_screen) {
            statusBar = switch(R.string.show_status_bar, prefs.showStatusBar) {
                prefs.showStatusBar = it
                refresh()
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

        // How the text of the home screen and drawer looks (this screen keeps the phone's look).
        // The font is Android's own; the time and date are sized for the screen, and only app
        // names have a size of your choosing, which decides how many apps fit.
        group(R.string.appearance, summary = R.string.appearance_summary) {
            textColour = item(R.string.text_colour) {
                choose(R.string.text_colour, textColours, prefs.textColour, ::textColourLabel) { prefs.textColour = it }
            }
            // 24 to 48 dp, in steps of 2, and as you move it, how many apps that leaves room for
            nameSize = slider(R.string.app_name_size, 0, NAME_SIZE_STEPS, nameSizeStep()) {
                prefs.appNameSize = nameSizeAt(it)
            }.apply { formatValue = ::nameSizeLabel }
        }

        group(R.string.app_drawer) {
            switch(R.string.show_keyboard, prefs.autoKeyboard) { prefs.autoKeyboard = it }
            switch(R.string.auto_open_unique_search_result, prefs.autoOpenUniqueSearchResult) {
                prefs.autoOpenUniqueSearchResult = it
            }
            hiddenApps = item(R.string.hidden_apps) { startActivity(AppListActivity.hiddenApps(this@SettingsActivity)) }
        }

        // What tapping the time and the date opens; then one row per swipe, where the text chooses
        // the app and the switch turns the swipe on or off. Choosing goes straight to the list of
        // apps, with Android's own app first.
        group(R.string.gestures) {
            tapTime = item(R.string.tap_time) { chooseApp(Slot.Clock) }
            tapDate = item(R.string.tap_date) { chooseApp(Slot.Calendar) }
            swipeLeft = splitSwitch(R.string.swipe_left, prefs.swipeLeftOn, onChange = { prefs.swipeLeftOn = it }) {
                chooseApp(Slot.SwipeLeft)
            }
            swipeRight = splitSwitch(R.string.swipe_right, prefs.swipeRightOn, onChange = { prefs.swipeRightOn = it }) {
                chooseApp(Slot.SwipeRight)
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

        statusBar.isChecked = prefs.showStatusBar
        statusBar.summaryOff = getString(R.string.status_bar_off_summary)
        showTime.isChecked = prefs.showTime
        showDate.isChecked = prefs.showDate

        textColour.summary = getString(textColourLabel(prefs.textColour))
        // Also brings the room count up to date: the time, date and status bar change it too
        nameSize.value = nameSizeStep()

        // Only apps still installed (the list keeps apps you uninstalled while hidden)
        val hiddenKeys = prefs.hiddenApps
        val hidden = repo.apps.value?.mapNotNullTo(HashSet()) { it.appKey.takeIf { key -> key in hiddenKeys } }?.size
            ?: hiddenKeys.size
        hiddenApps.summary = if (hidden == 0) getString(R.string.none) else resources.getQuantityString(R.plurals.app_count, hidden, hidden)

        // Nothing to tap when they're hidden
        tapTime.isVisible = prefs.showTime
        tapDate.isVisible = prefs.showDate
        tapTime.summary = appName(repo, prefs.clockApp, R.string.default_clock_app)
        tapDate.summary = appName(repo, prefs.calendarApp, R.string.default_calendar_app)
        swipeLeft.isChecked = prefs.swipeLeftOn
        swipeLeft.summary = appName(repo, prefs.swipeLeftApp, R.string.default_camera_app)
        swipeRight.isChecked = prefs.swipeRightOn
        swipeRight.summary = appName(repo, prefs.swipeRightApp, R.string.default_phone_app)
    }

    // Dialogs and lists

    private fun chooseApp(slot: Slot) = startActivity(AppListActivity.chooseApp(this, slot))

    private fun choose(@StringRes title: Int, values: IntArray, current: Int, label: (Int) -> Int, onChoice: (Int) -> Unit) {
        val labels = Array<CharSequence>(values.size) { getString(label(values[it])) }
        choiceDialog(title, labels, values.indexOf(current)) {
            onChoice(values[it])
            refresh()
        }
    }

    // App name size

    private fun nameSizeAt(step: Int) = Prefs.MIN_NAME_SIZE + step * NAME_SIZE_STEP

    private fun nameSizeStep(): Int = (prefs.appNameSize - Prefs.MIN_NAME_SIZE) / NAME_SIZE_STEP

    // e.g. "34 · room for 9 apps", for the size the slider is at, before it's saved
    private fun nameSizeLabel(step: Int): String {
        val size = nameSizeAt(step)
        val room = HomeFit.capacity(this, prefs, size)
        return "$size · " + resources.getQuantityString(R.plurals.room_for_apps, room, room)
    }

    // Labels

    @StringRes
    private fun textColourLabel(colour: Int) = when (colour) {
        Prefs.TEXT_DARK -> R.string.dark_text
        Prefs.TEXT_LIGHT -> R.string.light_text
        else -> R.string.text_matches_wallpaper
    }

    private companion object {
        const val NAME_SIZE_STEP = 2
        const val NAME_SIZE_STEPS = (Prefs.MAX_NAME_SIZE - Prefs.MIN_NAME_SIZE) / NAME_SIZE_STEP
    }
}
