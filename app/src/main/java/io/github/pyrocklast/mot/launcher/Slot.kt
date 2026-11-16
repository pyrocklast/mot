package io.github.pyrocklast.mot.launcher

import io.github.pyrocklast.mot.data.AppTarget
import io.github.pyrocklast.mot.data.Prefs

/** A place you choose an app for: a home app, a swipe, or what tapping the time or date opens. */
sealed interface Slot {
    /** The home app [current], to be changed for another in the same place. */
    data class Home(val current: AppTarget) : Slot
    data object Clock : Slot
    data object Calendar : Slot
    data object SwipeLeft : Slot
    data object SwipeRight : Slot

    fun save(prefs: Prefs, target: AppTarget?) = when (this) {
        is Home -> prefs.replaceHomeApp(current, target)
        Clock -> prefs.clockApp = target
        Calendar -> prefs.calendarApp = target
        SwipeLeft -> prefs.swipeLeftApp = target
        SwipeRight -> prefs.swipeRightApp = target
    }

    /** For passing a slot to the settings screen's app picker. */
    fun encode(): String = when (this) {
        is Home -> "home:" + current.encode()
        Clock -> "clock"
        Calendar -> "calendar"
        SwipeLeft -> "swipe_left"
        SwipeRight -> "swipe_right"
    }

    companion object {
        fun decode(value: String?): Slot? = when {
            value == null -> null
            value.startsWith("home:") -> AppTarget.decode(value.removePrefix("home:"))?.let { Home(it) }
            value == "clock" -> Clock
            value == "calendar" -> Calendar
            value == "swipe_left" -> SwipeLeft
            value == "swipe_right" -> SwipeRight
            else -> null
        }
    }
}
