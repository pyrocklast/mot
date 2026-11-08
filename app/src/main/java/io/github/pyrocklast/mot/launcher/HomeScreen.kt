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
import io.github.pyrocklast.mot.util.Look
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
            // A home app: its menu, and dragging from here moves it to another spot
            if (MENU_WHILE_HOLDING) {
                if (homeAppMenu(view, view.tag as Int)) reorder.arm(index, e.rawY, lift = false)
            } else {
                if (canMove(index)) reorder.arm(index, e.rawY, lift = true) else homeAppMenu(view, view.tag as Int)
            }
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
        onRelease = { index -> if (!MENU_WHILE_HOLDING) appViews.getOrNull(index)?.let { homeAppMenu(it, it.tag as Int) } },
        onMove = ::move,
    )

    init {
        // Taps reach TalkBack users through the actions set up in describeForTalkBack
        binding.home.setOnTouchListener(::onTouch)
        // TalkBack and switch-access users cannot rely on full-screen swipe/long-press
        // gestures. Expose the two essential destinations without a visible home control.
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
        // The first render can precede layout. Recalculate when the actual viewport arrives
        // or changes size, without triggering another render on padding-only layout passes.
        binding.homeApps.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) render()
        }
        // The date can change height at midnight or after a locale/font adjustment, even
        // when the viewport stays the same size.
        binding.clockBlock.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) render()
        }
        binding.setDefault.setOnClickListener { requestHomeRole() }
        describeForTalkBack(binding.clock, R.string.action_open_clock, R.string.action_choose_clock) { tap(it) }
        describeForTalkBack(binding.date, R.string.action_open_calendar, R.string.action_choose_calendar) { tap(it) }
    }

    /** Draws the home screen from the current settings and app names. Cheap enough to call often. */
    fun render() {
        // The home has one deliberate composition; legacy alignment preferences remain stored
        // until the settings migration, but no longer control the home screen.
        val alignment = Gravity.START
        binding.homeApps.gravity = Gravity.TOP or alignment
        binding.clockBlock.gravity = alignment

        // The clock and date first: where the names start depends on how tall they come out.
        // With the status bar hidden, they move up into its space.
        val clockParams = binding.clockBlock.layoutParams as ViewGroup.MarginLayoutParams
        val clockTop = HomeFit.clockTop(context, topInset)
        if (clockParams.topMargin != clockTop) {
            clockParams.topMargin = clockTop
            binding.clockBlock.layoutParams = clockParams
        }
        binding.clock.isVisible = prefs.showTime
        binding.date.isVisible = prefs.showDate
        // The time and the date each have their own style
        binding.clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, HomeFit.timeSizePx(context))
        binding.clock.typeface = Look.typeface(Prefs.FONT_SANS, 300)
        binding.date.setTextSize(TypedValue.COMPLEX_UNIT_PX, HomeFit.dateSizePx(context))
        binding.date.typeface = Look.typeface(Prefs.FONT_SANS, 400)
        updateDate()

        // Keep every existing assignment, including ones stored beyond the old slot count.
        // A null slot is not an app and never occupies home-screen space.
        val assignments = prefs.homeApps.mapIndexedNotNull { slot, target ->
            target?.let { slot to it }
        }
        val count = assignments.size
        while (appViews.size < count) appViews += newAppView(appViews.size)
        while (appViews.size > count) binding.homeApps.removeView(appViews.removeAt(appViews.lastIndex))
        val nameTypeface = Look.typeface(Prefs.FONT_SANS, 300)
        val nameSizePx = HomeFit.appNameSizePx(context, prefs)
        appViews.forEachIndexed { index, view ->
            view.isVisible = true // Reconsider previously hidden rows after a size/configuration change.
            val (slot, target) = assignments[index]
            view.tag = slot
            view.text = repo.nameFor(target) ?: context.getString(
                if (repo.isLockedPrivateSpace(target.userSerial)) R.string.private_space_locked
                else R.string.app_not_found,
            )
            view.gravity = alignment or Gravity.CENTER_VERTICAL
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, nameSizePx)
            view.typeface = nameTypeface
            view.setPadding(view.paddingLeft, 0, view.paddingRight, 0)
        }

        // Onboarding is drawn over the home; reserve its bottom area so app rows cannot
        // overlap first-run instructions or the action to set mot as the default launcher.
        binding.tips.isVisible = !prefs.settingsOpened
        binding.setDefault.isVisible = !prefs.settingsOpened && !Apps.isDefaultLauncher(context)

        // Balance the app list between the information block and navigation/onboarding.
        val minimumTop = HomeFit.topPadding(context, prefs, topInset, heightOf(binding.clockBlock))
        val overlayClearance = listOf(binding.tips, binding.setDefault)
            .filter { it.isVisible }
            .maxOfOrNull {
                (it.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin +
                    heightOf(it) + (8 * context.resources.displayMetrics.density).toInt()
            } ?: 0
        val bottomPadding = maxOf(HomeFit.bottomPadding(context, bottomInset), overlayClearance)
        val displayHeight = if (binding.homeApps.height > 0) binding.homeApps.height else screenHeight()
        val room = (displayHeight - minimumTop - bottomPadding).coerceAtLeast(0)
        // Each app row is measured independently so every slot preserves its 48dp target,
        // including when fonts or view metrics differ.
        val textHeights = appViews.map { heightOf(it) }
        appViews.forEachIndexed { index, view ->
            val padding = HomeFit.namePadding(context, count, textHeights[index].toFloat(), room.toFloat())
            view.setPadding(view.paddingLeft, padding, view.paddingRight, padding)
        }
        // Keep the home fixed and gesture-driven. If all rows cannot fit, reserve a
        // final row that explicitly opens the drawer rather than silently losing apps.
        val rowHeights = appViews.map { heightOf(it) }
        val indicator = overflowView ?: (LayoutInflater.from(binding.homeApps.context)
            .inflate(R.layout.item_home_app, binding.homeApps, false) as TextView).also { view ->
            // Reuse themed launcher typography (wallpaper contrast + shadow), not default
            // Android TextView colours, which may be unreadable on the user's wallpaper.
            view.hint = null
            view.setPadding(view.paddingLeft, 0, view.paddingRight, 0)
            view.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            view.typeface = Look.typeface(Prefs.FONT_SANS, 400)
            view.minHeight = kotlin.math.ceil(48.0 * context.resources.displayMetrics.density).toInt()
            // Do not make this view touch-clickable: that would swallow swipe-up gestures.
            // The home detector handles taps; TalkBack gets an explicit open action.
            describeForTalkBack(
                view, R.string.home_all_apps_description, R.string.home_all_apps_description,
            ) { openDrawer(null) }
            overflowView = view
            binding.homeApps.addView(view)
        }
        // App count can grow after the indicator was first created.
        indicator.bringToFront()
        indicator.setText(R.string.home_all_apps)
        // Avoid showing and re-measuring the shortcut on ordinary homes that already fit.
        indicator.isVisible = rowHeights.sumOf { it.toLong() } > room.toLong()
        val indicatorHeight = if (indicator.isVisible) heightOf(indicator) else 0
        val layout = HomeFit.visibleRows(rowHeights, room, indicatorHeight)
        appViews.forEachIndexed { index, view -> view.isVisible = index < layout.count }
        indicator.isVisible = layout.showAllApps
        val groupHeight = rowHeights.take(layout.count).sum() +
            if (layout.showAllApps) indicatorHeight else 0
        val balancedTop = HomeFit.balancedTop(
            displayHeight, topInset, bottomPadding, minimumTop, groupHeight,
        )
        binding.homeApps.setPadding(
            binding.homeApps.paddingLeft, balancedTop,
            binding.homeApps.paddingRight, bottomPadding,
        )


    }

    // How tall a view comes out at its current text, size and font
    private fun heightOf(view: View): Int {
        // Measure with the real content width: UNSPECIFIED width may undercount height
        // when text (for example the date) needs to fit a narrow screen.
        val screenWidth = binding.homeApps.width.takeIf { it > 0 }
            ?: binding.home.width.takeIf { it > 0 }
            ?: context.resources.displayMetrics.widthPixels
        val horizontal = if (view === binding.clockBlock || view === binding.tips ||
            view === binding.setDefault) {
            val margins = view.layoutParams as? ViewGroup.MarginLayoutParams
            (margins?.leftMargin ?: 0) + (margins?.rightMargin ?: 0)
        } else {
            binding.homeApps.paddingLeft + binding.homeApps.paddingRight
        }
        val width = (screenWidth - horizontal).coerceAtLeast(1)
        val atMost = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST)
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(atMost, unspecified)
        return view.measuredHeight
    }

    private fun screenHeight(): Int = context.resources.displayMetrics.heightPixels

    private fun newAppView(index: Int): TextView {
        val view = LayoutInflater.from(binding.homeApps.context)
            .inflate(R.layout.item_home_app, binding.homeApps, false) as TextView
        binding.homeApps.addView(view)
        describeForTalkBack(view, R.string.action_open, R.string.action_more_options, canBeMoved = true) { tap(it) }
        view.tag = index
        return view
    }

    // Date remains independent of the status bar; no simulated battery/status indicators.
    private fun updateDate() {
        if (!prefs.showDate) return
        val text = DateText.date(context)
        if (binding.date.text.toString() != text) binding.date.text = text
    }

    // Keeps the date current while the home screen shows, e.g. past midnight.
    // It follows Android's once-a-minute tick, so it adds no wake-ups.
    private var ticking = false
    private val ticker = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = updateDate()
    }

    fun startTicking() {
        if (ticking || !prefs.showDate) return
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

    // Only a visible installed app moves, not an away/private profile or clipped row.
    private fun canMove(index: Int): Boolean = appViews.getOrNull(index)?.let { view ->
        view.isVisible && (view.tag as? Int)?.let { slot ->
            prefs.homeApp(slot)?.let { repo.find(it) }
        } != null
    } ?: false

    private fun move(from: Int, to: Int) {
        val fromSlot = appViews.getOrNull(from)?.tag as? Int ?: return
        val toSlot = appViews.getOrNull(to)?.tag as? Int ?: return
        prefs.moveHomeAppBySlot(fromSlot, toSlot)
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
            else -> {
                val target = prefs.homeApp(view.tag as Int)
                if (target == null) Apps.toast(context, R.string.long_press_to_choose)
                else Apps.open(context, target, view)
            }
        }
    }

    private fun longPress(view: View?, event: MotionEvent? = null) {
        when (view) {
            null -> showHomeMenu(event)
            binding.clock -> openDrawer(Slot.Clock)
            binding.date -> openDrawer(Slot.Calendar)
            overflowView -> openDrawer(null)
            else -> homeAppMenu(view, view.tag as Int)
        }
    }

    // The app menu offers replace/remove. Unavailable or locked apps get the same management
    // actions without displaying their identity or requiring an installed AppEntry.
    private fun homeAppMenu(view: View, index: Int): Boolean {
        val entry = prefs.homeApp(index)?.let { repo.find(it) }
        if (entry == null) {
            homeMenu?.dismiss()
            val popup = PopupMenu(context, view, Gravity.START)
            popup.menu.add(0, HOME_CHANGE, 0, R.string.change_app)
            popup.menu.add(0, HOME_REMOVE, 1, R.string.remove_from_home)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    HOME_CHANGE -> openDrawer(Slot.Home(index))
                    HOME_REMOVE -> {
                        prefs.setHomeApp(index, null)
                        render()
                    }
                }
                true
            }
            popup.setOnDismissListener { if (homeMenu === popup) homeMenu = null }
            homeMenu = popup
            popup.show()
            return false
        }
        menu.show(
            anchor = view,
            entry = entry,
            alignment = Gravity.START,
            // Hiding is about the drawer: it's done there
            canHide = false,
            extras = listOf(
                AppMenu.Extra(R.string.change_app) { openDrawer(Slot.Home(index)) },
                AppMenu.Extra(R.string.remove_from_home) {
                    prefs.setHomeApp(index, null)
                    render()
                },
            ),
        )
        return true
    }

    /** Native contextual actions positioned at the long press, without a permanent home control. */
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
        if (prefs.assignedHomeApps.size < Prefs.MAX_HOME_APPS) {
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
        // The one-pixel anchor must actually be laid out before Android positions its
        // popup. A posted runnable alone does not guarantee that its coordinates exist.
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
    // default rather than saying it's missing. One whose profile is away still asks to turn it on.
    private fun openOrDefault(target: AppTarget?, default: DefaultApp, from: View? = null) {
        val here = target != null && (repo.find(target) != null || repo.isProfileAway(target.userSerial))
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

        /**
         * When a long press on a home app shows its menu: straight away (true), or once the finger
         * lifts without dragging (false), so the menu never covers the spots an app is dragged to.
         */
        const val MENU_WHILE_HOLDING = false
    }
}
