package io.github.pyrocklast.mot.settings

import android.app.AlertDialog
import android.content.Context
import androidx.annotation.StringRes
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.AppRepository
import io.github.pyrocklast.mot.data.AppTarget
import io.github.pyrocklast.mot.data.Prefs
import io.github.pyrocklast.mot.launcher.Slot
import java.text.NumberFormat

// Dialogs and labels shared by the settings screens. The rows themselves are in Rows.kt.

// Dialogs

/** A list of choices with the current one ticked; picking one closes it. */
internal fun SettingsScreen.choiceDialog(
    @StringRes title: Int,
    labels: Array<CharSequence>,
    checked: Int,
    onChoice: (Int) -> Unit,
) = showDialog(
    AlertDialog.Builder(this)
        .setTitle(title)
        .setSingleChoiceItems(labels, checked) { dialog, which ->
            dialog.dismiss()
            onChoice(which)
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
)

/**
 * Android's default app for something (e.g. "Default clock app"), or one you choose from the list
 * of apps. [onDefault] runs after going back to the default.
 */
internal fun SettingsScreen.appChoiceDialog(
    prefs: Prefs,
    @StringRes title: Int,
    @StringRes defaultLabel: Int,
    current: AppTarget?,
    slot: Slot,
    onDefault: () -> Unit,
) {
    val labels = arrayOf<CharSequence>(getString(defaultLabel), getString(R.string.choose_app_ellipsis))
    choiceDialog(title, labels, if (current == null) 0 else 1) { which ->
        if (which == 0) {
            slot.save(prefs, null)
            onDefault()
        } else startActivity(AppListActivity.chooseApp(this, slot))
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
