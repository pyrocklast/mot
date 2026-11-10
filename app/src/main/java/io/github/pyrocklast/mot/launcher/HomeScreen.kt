package io.github.pyrocklast.mot.launcher

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isVisible
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.AppRepository
import io.github.pyrocklast.mot.data.AppTarget
import io.github.pyrocklast.mot.data.Prefs
import io.github.pyrocklast.mot.databinding.ActivityLauncherBinding
import io.github.pyrocklast.mot.util.Apps
import io.github.pyrocklast.mot.util.DateText
import io.github.pyrocklast.mot.util.DefaultApp
import io.github.pyrocklast.mot.util.HomeFit
import io.github.pyrocklast.mot.util.TimeText
import kotlin.math.abs

/**
 * The home screen: the clock and date, your home apps, and every gesture. One gesture detector
 * on the whole screen handles taps, long presses and swipes, wherever they start.
 */
class HomeScreen(
    private val context: Context,
    private val binding: ActivityLauncherBinding,
    private val prefs: Prefs,
    private val repo: AppRepository,
    private val menu: AppMenu,
    private val openDrawer: (Slot?) -> Unit,
    private val openSettings: () -> Unit,
    private val requestHomeRole: () -> Unit,
) {
    /** The home apps as last drawn: the names on screen are these, in this order. */
    private var shown: List<AppTarget> = emptyList()
    private val appViews = ArrayList<TextView>()
    private var overflowView: TextView? = null
    private var homeMenu: PopupMenu? = null
    private var homeMenuAnchor: View? = null

    // Where the system bars are, until Android says otherwise: the home screen draws under them
    private var topInset = (24 * context.resources.displayMetrics.density).toInt()
    private var bottomInset = (40 * context.resources.displayMetrics.density).toInt()
    private val config = ViewConfiguration.get(context)
    private val minSwipeDistance = SWIPE_DISTANCE_DP * context.resources.displayMetrics.density
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            tap(viewAt(e))
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            binding.home.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val view = viewAt(e)
            val index = appViews.indexOf(view)
            if (view == null || index < 0) return longPress(view, e)
            // A home app picks up, and moves if you drag it. Its menu waits until you let go
            // without dragging, so it never covers the spots the app could be dragged to.
            if (canMove(index)) reorder.arm(index, e.rawY, lift = true) else homeAppMenu(view, index)
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val start = e1 ?: return false
            val dx = e2.x - start.x
            val dy = e2.y - start.y
            val minVelocity = config.scaledMinimumFlingVelocity
            if (abs(dx) > abs(dy)) {
                if (abs(dx) < minSwipeDistance || abs(velocityX) < minVelocity) return false
                if (dx < 0) swipe(left = true) else swipe(left = false)
            } else {
                if (abs(dy) < minSwipeDistance || abs(velocityY) < minVelocity) return false
                if (dy < 0) openDrawer(null) else Apps.expandNotifications(context)
            }
            return true
        }
    })

    private val reorder = HomeReorder(
        apps = { appViews.filter { it.isVisible } },
        others = { listOf(binding.clockBlock) },
        onStart = { menu.dismiss() },
        onRelease = { index -> appViews.getOrNull(index)?.let { homeAppMenu(it, index) } },
        onMove = ::move,
    )

    init {
        // Taps reach TalkBack users through the actions set up in describeForTalkBack
        binding.home.setOnTouchListener(::onTouch)
        // TalkBack and Switch Access can't swipe or long press the whole screen, so the drawer
        // and settings are actions on it instead
        binding.home.isFocusable = true
        binding.home.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        ViewCompat.addAccessibilityAction(binding.home, context.getString(R.string.action_open_app_drawer)) { _, _ ->
            openDrawer(null)
            true
        }
        ViewCompat.addAccessibilityAction(binding.home, context.getString(R.string.action_open_settings)) { _, _ ->
            openSettings()
            true
        }
        // The first render can come before layout: draw again once the screen's real size is
        // known, or when it changes, but not when only the padding does
        binding.homeApps.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) render()
        }
        binding.setDefault.setOnClickListener { requestHomeRole() }
        describeForTalkBack(binding.clock, R.string.action_open_clock, R.string.action_choose_clock) { tap(it) }
        describeForTalkBack(binding.date, R.string.action_open_calendar, R.string.action_choose_calendar) { tap(it) }
    }

    /**
     * How many apps there's room for on this screen, as last drawn. Adding an app stops here; the
     * drawer and the home screen's own menu ask.
     */
    var capacity = 0
        private set

    /** The layout last drawn: the time and date use it as they change each minute. */
    private var fit: HomeFit.Layout? = null

    /** Draws the home screen from the current settings and app names. Cheap enough to call often. */
    fun render() {
        val displayHeight = if (binding.homeApps.height > 0) binding.homeApps.height else screenHeight()
        val fit = HomeFit.layout(context, prefs, displayHeight, topInset, bottomInset)
        this.fit = fit
        capacity = fit.capacity

        // The time and date, sized for the screen, the date's capitals tucked in under the time.
        // With the status bar hidden, they move up into its space.
        binding.clock.isVisible = prefs.showTime
        binding.date.isVisible = prefs.showDate
        binding.clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, fit.timeSize)
        binding.date.setTextSize(TypedValue.COMPLEX_UNIT_PX, fit.dateSize)
        setTopMargin(binding.clockBlock, fit.clockTop)
        setTopMargin(binding.date, fit.dateMargin)
        updateClock()

        // One row per home app, but none from a Private Space that's locked and hidden: they
        // come back when it's unlocked
        shown = prefs.homeApps.filterNot { repo.isConcealed(it) }
        val count = shown.size
        while (appViews.size < count) appViews += newAppView()
        while (appViews.size > count) binding.homeApps.removeView(appViews.removeAt(appViews.lastIndex))
        appViews.forEachIndexed { index, view ->
            val target = shown[index]
            view.text = repo.nameFor(target) ?: context.getString(
                if (repo.isLockedPrivateSpace(target.userSerial)) R.string.private_space_locked
                else R.string.app_not_found,
            )
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, fit.nameSize)
            setHeight(view, fit.row)
        }

        // The first-run tips and the "Set as default launcher" link sit over the bottom of the
        // home screen: while they show, the names stop above them
        binding.tips.isVisible = !prefs.settingsOpened
        binding.setDefault.isVisible = !prefs.settingsOpened && !Apps.isDefaultLauncher(context)
        val overlayClearance = listOf(binding.tips, binding.setDefault)
            .filter { it.isVisible }
            .maxOfOrNull {
                (it.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin +
                    heightOf(it) + (8 * context.resources.displayMetrics.density).toInt()
            } ?: 0
        val bottomPadding = maxOf(fit.bottom, overlayClearance)
        val room = (displayHeight - fit.namesTop - bottomPadding).coerceAtLeast(0)

        // The home screen never scrolls (swiping is for gestures). Adding apps stops when the
        // screen is full, but making names bigger afterwards can leave too many: then the last
        // row becomes "All apps", rather than names quietly going missing.
        val indicator = overflowView ?: (LayoutInflater.from(binding.homeApps.context)
            .inflate(R.layout.item_home_app, binding.homeApps, false) as TextView).also { view ->
            // Made like a name, so it has the launcher's text colour and shadow
            view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, HomeFit.ALL_APPS_DP)
            view.setText(R.string.home_all_apps)
            // Not clickable itself, or it would swallow swipes: the gesture detector handles
            // taps, and TalkBack gets an action
            describeForTalkBack(
                view, R.string.home_all_apps_description, R.string.home_all_apps_description,
            ) { openDrawer(null) }
            overflowView = view
            binding.homeApps.addView(view)
        }
        setHeight(indicator, fit.row)
        // Always the last row, after any names added since it was made
        indicator.bringToFront()
        val layout = HomeFit.visibleRows(count, HomeFit.slots(room, fit.row, 0))
        appViews.forEachIndexed { index, view -> view.isVisible = index < layout.count }
        indicator.isVisible = layout.showAllApps
        val groupHeight = fit.row * (layout.count + if (layout.showAllApps) 1 else 0)
        val balancedTop = HomeFit.balancedTop(
            displayHeight, topInset, bottomPadding, fit.namesTop, groupHeight,
        )
        binding.homeApps.setPadding(
            binding.homeApps.paddingLeft, balancedTop,
            binding.homeApps.paddingRight, bottomPadding,
        )
    }

    private fun setTopMargin(view: View, margin: Int) {
        val params = view.layoutParams as ViewGroup.MarginLayoutParams
        if (params.topMargin == margin) return
        params.topMargin = margin
        view.layoutParams = params
    }

    // Every row the same height, with its name in the middle of it
    private fun setHeight(view: View, height: Int) {
        val params = view.layoutParams
        if (params.height == height) return
        params.height = height
        view.layoutParams = params
    }

    // How tall a view comes out at its current text, size and font
    private fun heightOf(view: View): Int {
        // At the width it really has: measured without one, text that wraps on a narrow screen
        // would come out a line short
        val screenWidth = binding.home.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
        val margins = view.layoutParams as? ViewGroup.MarginLayoutParams
        val width = (screenWidth - (margins?.leftMargin ?: 0) - (margins?.rightMargin ?: 0)).coerceAtLeast(1)
        val atMost = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST)
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(atMost, unspecified)
        return view.measuredHeight
    }

    private fun screenHeight(): Int = context.resources.displayMetrics.heightPixels

    private fun newAppView(): TextView {
        val view = LayoutInflater.from(binding.homeApps.context)
            .inflate(R.layout.item_home_app, binding.homeApps, false) as TextView
        binding.homeApps.addView(view)
        describeForTalkBack(view, R.string.action_open, R.string.action_more_options, canBeMoved = true) { tap(it) }
        return view
    }

    // The time and the date, each left as it is when it hasn't changed
    private fun updateClock() {
        val fit = fit ?: return
        if (prefs.showTime) {
            val time = TimeText.time(context, fit.colonLift, fit.dateSize, fit.markerGap)
            if (binding.clock.text.toString() != time.toString()) {
                binding.clock.text = time
                alignInk(binding.clock)
            }
        }
        if (prefs.showDate) {
            val date = DateText.date(context)
            if (binding.date.text.toString() != date) {
                binding.date.text = date
                alignInk(binding.date)
            }
        }
    }

    // Puts the first letter or figure itself on the margin, rather than the room the font keeps
    // before it, which at the time's size is a few dp, and different for each figure
    private fun alignInk(view: TextView) {
        val text = view.text
        if (text.isEmpty() || view.layoutDirection != View.LAYOUT_DIRECTION_LTR) {
            view.translationX = 0f
            return
        }
        val first = android.graphics.Rect()
        view.paint.getTextBounds(text, 0, 1, first)
        view.translationX = -first.left.toFloat()
    }

    // Keeps the time and date current while the home screen shows, e.g. past midnight, and
    // follows the phone's 12- or 24-hour setting when it changes. It follows Android's
    // once-a-minute tick, so it adds no wake-ups.
    private var ticking = false
    private val ticker = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = updateClock()
    }

    fun startTicking() {
        if (ticking || (!prefs.showTime && !prefs.showDate)) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, ticker, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        ticking = true
    }

    fun stopTicking() {
        if (!ticking) return
        ticking = false
        runCatching { context.unregisterReceiver(ticker) }
    }

    // Gestures

    // A long press on a home app can turn into moving it; until the finger lifts, that comes first
    @SuppressLint("ClickableViewAccessibility")
    @Suppress("UNUSED_PARAMETER")
    private fun onTouch(view: View, event: MotionEvent): Boolean =
        reorder.onTouch(event) || gestures.onTouchEvent(event)

    /** Where the status bar (with any cutout) and the navigation bar end, so nothing hides behind them. */
    fun applyInsets(top: Int, bottom: Int) {
        if (top == topInset && bottom == bottomInset) return
        topInset = top
        bottomInset = bottom
        render()
    }

    /** Leaving the home screen mid-drag puts the apps back where they were. */
    fun cancelDrag() {
        reorder.cancel()
        dismissHomeMenu()
    }

    private fun dismissHomeMenu() {
        homeMenu?.dismiss()
        homeMenu = null
        homeMenuAnchor?.let { binding.home.removeView(it) }
        homeMenuAnchor = null
    }

    // Only an app that's there and showing moves: not one that's gone, paused or locked away
    private fun canMove(index: Int): Boolean =
        appViews.getOrNull(index)?.isVisible == true && shown.getOrNull(index)?.let { repo.find(it) } != null

    // [from] and [to] are rows on screen; the saved list can also have apps that aren't shown
    private fun move(from: Int, to: Int) {
        val apps = prefs.homeApps
        val moving = shown.getOrNull(from) ?: return
        val there = shown.getOrNull(to) ?: return
        prefs.moveHomeApp(apps.indexOf(moving), apps.indexOf(there))
        render()
    }

    private fun viewAt(event: MotionEvent): View? {
        overflowView?.let { if (it.isVisible && it.contains(event.rawX, event.rawY)) return it }
        appViews.forEach { if (it.isVisible && it.contains(event.rawX, event.rawY)) return it }
        if (binding.clock.isVisible && binding.clock.contains(event.rawX, event.rawY)) return binding.clock
        if (binding.date.isVisible && binding.date.contains(event.rawX, event.rawY)) return binding.date
        return null
    }

    private fun tap(view: View?) {
        when (view) {
            null -> Unit
            binding.clock -> openOrDefault(prefs.clockApp, DefaultApp.CLOCK, view)
            binding.date -> openOrDefault(prefs.calendarApp, DefaultApp.CALENDAR, view)
            overflowView -> openDrawer(null)
            else -> shown.getOrNull(appViews.indexOf(view))?.let { Apps.open(context, it, view) }
        }
    }

    private fun longPress(view: View?, event: MotionEvent? = null) {
        when (view) {
            null -> showHomeMenu(event)
            binding.clock -> openDrawer(Slot.Clock)
            binding.date -> openDrawer(Slot.Calendar)
            overflowView -> openDrawer(null)
            else -> homeAppMenu(view, appViews.indexOf(view))
        }
    }

    // A home app's menu: Change app and Remove, above the usual items. An app that's gone, or
    // locked away in Private Space, gets just those two, without showing its name.
    private fun homeAppMenu(view: View, index: Int) {
        val target = shown.getOrNull(index) ?: return
        val remove = {
            prefs.removeHomeApp(target)
            render()
        }
        val entry = repo.find(target)
        if (entry == null) {
            homeMenu?.dismiss()
            val popup = PopupMenu(context, view, Gravity.START)
            popup.menu.add(0, HOME_CHANGE, 0, R.string.change_app)
            popup.menu.add(0, HOME_REMOVE, 1, R.string.remove_from_home)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    HOME_CHANGE -> openDrawer(Slot.Home(target))
                    HOME_REMOVE -> remove()
                }
                true
            }
            popup.setOnDismissListener { if (homeMenu === popup) homeMenu = null }
            homeMenu = popup
            popup.show()
            return
        }
        menu.show(
            anchor = view,
            entry = entry,
            // Hiding is about the drawer: it's done there
            canHide = false,
            extras = listOf(
                AppMenu.Extra(R.string.change_app) { openDrawer(Slot.Home(target)) },
                AppMenu.Extra(R.string.remove_from_home, remove),
            ),
        )
    }

    /** A long press on the home screen's empty space: add an app, or settings, where you pressed. */
    private fun showHomeMenu(event: MotionEvent?) {
        dismissHomeMenu()
        val anchor = View(context)
        val params = FrameLayout.LayoutParams(1, 1)
        params.leftMargin = (event?.x?.toInt() ?: binding.home.width / 2)
            .coerceIn(0, (binding.home.width - 1).coerceAtLeast(0))
        params.topMargin = (event?.y?.toInt() ?: binding.home.height / 2)
            .coerceIn(0, (binding.home.height - 1).coerceAtLeast(0))
        binding.home.addView(anchor, params)
        val popup = PopupMenu(context, anchor, Gravity.START)
        if (prefs.homeApps.size < capacity) {
            popup.menu.add(0, HOME_ADD, 0, R.string.add_to_home)
        }
        popup.menu.add(0, HOME_SETTINGS, 1, R.string.settings)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                HOME_ADD -> openDrawer(Slot.AddHome)
                HOME_SETTINGS -> openSettings()
            }
            true
        }
        popup.setOnDismissListener {
            binding.home.removeView(anchor)
            if (homeMenuAnchor === anchor) homeMenuAnchor = null
            if (homeMenu === popup) homeMenu = null
        }
        homeMenuAnchor = anchor
        homeMenu = popup
        // The menu hangs from a one-pixel view where you pressed, which has to be laid out
        // before the menu can find it
        anchor.doOnLayout {
            if (homeMenu === popup && anchor.parent === binding.home) popup.show()
        }
    }

    private fun swipe(left: Boolean) {
        if (left) {
            if (!prefs.swipeLeftOn) return
            openOrDefault(prefs.swipeLeftApp, DefaultApp.CAMERA)
        } else {
            if (!prefs.swipeRightOn) return
            openOrDefault(prefs.swipeRightApp, DefaultApp.PHONE)
        }
    }

    // The app you chose, or Android's own: an app that's been uninstalled falls back to the
    // default rather than saying it's missing. One whose profile is away still asks to turn it on,
    // unless it's in a Private Space that's locked and hidden, which mustn't show it's there.
    private fun openOrDefault(target: AppTarget?, default: DefaultApp, from: View? = null) {
        val here = target != null && !repo.isConcealed(target) &&
            (repo.find(target) != null || repo.isProfileAway(target.userSerial))
        if (here) Apps.open(context, target!!, from) else Apps.openDefault(context, default)
    }

    private fun View.contains(x: Float, y: Float): Boolean {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return x >= location[0] && x < location[0] + width && y >= location[1] && y < location[1] + height
    }

    /**
     * The home screen's text isn't clickable itself (the gesture detector handles touch), so
     * TalkBack is told what each item does: double tap to open, double tap and hold to change.
     * Home apps can also be moved up and down from TalkBack's actions, as dragging can't be done.
     */
    private fun describeForTalkBack(
        view: View,
        openLabel: Int,
        changeLabel: Int,
        canBeMoved: Boolean = false,
        onOpen: (View) -> Unit,
    ) {
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.isClickable = true
                info.isLongClickable = true
                info.addAction(AccessibilityActionCompat(AccessibilityNodeInfoCompat.ACTION_CLICK, context.getString(openLabel)))
                info.addAction(AccessibilityActionCompat(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK, context.getString(changeLabel)))
                if (!canBeMoved) return
                val position = appViews.indexOf(host)
                if (!canMove(position)) return
                if (position > 0 && appViews[position - 1].isVisible) {
                    info.addAction(AccessibilityActionCompat(R.id.action_move_up, context.getString(R.string.move_up)))
                }
                if (appViews.getOrNull(position + 1)?.isVisible == true) {
                    info.addAction(AccessibilityActionCompat(R.id.action_move_down, context.getString(R.string.move_down)))
                }
            }

            override fun performAccessibilityAction(host: View, action: Int, args: android.os.Bundle?): Boolean = when (action) {
                AccessibilityNodeInfoCompat.ACTION_CLICK -> {
                    onOpen(host)
                    true
                }
                AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> {
                    longPress(host)
                    true
                }
                R.id.action_move_up -> moveForTalkBack(appViews.indexOf(host), -1)
                R.id.action_move_down -> moveForTalkBack(appViews.indexOf(host), 1)
                else -> super.performAccessibilityAction(host, action, args)
            }
        })
    }

    // Moves the app one spot, and TalkBack follows it there
    private fun moveForTalkBack(index: Int, step: Int): Boolean {
        val to = index + step
        if (index !in appViews.indices || to !in appViews.indices || !appViews[index].isVisible ||
            !appViews[to].isVisible || !canMove(index)) return false
        move(index, to)
        appViews[to].performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
        return true
    }

    private companion object {
        const val SWIPE_DISTANCE_DP = 40
        const val HOME_ADD = 1
        const val HOME_SETTINGS = 2
        const val HOME_CHANGE = 3
        const val HOME_REMOVE = 4
    }
}
