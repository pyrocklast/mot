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
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.pyrocklast.mot.BuildConfig
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.AppRepository
import io.github.pyrocklast.mot.data.AppTarget
import io.github.pyrocklast.mot.data.Prefs
import io.github.pyrocklast.mot.launcher.Slot
import io.github.pyrocklast.mot.util.Apps
import io.github.pyrocklast.mot.util.DateText
import java.text.NumberFormat

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
    private lateinit var showTime: SwitchPreferenceCompat
    private lateinit var showDate: SwitchPreferenceCompat
    private lateinit var dateStyle: Preference
    private lateinit var clockStyle: Preference
    private lateinit var clockApp: Preference
    private lateinit var calendarApp: Preference
    private lateinit var drawerAlignment: Preference
    private lateinit var hiddenApps: Preference
    private lateinit var privateSpace: Preference
    private lateinit var textColour: Preference
    private lateinit var nameStyle: Preference
    private lateinit var statusBar: SwitchPreferenceCompat
    private lateinit var swipeLeft: SplitSwitchPreference
    private lateinit var swipeRight: SplitSwitchPreference

    private val alignments = intArrayOf(Gravity.START, Gravity.CENTER, Gravity.END)
    private val dateStyles = intArrayOf(Prefs.DATE_SHORT, Prefs.DATE_FULL, Prefs.DATE_NUMERIC)
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

        screen.category(R.string.clock_and_date) {
            showTime = switch(R.string.time, prefs.showTime) {
                prefs.showTime = it
                refresh()
            }
            showDate = switch(R.string.date, prefs.showDate) {
                prefs.showDate = it
                refresh()
            }
            dateStyle = item(R.string.date_style) {
                val labels = Array<CharSequence>(dateStyles.size) { DateText.date(requireContext(), dateStyles[it]) }
                choose(R.string.date_style, labels, dateStyles.indexOf(prefs.dateStyle)) { prefs.dateStyle = dateStyles[it] }
            }
            // Font, weight and size, with a preview
            clockStyle = item(R.string.style) { settings().show(StyleFragment.clockAndDate()) }
            clockApp = item(R.string.tap_time) {
                chooseApp(R.string.tap_time, R.string.default_clock_app, prefs.clockApp, Slot.Clock)
            }
            calendarApp = item(R.string.tap_date) {
                chooseApp(R.string.tap_date, R.string.default_calendar_app, prefs.calendarApp, Slot.Calendar)
            }
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
        homeAlignment.summary = getString(alignmentLabel(prefs.homeAlignment))
        homeAtBottom.isChecked = prefs.homeAtBottom

        showTime.isChecked = prefs.showTime
        showDate.isChecked = prefs.showDate
        dateStyle.summary = DateText.date(requireContext(), prefs.dateStyle)
        dateStyle.isEnabled = prefs.showDate
        clockStyle.summary = styleSummary(prefs.clockFont, prefs.clockWeight, prefs.clockScale)
        clockStyle.isEnabled = prefs.showTime || prefs.showDate
        clockApp.summary = appName(prefs.clockApp, R.string.default_clock_app)
        clockApp.isEnabled = prefs.showTime
        calendarApp.summary = appName(prefs.calendarApp, R.string.default_calendar_app)
        calendarApp.isEnabled = prefs.showDate

        drawerAlignment.summary = getString(alignmentLabel(prefs.drawerAlignment))
        // Only apps still installed (the list keeps apps you uninstalled while hidden)
        val hiddenKeys = prefs.hiddenApps
        val hidden = repo.apps.value?.map { it.appKey }?.distinct()?.count { it in hiddenKeys } ?: hiddenKeys.size
        hiddenApps.summary = if (hidden == 0) getString(R.string.none) else resources.getQuantityString(R.plurals.app_count, hidden, hidden)
        // Android only shows Private Space to the Home app, so it's unknown until mot launcher is the Home app
        privateSpace.summary = repo.privateSpace.value?.let {
            getString(if (it.locked) R.string.private_space_locked_summary else R.string.private_space_unlocked_summary)
        } ?: if (isHome) getString(R.string.not_set_up) else null

        textColour.summary = getString(textColourLabel(prefs.textColour))
        nameStyle.summary = styleSummary(prefs.nameFont, prefs.nameWeight, prefs.appNameScale)
        // With the status bar hidden, the battery level follows the date (when the date shows)
        statusBar.summaryOff = if (prefs.showDate) getString(R.string.status_bar_off_summary) else null

        swipeLeft.isChecked = prefs.swipeLeftOn
        swipeLeft.summary = appName(prefs.swipeLeftApp, R.string.default_camera_app)
        swipeRight.isChecked = prefs.swipeRightOn
        swipeRight.summary = appName(prefs.swipeRightApp, R.string.default_phone_app)
    }

    // An app in a locked Private Space is only named once it's unlocked
    private fun appName(target: AppTarget?, @StringRes default: Int): String = when {
        target == null -> getString(default)
        repo.isLockedPrivateSpace(target.userSerial) -> getString(R.string.private_space)
        else -> repo.nameFor(target) ?: getString(default)
    }

    // Dialogs

    /** Android's default app for something, or one you choose from the list of apps. */
    private fun chooseApp(@StringRes title: Int, @StringRes defaultLabel: Int, current: AppTarget?, slot: Slot) {
        val labels = arrayOf<CharSequence>(getString(defaultLabel), getString(R.string.choose_app_ellipsis))
        choose(title, labels, if (current == null) 0 else 1) { which ->
            if (which == 0) {
                slot.save(prefs, null)
                refresh()
            } else settings().show(AppListFragment.chooseApp(slot))
        }
    }

    private fun choose(@StringRes title: Int, values: IntArray, current: Int, label: (Int) -> Int, onChoice: (Int) -> Unit) {
        val labels = Array<CharSequence>(values.size) { getString(label(values[it])) }
        choose(title, labels, values.indexOf(current)) { onChoice(values[it]) }
    }

    private fun choose(@StringRes title: Int, labels: Array<CharSequence>, checked: Int, onChoice: (Int) -> Unit) {
        dialog?.dismiss()
        dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(labels, checked) { choiceDialog, which ->
                choiceDialog.dismiss()
                onChoice(which)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // e.g. "Sans serif · Light · 100 %"
    private fun styleSummary(font: Int, weight: Int, scale: Float): String = listOf(
        getString(StyleFragment.fontLabel(font)),
        getString(StyleFragment.weightLabel(weight)),
        percent(scale),
    ).joinToString(" · ")

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

    // e.g. "110 %" in French
    private fun percent(scale: Float): String = NumberFormat.getPercentInstance().format(scale.toDouble())

    // Builders. No preference is persisted by itself: Prefs stays the one source of truth.

    private fun PreferenceGroup.category(
        @StringRes title: Int,
        @StringRes summary: Int = 0,
        rows: PreferenceCategory.() -> Unit,
    ) {
        val category = PreferenceCategory(context)
        category.setTitle(title)
        if (summary != 0) category.setSummary(summary)
        category.isIconSpaceReserved = false
        addPreference(category)
        category.rows()
    }

    private fun PreferenceGroup.item(@StringRes title: Int, onClick: () -> Unit): Preference {
        val preference = Preference(context)
        preference.setTitle(title)
        preference.isIconSpaceReserved = false
        preference.isPersistent = false
        preference.setOnPreferenceClickListener {
            onClick()
            true
        }
        addPreference(preference)
        return preference
    }

    private fun PreferenceGroup.slider(
        @StringRes title: Int,
        from: Int,
        to: Int,
        value: Int,
        onChange: (Int) -> Unit,
    ): SliderPreference {
        val preference = SliderPreference(context)
        preference.setTitle(title)
        preference.isIconSpaceReserved = false
        preference.valueFrom = from
        preference.valueTo = to
        preference.value = value
        preference.onValueChange = onChange
        addPreference(preference)
        return preference
    }

    private fun PreferenceGroup.switch(@StringRes title: Int, checked: Boolean, onChange: (Boolean) -> Unit): SwitchPreferenceCompat {
        val preference = SwitchPreferenceCompat(context)
        preference.setTitle(title)
        preference.isIconSpaceReserved = false
        preference.isPersistent = false
        preference.widgetLayoutResource = R.layout.preference_material_switch
        preference.isChecked = checked
        preference.setOnPreferenceChangeListener { _, newValue ->
            onChange(newValue as Boolean)
            true
        }
        addPreference(preference)
        return preference
    }

    private fun PreferenceGroup.splitSwitch(
        @StringRes title: Int,
        checked: Boolean,
        onChange: (Boolean) -> Unit,
        onClick: () -> Unit,
    ): SplitSwitchPreference {
        val preference = SplitSwitchPreference(context)
        preference.setTitle(title)
        preference.isIconSpaceReserved = false
        preference.isChecked = checked
        preference.onCheckedChange = onChange
        preference.setOnPreferenceClickListener {
            onClick()
            true
        }
        addPreference(preference)
        return preference
    }
}
