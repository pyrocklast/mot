package io.github.pyrocklast.mot.settings

import android.app.AlertDialog
import android.content.Context
import androidx.annotation.StringRes
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.AppRepository
import io.github.pyrocklast.mot.data.AppTarget
import io.github.pyrocklast.mot.data.Prefs
import io.github.pyrocklast.mot.launcher.Slot

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

/**
 * The name of the app a setting opens, or [default]. An app in a locked Private Space is only
 * named once it's unlocked, and while it's hidden too, the default shows, as that's what opens.
 */
internal fun Context.appName(repo: AppRepository, target: AppTarget?, @StringRes default: Int): String = when {
    target == null || repo.isConcealed(target) -> getString(default)
    repo.isLockedPrivateSpace(target.userSerial) -> getString(R.string.private_space)
    else -> repo.nameFor(target) ?: getString(default)
}
