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
import io.github.pyrocklast.mot.util.DateText
import io.github.pyrocklast.mot.util.HomeFit

/**
 * mot launcher's settings, grouped by the part of the home screen each setting changes. Rows
 * aren't saved by the list itself: each row shows a value from [Prefs] and writes changes back.
 */
class SettingsActivity : SettingsScreen() {

    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository

    private lateinit var setAsHome: Row
    private lateinit var changeHome: Row
    private lateinit var homeAppCount: SliderRow
    private lateinit var homeAlignment: Row
    private lateinit var homeAtBottom: SwitchRow
    private lateinit var time: Row
    private lateinit var date: Row
    private lateinit var drawerAlignment: Row
    private lateinit var hiddenApps: Row
    private lateinit var privateSpace: Row
    private lateinit var textColour: Row
    private lateinit var tintText: SwitchRow
    private lateinit var nameStyle: Row
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
            homeAppCount = slider(R.string.number_of_apps, 0, Prefs.MAX_HOME_APPS, prefs.homeAppCount) {
                prefs.homeAppCount = it
                showHomeAppCountWarning()
            }
            homeAppCount.formatValue = { resources.getQuantityString(R.plurals.app_count, it, it) }
            homeAlignment = item(R.string.alignment) {
                choose(R.string.home_alignment, alignments, prefs.homeAlignment, ::alignmentLabel) { prefs.homeAlignment = it }
            }
            homeAtBottom = switch(R.string.align_bottom, prefs.homeAtBottom) { prefs.homeAtBottom = it }
        }

        group(R.string.app_drawer) {
            drawerAlignment = item(R.string.alignment) {
                choose(R.string.drawer_alignment, alignments, prefs.drawerAlignment, ::alignmentLabel) { prefs.drawerAlignment = it }
            }
            switch(R.string.show_keyboard, prefs.autoKeyboard) { prefs.autoKeyboard = it }
            hiddenApps = item(R.string.hidden_apps) { startActivity(AppListActivity.hiddenApps(this@SettingsActivity)) }
            // Private Space is set up in Android's settings; its apps show at the end of the drawer
            privateSpace = item(R.string.private_space) { Apps.openPrivateSpaceSettings(this@SettingsActivity) }
        }

        // How the home screen and drawer look (this screen keeps the phone's look). App names, the
        // time and the date each have their own screen, with a preview, for font, weight and size;
        // the time's and date's screens also set what tapping them opens.
        group(R.string.appearance, summary = R.string.appearance_summary) {
            textColour = item(R.string.text_colour) {
                choose(R.string.text_colour, textColours, prefs.textColour, ::textColourLabel) { prefs.textColour = it }
            }
            tintText = switch(R.string.tint_text, prefs.tintText) {
                prefs.tintText = it
                refresh()
            }
            nameStyle = item(R.string.app_names) { startActivity(StyleActivity.appNames(this@SettingsActivity)) }
            time = item(R.string.time) { startActivity(StyleActivity.time(this@SettingsActivity)) }
            date = item(R.string.date) { startActivity(StyleActivity.date(this@SettingsActivity)) }
            statusBar = switch(R.string.show_status_bar, prefs.showStatusBar) {
                prefs.showStatusBar = it
                refresh()
            }
        }

        // One row per swipe: the text chooses the app, the switch turns the swipe on or off
        group(R.string.gestures, summary = R.string.gesture_summary) {
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

        homeAppCount.value = prefs.homeAppCount
        showHomeAppCountWarning()
        homeAlignment.summary = getString(alignmentLabel(prefs.homeAlignment))
        homeAtBottom.isChecked = prefs.homeAtBottom

        // e.g. "Sans serif · Bold · 100 %", and "Tue 3 Nov · Condensed · Light · 100 %"
        time.summary = if (!prefs.showTime) getString(R.string.hidden)
        else styleSummary(prefs.timeFont, prefs.timeWeight, prefs.timeScale)
        date.summary = if (!prefs.showDate) getString(R.string.hidden)
        else DateText.date(this, prefs.dateStyle) + SEPARATOR + styleSummary(prefs.dateFont, prefs.dateWeight, prefs.dateScale)

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
        nameStyle.summary = styleSummary(prefs.nameFont, prefs.nameWeight, prefs.appNameScale)
        // With the status bar hidden, the battery level follows the date (when the date shows)
        statusBar.isChecked = prefs.showStatusBar
        statusBar.summaryOff = getString(
            if (prefs.showDate) R.string.status_bar_off_summary else R.string.status_bar_off_no_date_summary
        )

        swipeLeft.isChecked = prefs.swipeLeftOn
        swipeLeft.summary = appName(repo, prefs.swipeLeftApp, R.string.default_camera_app)
        swipeRight.isChecked = prefs.swipeRightOn
        swipeRight.summary = appName(repo, prefs.swipeRightApp, R.string.default_phone_app)
    }

    // Big names on a small screen: say so rather than quietly cutting the list off
    private fun showHomeAppCountWarning() {
        val fit = HomeFit.namesThatFit(this, prefs)  // from the screen's height, with typical system bars
        homeAppCount.summary = if (prefs.homeAppCount > fit) getString(R.string.too_many_apps) else null
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
