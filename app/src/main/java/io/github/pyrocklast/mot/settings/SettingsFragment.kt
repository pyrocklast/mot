package io.github.pyrocklast.mot.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.color.MaterialColors
import io.github.pyrocklast.mot.BuildConfig
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.AppRepository
import io.github.pyrocklast.mot.data.AppTarget
import io.github.pyrocklast.mot.data.Prefs
import io.github.pyrocklast.mot.launcher.Slot
import io.github.pyrocklast.mot.util.Apps
import io.github.pyrocklast.mot.util.DateText
import io.github.pyrocklast.mot.util.HomeFit

/**
 * The settings list, grouped by the part of the home screen each setting changes. Preferences
 * aren't saved by the list itself: each row shows a value from [Prefs] and writes changes back.
 */
class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository
    private var dialog: AlertDialog? = null

    private lateinit var setAsHome: Preference
    private lateinit var changeHome: Preference
    private lateinit var homeAppCount: SliderPreference
    private lateinit var homeAlignment: Preference
    private lateinit var homeAtBottom: SwitchPreferenceCompat
    private lateinit var time: Preference
    private lateinit var date: Preference
    private lateinit var drawerAlignment: Preference
    private lateinit var hiddenApps: Preference
    private lateinit var privateSpace: Preference
    private lateinit var textColour: Preference
    private lateinit var nameStyle: Preference
    private lateinit var statusBar: SwitchPreferenceCompat
    private lateinit var swipeLeft: SplitSwitchPreference
    private lateinit var swipeRight: SplitSwitchPreference

    private val alignments = intArrayOf(Gravity.START, Gravity.CENTER, Gravity.END)
    private val textColours = intArrayOf(Prefs.TEXT_MATCH_WALLPAPER, Prefs.TEXT_DARK, Prefs.TEXT_LIGHT, Prefs.TEXT_FOLLOW_DARK_MODE)

    private val homeRoleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        prefs = Prefs(requireContext())
        repo = AppRepository.get(requireContext())
        val screen = preferenceManager.createPreferenceScreen(preferenceManager.context)

        // First, until mot launcher is your Home app; afterwards changing it is rare, so it moves under About
        setAsHome = screen.item(R.string.set_as_home) {
            Apps.homeRoleRequest(requireContext())?.let { homeRoleRequest.launch(it) }
                ?: Apps.openHomeSettings(requireContext())
        }

        screen.category(R.string.home_screen) {
            homeAppCount = slider(R.string.number_of_apps, 0, Prefs.MAX_HOME_APPS, prefs.homeAppCount) {
                prefs.homeAppCount = it
            }
            homeAppCount.formatValue = { resources.getQuantityString(R.plurals.app_count, it, it) }
            homeAlignment = item(R.string.alignment) {
                choose(R.string.home_alignment, alignments, prefs.homeAlignment, ::alignmentLabel) { prefs.homeAlignment = it }
            }
            homeAtBottom = switch(R.string.align_bottom, prefs.homeAtBottom) { prefs.homeAtBottom = it }
        }

        // Everything about each one on its own screen, with a preview of both
        screen.category(R.string.clock_and_date) {
            time = item(R.string.time) { settings().show(StyleFragment.time()) }
            date = item(R.string.date) { settings().show(StyleFragment.date()) }
        }

        screen.category(R.string.app_drawer) {
            drawerAlignment = item(R.string.alignment) {
                choose(R.string.drawer_alignment, alignments, prefs.drawerAlignment, ::alignmentLabel) { prefs.drawerAlignment = it }
            }
            switch(R.string.show_keyboard, prefs.autoKeyboard) { prefs.autoKeyboard = it }
            hiddenApps = item(R.string.hidden_apps) { settings().show(AppListFragment.hiddenApps()) }
            // Private Space is set up in Android's settings; its apps show at the end of the drawer
            privateSpace = item(R.string.private_space) { Apps.openPrivateSpaceSettings(requireContext()) }
        }

        // These change the home screen and drawer only; this screen keeps the phone's look
        screen.category(R.string.appearance, summary = R.string.appearance_summary) {
            textColour = item(R.string.text_colour) {
                choose(R.string.text_colour, textColours, prefs.textColour, ::textColourLabel) { prefs.textColour = it }
            }
            // Font, weight and size, with a preview
            nameStyle = item(R.string.app_names) { settings().show(StyleFragment.appNames()) }
            statusBar = switch(R.string.show_status_bar, prefs.showStatusBar) {
                prefs.showStatusBar = it
                refresh()
            }
        }

        // One row per swipe: the text chooses the app, the switch turns the swipe on or off
        screen.category(R.string.gestures) {
            swipeLeft = splitSwitch(R.string.swipe_left, prefs.swipeLeftOn, onChange = { prefs.swipeLeftOn = it }) {
                chooseApp(R.string.swipe_left_opens, R.string.default_camera_app, prefs.swipeLeftApp, Slot.SwipeLeft)
            }
            swipeRight = splitSwitch(R.string.swipe_right, prefs.swipeRightOn, onChange = { prefs.swipeRightOn = it }) {
                chooseApp(R.string.swipe_right_opens, R.string.default_phone_app, prefs.swipeRightApp, Slot.SwipeRight)
            }
        }

        screen.category(R.string.about) {
            changeHome = item(R.string.change_home) { Apps.openHomeSettings(requireContext()) }
            item(R.string.app_info) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", BuildConfig.APPLICATION_ID, null))
                runCatching { startActivity(intent) }
            }.summary = getString(R.string.version, BuildConfig.VERSION_NAME)
        }

        preferenceScreen = screen
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // The settings window shows the wallpaper (for the style previews); this screen covers it
        view.setBackgroundColor(MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurface))
        listView.clipToPadding = false
        listView.padForNavigationBar()
    }

    override fun onResume() {
        super.onResume()
        settings().setScreenTitle(getString(R.string.settings_title, getString(R.string.app_name)))
        refresh()
    }

    override fun onDestroyView() {
        dialog?.dismiss()
        dialog = null
        super.onDestroyView()
    }

    private fun settings() = requireActivity() as SettingsActivity

    /** Shows the current value of every row. */
    private fun refresh() {
        val isHome = Apps.isDefaultLauncher(requireContext())
        setAsHome.isVisible = !isHome
        changeHome.isVisible = isHome

        homeAppCount.value = prefs.homeAppCount
        // Big names on a small screen: say so rather than quietly cutting the list off
        val fit = HomeFit.namesThatFit(requireContext(), prefs)  // from the screen's height, with typical system bars
        homeAppCount.summary = if (prefs.homeAppCount > fit) getString(R.string.too_many_apps) else null
        homeAlignment.summary = getString(alignmentLabel(prefs.homeAlignment))
        homeAtBottom.isChecked = prefs.homeAtBottom

        val context = requireContext()
        // e.g. "Sans serif · Bold · 100 %", and "Tue 3 Nov · Condensed · Light · 100 %"
        time.summary = if (!prefs.showTime) getString(R.string.hidden)
        else context.styleSummary(prefs.timeFont, prefs.timeWeight, prefs.timeScale)
        date.summary = if (!prefs.showDate) getString(R.string.hidden)
        else DateText.date(context, prefs.dateStyle) + SEPARATOR + context.styleSummary(prefs.dateFont, prefs.dateWeight, prefs.dateScale)

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
        nameStyle.summary = context.styleSummary(prefs.nameFont, prefs.nameWeight, prefs.appNameScale)
        // With the status bar hidden, the battery level follows the date (when the date shows)
        statusBar.summaryOff = if (prefs.showDate) getString(R.string.status_bar_off_summary) else null

        swipeLeft.isChecked = prefs.swipeLeftOn
        swipeLeft.summary = context.appName(repo, prefs.swipeLeftApp, R.string.default_camera_app)
        swipeRight.isChecked = prefs.swipeRightOn
        swipeRight.summary = context.appName(repo, prefs.swipeRightApp, R.string.default_phone_app)
    }

    // Dialogs

    /** Android's default app for something, or one you choose from the list of apps. */
    private fun chooseApp(@StringRes title: Int, @StringRes defaultLabel: Int, current: AppTarget?, slot: Slot) {
        dialog?.dismiss()
        dialog = appChoiceDialog(prefs, title, defaultLabel, current, slot) { refresh() }
    }

    private fun choose(@StringRes title: Int, values: IntArray, current: Int, label: (Int) -> Int, onChoice: (Int) -> Unit) {
        val labels = Array<CharSequence>(values.size) { getString(label(values[it])) }
        dialog?.dismiss()
        dialog = choiceDialog(title, labels, values.indexOf(current)) {
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
        Prefs.TEXT_FOLLOW_DARK_MODE -> R.string.text_follows_dark_mode
        else -> R.string.text_matches_wallpaper
    }
}
