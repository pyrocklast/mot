package io.github.pyrocklast.mot.launcher

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.MenuCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.AppEntry
import io.github.pyrocklast.mot.data.AppRepository
import io.github.pyrocklast.mot.data.Prefs
import io.github.pyrocklast.mot.databinding.DialogRenameBinding
import io.github.pyrocklast.mot.util.Apps

/**
 * The long-press menu for an app or shortcut: Rename, Hide, App info, Uninstall (Remove, for a
 * shortcut). The drawer and the home screen share it; the home screen adds its own items on top,
 * and leaves out Hide, which is about the drawer.
 */
class AppMenu(
    private val activity: AppCompatActivity,
    private val prefs: Prefs,
    private val repo: AppRepository,
) {
    /** An item shown above the usual ones, like the home screen's "Change app". */
    class Extra(@StringRes val title: Int, val onClick: () -> Unit)

    private var popup: PopupMenu? = null
    private var dialog: AlertDialog? = null

    /**
     * Shows the menu next to [anchor]. [onHidden] runs after the app is hidden from the drawer;
     * [canHide] is false where hiding makes no sense (choosing an app for a spot).
     */
    fun show(
        anchor: View,
        entry: AppEntry,
        alignment: Int,
        extras: List<Extra> = emptyList(),
        canHide: Boolean = true,
        onHidden: () -> Unit = {},
    ) {
        dismiss()
        val shortcut = entry.target.isShortcut
        val menu = PopupMenu(activity, anchor, if (alignment == Gravity.END) Gravity.END else Gravity.START)
        // The extras (e.g. the home screen's) and the app's own items, with a line between them
        extras.forEachIndexed { index, extra -> menu.menu.add(GROUP_EXTRAS, EXTRA + index, index, extra.title) }
        val order = extras.size
        menu.menu.add(GROUP_APP, RENAME, order, R.string.rename)
        if (!shortcut && canHide) menu.menu.add(GROUP_APP, HIDE, order + 1, R.string.hide)
        menu.menu.add(GROUP_APP, INFO, order + 2, R.string.app_info)
        menu.menu.add(GROUP_APP, REMOVE, order + 3, if (shortcut) R.string.remove else R.string.uninstall)
        MenuCompat.setGroupDividerEnabled(menu.menu, extras.isNotEmpty())
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                RENAME -> rename(entry)
                HIDE -> hide(entry, onHidden)
                INFO -> Apps.openAppInfo(activity, entry.target)
                REMOVE -> if (shortcut) Apps.unpin(activity, entry.target) else Apps.uninstall(activity, entry.target)
                else -> extras.getOrNull(item.itemId - EXTRA)?.onClick?.invoke()
            }
            true
        }
        menu.show()
        popup = menu
    }

    fun dismiss() {
        popup?.dismiss()
        popup = null
        dialog?.dismiss()
        dialog = null
    }

    // A new name shows everywhere the app is: drawer, home screen and settings
    private fun rename(entry: AppEntry) {
        val builder = MaterialAlertDialogBuilder(activity)
        val rename = DialogRenameBinding.inflate(LayoutInflater.from(builder.context))
        rename.nameLayout.hint = entry.ownLabel
        rename.name.setText(entry.label)
        rename.name.setSelectAllOnFocus(true)

        fun save(name: String?) {
            prefs.setCustomName(entry.appKey, name?.takeIf { it.isNotBlank() && it.trim() != entry.ownLabel })
            repo.namesChanged()
        }

        val renameDialog = builder
            .setTitle(R.string.rename)
            .setView(rename.root)
            .setPositiveButton(R.string.rename) { _, _ -> save(rename.name.text?.toString()) }
            .setNeutralButton(R.string.original_name) { _, _ -> save(null) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        rename.name.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            save(rename.name.text?.toString())
            renameDialog.dismiss()
            true
        }
        renameDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        renameDialog.show()
        rename.name.requestFocus()
        dialog = renameDialog
    }

    private fun hide(entry: AppEntry, onHidden: () -> Unit) {
        prefs.hiddenApps = prefs.hiddenApps + entry.appKey
        if (!prefs.hiddenBefore) {
            prefs.hiddenBefore = true
            Toast.makeText(
                activity,
                activity.getString(R.string.first_hide, activity.getString(R.string.app_drawer), activity.getString(R.string.hidden_apps)),
                Toast.LENGTH_LONG,
            ).show()
        }
        onHidden()
    }

    private companion object {
        const val GROUP_EXTRAS = 1
        const val GROUP_APP = 2

        const val RENAME = 1
        const val HIDE = 2
        const val INFO = 3
        const val REMOVE = 4
        const val EXTRA = 100
    }
}
