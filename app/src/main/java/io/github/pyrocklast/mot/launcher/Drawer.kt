package io.github.pyrocklast.mot.launcher

import android.annotation.SuppressLint
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.AppRepository
import io.github.pyrocklast.mot.data.Prefs
import io.github.pyrocklast.mot.data.Profile
import io.github.pyrocklast.mot.databinding.ActivityLauncherBinding
import io.github.pyrocklast.mot.util.Apps
import io.github.pyrocklast.mot.util.Look
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
    private val menu: AppMenu,
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

    /** Showing Private Space's apps instead of yours. */
    private var inPrivateSpace = false

    /** Private Space is being unlocked from the drawer: open it once it is. */
    private var openPrivateSpaceWhenUnlocked = false
    private val search = binding.search
    private val list = binding.appList

    private val adapter = DrawerAdapter(
        nameSizePx = activity.resources.getDimension(R.dimen.app_name_size) * prefs.appNameScale,
        nameTypeface = Look.typeface(prefs.nameFont, prefs.nameWeight),
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
        search.typeface = Look.typeface(prefs.nameFont, prefs.nameWeight)
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
        inPrivateSpace = false
        search.text = null
        setSearchHint()
        update(typed = false)
        list.scrollToPosition(0)

        binding.drawer.isVisible = true
        binding.drawer.alpha = 0f
        binding.drawer.animate().alpha(1f).setDuration(OPEN_MS).start()
        // Only the wallpaper shows through, not the home screen
        binding.home.animate().alpha(0f).setDuration(OPEN_MS).withEndAction {
            if (isOpen) binding.home.isInvisible = true
        }.start()
        // The rows rise into place while the keyboard comes up
        list.scheduleLayoutAnimation()
        showKeyboardIfWanted()
    }

    fun close(animate: Boolean = true) {
        if (!isOpen) return
        isOpen = false
        choosingFor = null
        inPrivateSpace = false
        openPrivateSpaceWhenUnlocked = false
        keepOpenWhileAway = false
        menu.dismiss()
        hideKeyboard()
        search.text = null
        binding.home.isInvisible = false
        if (animate) {
            binding.drawer.animate().alpha(0f).setDuration(CLOSE_MS).withEndAction {
                if (!isOpen) hideDrawer()
            }.start()
            binding.home.animate().alpha(1f).setDuration(CLOSE_MS).start()
        } else {
            binding.drawer.animate().cancel()
            hideDrawer()
            binding.home.animate().cancel()
            binding.home.alpha = 1f
        }
    }

    private fun hideDrawer() {
        binding.drawer.isVisible = false
        // Undo any shrinking from a back gesture
        binding.drawer.scaleX = 1f
        binding.drawer.scaleY = 1f
    }

    /** Back: from Private Space to your apps, otherwise closes the drawer. */
    fun back() {
        if (inPrivateSpace) showPrivateSpace(false) else close()
    }

    /** Android's back gesture in progress (0 to 1): the drawer shrinks and fades a little, following it. */
    fun backProgress(progress: Float) {
        // Back from Private Space doesn't close the drawer, so nothing shrinks
        if (!isOpen || inPrivateSpace) return
        val scale = 1f - BACK_SHRINK * progress
        binding.drawer.scaleX = scale
        binding.drawer.scaleY = scale
        binding.drawer.alpha = 1f - BACK_FADE * progress
    }

    /** The back gesture was let go of without going back: the drawer returns to full size. */
    fun backCancelled() {
        if (!isOpen) return
        binding.drawer.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(CLOSE_MS).start()
    }

    /** Rebuilds the list from the current apps, hidden apps and search. */
    fun update(typed: Boolean = false) {
        if (!isOpen) return
        val apps = repo.apps.value.orEmpty()
        val space = repo.privateSpace.value
        val unlocked = space != null && !space.locked
        // Unlocked from here: go in. Locked (here, from quick settings or by Android): come out.
        if (openPrivateSpaceWhenUnlocked && unlocked) {
            openPrivateSpaceWhenUnlocked = false
            showPrivateSpace(true)
            return
        }
        if (inPrivateSpace && !unlocked) {
            showPrivateSpace(false)
            return
        }

        val query = Search.normalize(search.text ?: "")
        val choosing = choosingFor != null
        val hidden = if (choosing) emptySet() else prefs.hiddenApps

        // Your apps, then a small "Private space" row; or Private Space's apps, then "Lock".
        // The two never mix, in the list or in search.
        val rows = ArrayList<DrawerRow>()
        apps.filter {
            (it.profile == Profile.PRIVATE) == inPrivateSpace && it.appKey !in hidden && Search.matches(it.searchText, query)
        }.mapTo(rows) { DrawerRow.App(it) }
        if (query.isEmpty() && space != null) {
            rows += if (inPrivateSpace) DrawerRow.LockPrivateSpace else DrawerRow.OpenPrivateSpace(space.locked)
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
            is DrawerRow.OpenPrivateSpace -> {
                if (row.locked) {
                    // Unlocking shows Android's lock screen; the drawer waits behind it, then goes in
                    openPrivateSpaceWhenUnlocked = true
                    keepOpenWhileAway = true
                    repo.setPrivateSpaceLocked(false)
                } else showPrivateSpace(true)
            }
            DrawerRow.LockPrivateSpace -> {
                repo.setPrivateSpaceLocked(true)
                showPrivateSpace(false)
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
            is DrawerRow.OpenPrivateSpace, DrawerRow.LockPrivateSpace -> Apps.openPrivateSpaceSettings(activity)
            is DrawerRow.App -> menu.show(
                anchor = view,
                entry = row.entry,
                alignment = prefs.drawerAlignment,
                canHide = choosingFor == null,
                onHidden = { update() },
            )
        }
    }

    // Switches between your apps and Private Space's, starting from an empty search at the top
    private fun showPrivateSpace(show: Boolean) {
        inPrivateSpace = show
        search.text = null
        setSearchHint()
        update()
        list.scrollToPosition(0)
        list.scheduleLayoutAnimation()
        if (show) showKeyboardIfWanted()
    }

    // Underscores normally; "Choose an app" when choosing one; "Private space" inside it
    private fun setSearchHint() {
        search.hint = activity.getString(
            when {
                inPrivateSpace -> R.string.private_space
                choosingFor != null -> R.string.choose_an_app
                else -> R.string.search_hint
            }
        )
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
                if (choosingFor == null && !inPrivateSpace) info.hintText = activity.getString(R.string.search_apps)
            }
        })
    }

    private companion object {
        const val OPEN_MS = 150L
        const val CLOSE_MS = 120L
        const val BACK_SHRINK = 0.1f
        const val BACK_FADE = 0.4f
        const val OVERSCROLL_TO_CLOSE = 10

    }
}
