package io.github.pyrocklast.mot.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.AppRepository
import io.github.pyrocklast.mot.data.AppTarget
import io.github.pyrocklast.mot.data.Prefs
import io.github.pyrocklast.mot.launcher.Slot
import java.text.NumberFormat

// Rows, labels and dialogs shared by the settings screens. No row saves itself: each shows a
// value from Prefs and writes changes back, so Prefs stays the one source of truth.

// Rows

internal fun PreferenceGroup.category(
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

internal fun PreferenceGroup.item(@StringRes title: Int, onClick: () -> Unit): Preference {
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

internal fun PreferenceGroup.slider(
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

internal fun PreferenceGroup.switch(@StringRes title: Int, checked: Boolean, onChange: (Boolean) -> Unit): SwitchPreferenceCompat {
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

internal fun PreferenceGroup.splitSwitch(
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

// Dialogs

/** A list of choices with the current one ticked; picking one closes it. */
internal fun Fragment.choiceDialog(
    @StringRes title: Int,
    labels: Array<CharSequence>,
    checked: Int,
    onChoice: (Int) -> Unit,
): AlertDialog = MaterialAlertDialogBuilder(requireContext())
    .setTitle(title)
    .setSingleChoiceItems(labels, checked) { dialog, which ->
        dialog.dismiss()
        onChoice(which)
    }
    .setNegativeButton(android.R.string.cancel, null)
    .show()

/**
 * Android's default app for something (e.g. "Default clock app"), or one you choose from the list
 * of apps. [onDefault] runs after going back to the default.
 */
internal fun Fragment.appChoiceDialog(
    prefs: Prefs,
    @StringRes title: Int,
    @StringRes defaultLabel: Int,
    current: AppTarget?,
    slot: Slot,
    onDefault: () -> Unit,
): AlertDialog {
    val labels = arrayOf<CharSequence>(getString(defaultLabel), getString(R.string.choose_app_ellipsis))
    return choiceDialog(title, labels, if (current == null) 0 else 1) { which ->
        if (which == 0) {
            slot.save(prefs, null)
            onDefault()
        } else (requireActivity() as SettingsActivity).show(AppListFragment.chooseApp(slot))
    }
}

// Labels

/** The name of the app a setting opens, or [default]. An app in a locked Private Space is only named once it's unlocked. */
internal fun Context.appName(repo: AppRepository, target: AppTarget?, @StringRes default: Int): String = when {
    target == null -> getString(default)
    repo.isLockedPrivateSpace(target.userSerial) -> getString(R.string.private_space)
    else -> repo.nameFor(target) ?: getString(default)
}

/** e.g. "Sans serif · Light · 100 %" */
internal fun Context.styleSummary(font: Int, weight: Int, scale: Float): String =
    listOf(getString(fontLabel(font)), getString(weightLabel(weight)), percent(scale)).joinToString(SEPARATOR)

internal const val SEPARATOR = " · "

/** e.g. "110 %" in French. */
internal fun percent(scale: Float): String = NumberFormat.getPercentInstance().format(scale.toDouble())

@StringRes
internal fun fontLabel(font: Int) = when (font) {
    Prefs.FONT_SERIF -> R.string.font_serif
    Prefs.FONT_MONO -> R.string.font_mono
    Prefs.FONT_CONDENSED -> R.string.font_condensed
    else -> R.string.font_sans
}

@StringRes
internal fun weightLabel(weight: Int) = when {
    weight < 200 -> R.string.weight_thin
    weight < 350 -> R.string.weight_light
    weight < 450 -> R.string.weight_regular
    weight < 600 -> R.string.weight_medium
    else -> R.string.weight_bold
}
