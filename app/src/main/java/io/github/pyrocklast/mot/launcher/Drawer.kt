package io.github.pyrocklast.mot.launcher

import android.annotation.SuppressLint
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.AppRepository
import io.github.pyrocklast.mot.data.Prefs
import io.github.pyrocklast.mot.data.Profile
import io.github.pyrocklast.mot.databinding.ActivityLauncherBinding
import io.github.pyrocklast.mot.databinding.DialogRenameBinding
import io.github.pyrocklast.mot.util.Apps
import io.github.pyrocklast.mot.util.Search

/**
 * The app drawer, a panel over the home screen. It lists your apps for opening, or, when
 * [open] is given a [Slot], for choosing the app that spot opens.
 */
class Drawer(
    private val activity: AppCompatActivity,
    private val binding: ActivityLauncherBinding,
    private val prefs: Prefs,
    private val repo: AppRepository,
    private val onChosen: () -> Unit,
) {
    var isOpen = false
        private set

    /** Unlocking Private Space shows Android's lock screen; the drawer stays open behind it. */
    var keepOpenWhileAway = false
        private set

    /** Back from Android's lock screen (or anywhere else): the next trip away closes the drawer again. */
    fun onLauncherResumed() {
        keepOpenWhileAway = false
    }

    private var choosingFor: Slot? = null
    private var dialog: AlertDialog? = null
    private val search = binding.search
    private val list = binding.appList

    private val adapter = DrawerAdapter(
        nameSizePx = activity.resources.getDimension(R.dimen.app_name_size) * prefs.appNameScale,
        gravity = prefs.drawerAlignment,
        onClick = ::onRowClick,
        onLongClick = ::onRowLongClick,
    )

    // Touches on the drawer's empty space stay in the drawer (they'd reach the home screen
    // underneath otherwise), and a swipe down there closes it
    private val swipeDown = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val start = e1 ?: return false
            if (e2.y - start.y > 0 && velocityY > 0 && velocityY > kotlin.math.abs(velocityX)) close()
            return true
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    private fun onDrawerTouch(@Suppress("UNUSED_PARAMETER") view: View, event: MotionEvent): Boolean {
        swipeDown.onTouchEvent(event)
        return true
    }

    init {
        binding.drawer.setOnTouchListener(::onDrawerTouch)
        list.adapter = adapter
        list.itemAnimator = null
        list.layoutManager = object : LinearLayoutManager(activity) {
            // Pulling down past the top of the list closes the drawer
            override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
                val scrolled = super.scrollVerticallyBy(dy, recycler, state)
                if (dy - scrolled < -OVERSCROLL_TO_CLOSE && list.scrollState == RecyclerView.SCROLL_STATE_DRAGGING) close()
                return scrolled
            }
        }
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                // The keyboard makes way while you scroll, and comes back when you're at the top again
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) hideKeyboard()
                else if (newState == RecyclerView.SCROLL_STATE_IDLE && !recyclerView.canScrollVertically(-1) && isOpen)
                    showKeyboardIfWanted()
            }
        })

        val textSize = activity.resources.getDimension(R.dimen.app_name_size) * prefs.appNameScale
        search.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSize)
        search.gravity = prefs.drawerAlignment or Gravity.CENTER_VERTICAL
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (isOpen) update(typed = true)
            }
        })
        // Enter opens the first app listed
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_GO) return@setOnEditorActionListener false
            adapter.currentList.firstOrNull { it is DrawerRow.App }?.let { onRowClick(it, search) }
            true
        }
        describeSearchForTalkBack()

        // Room for the navigation bar, or the keyboard when it's open
        ViewCompat.setOnApplyWindowInsetsListener(binding.drawer) { view, insets ->
            val bottom = maxOf(
                insets.getInsets(WindowInsetsCompat.Type.ime()).bottom,
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom,
            )
            view.updatePadding(bottom = bottom)
            insets
        }
    }

    fun open(slot: Slot? = null) {
        choosingFor = slot
        isOpen = true
        search.text = null
        search.hint = activity.getString(if (slot != null) R.string.choose_an_app else R.string.search_hint)
        update(typed = false)
        list.scrollToPosition(0)

        binding.drawer.isVisible = true
        binding.drawer.alpha = 0f
        binding.drawer.animate().alpha(1f).setDuration(OPEN_MS).start()
        // The rows rise into place while the keyboard comes up
        list.scheduleLayoutAnimation()
        showKeyboardIfWanted()
    }

    fun close(animate: Boolean = true) {
        if (!isOpen) return
        isOpen = false
        choosingFor = null
        keepOpenWhileAway = false
        dialog?.dismiss()
        hideKeyboard()
        search.text = null
        if (animate) {
            binding.drawer.animate().alpha(0f).setDuration(CLOSE_MS).withEndAction {
                if (!isOpen) binding.drawer.isVisible = false
            }.start()
        } else {
            binding.drawer.animate().cancel()
            binding.drawer.isVisible = false
        }
    }

    /** Rebuilds the list from the current apps, hidden apps and search. */
    fun update(typed: Boolean = false) {
        if (!isOpen) return
        val apps = repo.apps.value.orEmpty()
        val space = repo.privateSpace.value
        val query = Search.normalize(search.text ?: "")
        val choosing = choosingFor != null
        val hidden = if (choosing) emptySet() else prefs.hiddenApps

        val rows = ArrayList<DrawerRow>()
        apps.filter { it.profile != Profile.PRIVATE && it.key !in hidden && Search.matches(it.searchText, query) }
            .mapTo(rows) { DrawerRow.App(it) }
        // Private Space comes last, under its own header (not while searching, when its apps simply join in)
        if (space != null) {
            if (query.isEmpty()) rows += DrawerRow.PrivateSpace(space.locked)
            apps.filter { it.profile == Profile.PRIVATE && it.key !in hidden && Search.matches(it.searchText, query) }
                .mapTo(rows) { DrawerRow.App(it) }
        }

        adapter.submitList(rows) {
            if (typed && !choosing) maybeOpenOnlyMatch()
        }
    }

    // Typing until one app is left opens it, unless the search starts with a space
    private fun maybeOpenOnlyMatch() {
        val text = search.text?.toString().orEmpty()
        if (text.isEmpty() || text.startsWith(" ") || isComposingCjk()) return
        val only = adapter.currentList.singleOrNull() as? DrawerRow.App ?: return
        onRowClick(only, search)
    }

    private fun onRowClick(row: DrawerRow, view: View) {
        when (row) {
            is DrawerRow.PrivateSpace -> {
                // Unlocking shows Android's lock screen; stay open for when it comes back
                keepOpenWhileAway = row.locked
                repo.setPrivateSpaceLocked(!row.locked)
            }
            is DrawerRow.App -> {
                val slot = choosingFor
                if (slot != null) {
                    slot.save(prefs, row.entry.target)
                    close()
                    onChosen()
                } else {
                    // The drawer closes when the app covers the launcher (LauncherActivity.onStop)
                    Apps.open(activity, row.entry.target, view)
                }
            }
        }
    }

    private fun onRowLongClick(row: DrawerRow, view: View) {
        when (row) {
            is DrawerRow.PrivateSpace -> Apps.openPrivateSpaceSettings(activity)
            is DrawerRow.App -> showMenu(row, view)
        }
    }

    // Long-press menu: Rename, Hide, App info, Uninstall (or Remove, for a shortcut)
    private fun showMenu(row: DrawerRow.App, anchor: View) {
        val entry = row.entry
        val shortcut = entry.target.isShortcut
        val popup = PopupMenu(activity, anchor, if (prefs.drawerAlignment == Gravity.END) Gravity.END else Gravity.START)
        popup.menu.add(Menu.NONE, MENU_RENAME, 0, R.string.rename)
        if (!shortcut && choosingFor == null) popup.menu.add(Menu.NONE, MENU_HIDE, 1, R.string.hide)
        popup.menu.add(Menu.NONE, MENU_INFO, 2, R.string.app_info)
        popup.menu.add(Menu.NONE, MENU_REMOVE, 3, if (shortcut) R.string.remove else R.string.uninstall)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_RENAME -> showRename(row)
                MENU_HIDE -> hide(row)
                MENU_INFO -> Apps.openAppInfo(activity, entry.target)
                MENU_REMOVE -> if (shortcut) Apps.unpin(activity, entry.target) else Apps.uninstall(activity, entry.target)
            }
            true
        }
        popup.show()
    }

    // A new name shows everywhere the app is: drawer, home screen and settings
    private fun showRename(row: DrawerRow.App) {
        val entry = row.entry
        val builder = MaterialAlertDialogBuilder(activity)
        val rename = DialogRenameBinding.inflate(LayoutInflater.from(builder.context))
        rename.nameLayout.hint = entry.ownLabel
        rename.name.setText(entry.label)
        rename.name.setSelectAllOnFocus(true)

        fun save(name: String?) {
            prefs.setCustomName(entry.key, name?.takeIf { it.isNotBlank() && it.trim() != entry.ownLabel })
            repo.namesChanged()
        }

        dialog?.dismiss()
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

    private fun hide(row: DrawerRow.App) {
        prefs.hiddenApps = prefs.hiddenApps + row.entry.key
        if (!prefs.hiddenBefore) {
            prefs.hiddenBefore = true
            Toast.makeText(
                activity,
                activity.getString(R.string.first_hide, activity.getString(R.string.app_drawer), activity.getString(R.string.hidden_apps)),
                Toast.LENGTH_LONG,
            ).show()
        }
        update()
    }

    // Keyboard

    private fun showKeyboardIfWanted() {
        if (!prefs.autoKeyboard) return
        search.requestFocus()
        search.post { WindowCompat.getInsetsController(activity.window, search).show(WindowInsetsCompat.Type.ime()) }
    }

    private fun hideKeyboard() {
        WindowCompat.getInsetsController(activity.window, search).hide(WindowInsetsCompat.Type.ime())
        search.clearFocus()
    }

    // Chinese, Japanese and Korean keyboards compose a word before committing it: don't open
    // an app halfway through
    private fun isComposingCjk(): Boolean {
        val text = search.text as? Spannable ?: return false
        val start = BaseInputConnection.getComposingSpanStart(text)
        val end = BaseInputConnection.getComposingSpanEnd(text)
        if (start < 0 || end <= start) return false
        val language = activity.getSystemService(InputMethodManager::class.java).currentInputMethodSubtype?.languageTag.orEmpty()
        return language.startsWith("zh") || language.startsWith("ja") || language.startsWith("ko")
    }

    // The visible hint is a few underscores; TalkBack says "Search apps" instead
    private fun describeSearchForTalkBack() {
        ViewCompat.setAccessibilityDelegate(search, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                if (choosingFor == null) info.hintText = activity.getString(R.string.search_apps)
            }
        })
    }

    private companion object {
        const val OPEN_MS = 150L
        const val CLOSE_MS = 120L
        const val OVERSCROLL_TO_CLOSE = 10

        const val MENU_RENAME = 1
        const val MENU_HIDE = 2
        const val MENU_INFO = 3
        const val MENU_REMOVE = 4
    }
}
