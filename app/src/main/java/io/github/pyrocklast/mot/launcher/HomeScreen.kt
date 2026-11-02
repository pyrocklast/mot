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
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isVisible
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.AppRepository
import io.github.pyrocklast.mot.data.Prefs
import io.github.pyrocklast.mot.databinding.ActivityLauncherBinding
import io.github.pyrocklast.mot.util.Apps
import io.github.pyrocklast.mot.util.DateText
import io.github.pyrocklast.mot.util.DefaultApp
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
    private val config = ViewConfiguration.get(context)
    private val minSwipeDistance = SWIPE_DISTANCE_DP * context.resources.displayMetrics.density
    private val appNameSize = context.resources.getDimension(R.dimen.app_name_size)
    private val clockSize = context.resources.getDimension(R.dimen.clock_size)
    private val dateSize = context.resources.getDimension(R.dimen.date_size)

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            tap(viewAt(e))
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            binding.home.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            longPress(viewAt(e))
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

    init {
        // Taps reach TalkBack users through the actions set up in describeForTalkBack
        binding.home.setOnTouchListener(::onTouch)
        binding.setDefault.setOnClickListener { requestHomeRole() }
        describeForTalkBack(binding.clock, R.string.action_open_clock, R.string.action_choose_clock) { tap(it) }
        describeForTalkBack(binding.date, R.string.action_open_calendar, R.string.action_choose_calendar) { tap(it) }
    }

    /** Draws the home screen from the current settings and app names. Cheap enough to call often. */
    fun render() {
        val alignment = prefs.homeAlignment
        binding.homeApps.gravity = alignment or (if (prefs.homeAtBottom) Gravity.BOTTOM else Gravity.CENTER_VERTICAL)
        binding.clockBlock.gravity = alignment

        val count = prefs.homeAppCount
        while (appViews.size < count) appViews += newAppView(appViews.size)
        while (appViews.size > count) binding.homeApps.removeView(appViews.removeAt(appViews.lastIndex))
        val nameTypeface = Look.typeface(prefs.nameFont, prefs.nameWeight)
        appViews.forEachIndexed { index, view ->
            val target = prefs.homeApp(index)
            view.text = target?.let { repo.nameFor(it) }.orEmpty()
            view.gravity = alignment
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, appNameSize * prefs.appNameScale)
            view.typeface = nameTypeface
        }

        binding.clock.isVisible = prefs.showTime
        binding.date.isVisible = prefs.showDate
        binding.clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, clockSize * prefs.clockScale)
        binding.date.setTextSize(TypedValue.COMPLEX_UNIT_PX, dateSize * prefs.clockScale)
        val clockTypeface = Look.typeface(prefs.clockFont, prefs.clockWeight)
        binding.clock.typeface = clockTypeface
        binding.date.typeface = clockTypeface
        updateDate()

        // First run: how to reach the drawer and settings, and making mot launcher the Home app.
        // Afterwards settings has a row for that.
        binding.tips.isVisible = !prefs.settingsOpened
        binding.setDefault.isVisible = !prefs.settingsOpened && !Apps.isDefaultLauncher(context)
    }

    private fun newAppView(index: Int): TextView {
        val view = LayoutInflater.from(binding.homeApps.context)
            .inflate(R.layout.item_home_app, binding.homeApps, false) as TextView
        binding.homeApps.addView(view)
        describeForTalkBack(view, R.string.action_open, R.string.action_more_options) { tap(it) }
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

    @SuppressLint("ClickableViewAccessibility")
    @Suppress("UNUSED_PARAMETER")
    private fun onTouch(view: View, event: MotionEvent): Boolean = gestures.onTouchEvent(event)

    private fun viewAt(event: MotionEvent): View? {
        val candidates = appViews + listOf(binding.clock, binding.date)
        return candidates.firstOrNull { it.isVisible && it.contains(event.rawX, event.rawY) }
    }

    private fun tap(view: View?) {
        when (view) {
            null -> Unit
            binding.clock -> prefs.clockApp?.let { Apps.open(context, it, view) } ?: Apps.openDefault(context, DefaultApp.CLOCK)
            binding.date -> prefs.calendarApp?.let { Apps.open(context, it, view) } ?: Apps.openDefault(context, DefaultApp.CALENDAR)
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
            else -> homeAppMenu(view, view.tag as Int)
        }
    }

    // The drawer's menu for the app, with ways to change this spot on top. An empty spot (or one
    // whose app is gone) goes straight to choosing an app.
    private fun homeAppMenu(view: View, index: Int) {
        val entry = prefs.homeApp(index)?.let { repo.find(it) }
        if (entry == null) {
            openDrawer(Slot.Home(index))
            return
        }
        menu.show(
            anchor = view,
            entry = entry,
            alignment = prefs.homeAlignment,
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
    }

    private fun swipe(left: Boolean) {
        if (left) {
            if (!prefs.swipeLeftOn) return
            prefs.swipeLeftApp?.let { Apps.open(context, it) } ?: Apps.openDefault(context, DefaultApp.CAMERA)
        } else {
            if (!prefs.swipeRightOn) return
            prefs.swipeRightApp?.let { Apps.open(context, it) } ?: Apps.openDefault(context, DefaultApp.PHONE)
        }
    }

    private fun View.contains(x: Float, y: Float): Boolean {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return x >= location[0] && x < location[0] + width && y >= location[1] && y < location[1] + height
    }

    /**
     * The home screen's text isn't clickable itself (the gesture detector handles touch), so
     * TalkBack is told what each item does: double tap to open, double tap and hold to change.
     */
    private fun describeForTalkBack(view: View, openLabel: Int, changeLabel: Int, onOpen: (View) -> Unit) {
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.isClickable = true
                info.isLongClickable = true
                info.addAction(AccessibilityActionCompat(AccessibilityNodeInfoCompat.ACTION_CLICK, context.getString(openLabel)))
                info.addAction(AccessibilityActionCompat(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK, context.getString(changeLabel)))
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
                else -> super.performAccessibilityAction(host, action, args)
            }
        })
    }

    private companion object {
        const val SWIPE_DISTANCE_DP = 40
    }
}
