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
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
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

    // Where the system bars are, until Android says otherwise: the home screen draws under them
    private var topInset = (24 * context.resources.displayMetrics.density).toInt()
    private var bottomInset = (40 * context.resources.displayMetrics.density).toInt()
    private val config = ViewConfiguration.get(context)
    private val minSwipeDistance = SWIPE_DISTANCE_DP * context.resources.displayMetrics.density
    private val clockSize = sp(42f)
    private val dateSize = sp(14f)
    private val homeNameSize = sp(20f)

    private fun sp(value: Float): Float = android.util.TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, value, context.resources.displayMetrics,
    )

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
            if (view == null || index < 0) return longPress(view)
            // A home app: its menu, and dragging from here moves it to another spot
            if (MENU_WHILE_HOLDING) {
                if (homeAppMenu(view, index)) reorder.arm(index, e.rawY, lift = false)
            } else {
                if (canMove(index)) reorder.arm(index, e.rawY, lift = true) else homeAppMenu(view, index)
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
        onRelease = { index -> if (!MENU_WHILE_HOLDING) appViews.getOrNull(index)?.let { homeAppMenu(it, index) } },
        onMove = ::move,
    )

    init {
        // Taps reach TalkBack users through the actions set up in describeForTalkBack
        binding.home.setOnTouchListener(::onTouch)
        // The first render can precede layout. Recalculate when the actual viewport arrives
        // or changes size, without triggering another render on padding-only layout passes.
        binding.homeApps.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) render()
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
        (binding.clockBlock.layoutParams as ViewGroup.MarginLayoutParams).topMargin = HomeFit.clockTop(context, topInset)
        binding.clock.isVisible = prefs.showTime
        binding.date.isVisible = prefs.showDate
        // The time and the date each have their own style
        binding.clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, clockSize)
        binding.clock.typeface = Look.typeface(Prefs.FONT_SANS, 300)
        binding.date.setTextSize(TypedValue.COMPLEX_UNIT_PX, dateSize)
        binding.date.typeface = Look.typeface(Prefs.FONT_SANS, 400)
        updateDate()

        val count = prefs.homeAppCount
        while (appViews.size < count) appViews += newAppView(appViews.size)
        while (appViews.size > count) binding.homeApps.removeView(appViews.removeAt(appViews.lastIndex))
        val nameTypeface = Look.typeface(Prefs.FONT_SANS, 400)
        appViews.forEachIndexed { index, view ->
            view.isVisible = true // Reconsider previously hidden rows after a size/configuration change.
            val target = prefs.homeApp(index)
            view.text = target?.let { repo.nameFor(it) }.orEmpty()
            view.gravity = alignment or Gravity.CENTER_VERTICAL
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, homeNameSize)
            view.typeface = nameTypeface
            view.setPadding(view.paddingLeft, 0, view.paddingRight, 0)
        }

        // Measure before positioning: the group is optically balanced around the usable
        // display midpoint, but cannot collide with the clock/date or system navigation.
        val minimumTop = HomeFit.topPadding(context, prefs, topInset, heightOf(binding.clockBlock))
        val bottomPadding = HomeFit.bottomPadding(context, bottomInset)
        val displayHeight = if (binding.homeApps.height > 0) binding.homeApps.height else screenHeight()
        val room = (displayHeight - minimumTop - bottomPadding).coerceAtLeast(0)
        // A long app name may wrap while its neighbours remain on one line. Each row must
        // independently preserve its 48dp target rather than copying the first row's padding.
        val textHeights = appViews.map { heightOf(it) }
        appViews.forEachIndexed { index, view ->
            val padding = HomeFit.namePadding(context, count, textHeights[index].toFloat(), room.toFloat())
            view.setPadding(view.paddingLeft, padding, view.paddingRight, padding)
        }
        // Keep the home fixed and gesture-driven. If all rows cannot fit, reserve a
        // final row that explicitly opens the drawer rather than silently losing apps.
        val rowHeights = appViews.map { heightOf(it) }
        val indicator = overflowView ?: TextView(context).also { view ->
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


        // First run: how to reach the drawer and settings, and making mot launcher the Home app.
        // Afterwards settings has a row for that.
        binding.tips.isVisible = !prefs.settingsOpened
        binding.setDefault.isVisible = !prefs.settingsOpened && !Apps.isDefaultLauncher(context)
    }

    // How tall a view comes out at its current text, size and font
    private fun heightOf(view: View): Int {
        // Measure with the real content width: UNSPECIFIED width incorrectly treats long app
        // names as one line, and underestimates their height on narrow screens.
        val screenWidth = binding.homeApps.width.takeIf { it > 0 }
            ?: binding.home.width.takeIf { it > 0 }
            ?: context.resources.displayMetrics.widthPixels
        val horizontal = if (view === binding.clockBlock) {
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

    // The date, and the battery level after it while the status bar is hidden
    private fun updateDate() {
        if (!prefs.showDate) return
        var text = DateText.date(context, prefs.dateStyle)
        if (!prefs.showStatusBar) DateText.battery(context)?.let { text = "$text, $it" }
        if (binding.date.text.toString() != text) binding.date.text = text
    }

    // Keeps the date and battery level current while the home screen shows, e.g. past midnight
    // or while charging. It follows the clock's once-a-minute tick, so it adds no wake-ups.
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
    fun cancelDrag() = reorder.cancel()

    // Only a spot with an app in it moves (not an empty one, or one whose app is gone or locked away)
    private fun canMove(index: Int): Boolean = prefs.homeApp(index)?.let { repo.find(it) } != null

    private fun move(from: Int, to: Int) {
        prefs.moveHomeApp(from, to, prefs.homeAppCount)
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
                // A blank spot is empty, its app is gone, or it's in a locked Private Space: that
                // one asks to unlock it, like any app whose profile is away
                val target = prefs.homeApp(view.tag as Int)
                val blank = (view as TextView).text.isEmpty()
                if (target == null || (blank && !repo.isProfileAway(target.userSerial))) Apps.toast(context, R.string.long_press_to_choose)
                else Apps.open(context, target, view)
            }
        }
    }

    private fun longPress(view: View?) {
        when (view) {
            null -> openSettings()
            binding.clock -> openDrawer(Slot.Clock)
            binding.date -> openDrawer(Slot.Calendar)
            overflowView -> openDrawer(null)
            else -> homeAppMenu(view, view.tag as Int)
        }
    }

    // The drawer's menu for the app, with ways to change this spot on top. An empty spot (or one
    // whose app is gone) goes straight to choosing an app. Returns whether the menu opened.
    private fun homeAppMenu(view: View, index: Int): Boolean {
        val entry = prefs.homeApp(index)?.let { repo.find(it) }
        if (entry == null) {
            openDrawer(Slot.Home(index))
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
                val index = host.tag as? Int ?: return
                if (!canBeMoved || !canMove(index)) return
                if (index > 0) info.addAction(AccessibilityActionCompat(R.id.action_move_up, context.getString(R.string.move_up)))
                if (appViews.getOrNull(index + 1)?.isVisible == true) {
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
                R.id.action_move_up -> moveForTalkBack(host.tag as Int, -1)
                R.id.action_move_down -> moveForTalkBack(host.tag as Int, 1)
                else -> super.performAccessibilityAction(host, action, args)
            }
        })
    }

    // Moves the app one spot, and TalkBack follows it there
    private fun moveForTalkBack(index: Int, step: Int): Boolean {
        val to = index + step
        if (to !in appViews.indices || !appViews[index].isVisible ||
            !appViews[to].isVisible || !canMove(index)) return false
        move(index, to)
        appViews[to].performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
        return true
    }

    private companion object {
        const val SWIPE_DISTANCE_DP = 40

        /**
         * When a long press on a home app shows its menu: straight away (true), or once the finger
         * lifts without dragging (false), so the menu never covers the spots an app is dragged to.
         */
        const val MENU_WHILE_HOLDING = false
    }
}
