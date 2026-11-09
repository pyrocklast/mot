package io.github.pyrocklast.mot.launcher

import io.github.pyrocklast.mot.data.AppTarget
import io.github.pyrocklast.mot.data.Prefs

/** A place you choose an app for: a home app, a new one, a swipe, or what tapping the time or date opens. */
sealed interface Slot {
    /** The home app at [index], top to bottom. */
    data class Home(val index: Int) : Slot
    /** A new home app, added at the end. */
    data object AddHome : Slot
    data object Clock : Slot
    data object Calendar : Slot
    data object SwipeLeft : Slot
    data object SwipeRight : Slot

    fun save(prefs: Prefs, target: AppTarget?) = when (this) {
        is Home -> prefs.setHomeApp(index, target)
        AddHome -> { if (target != null) prefs.addHomeApp(target); Unit }
        Clock -> prefs.clockApp = target
        Calendar -> prefs.calendarApp = target
        SwipeLeft -> prefs.swipeLeftApp = target
        SwipeRight -> prefs.swipeRightApp = target
    }

    /** For passing a slot to the settings screen's app picker. */
    fun encode(): String = when (this) {
        is Home -> "home:$index"
        AddHome -> "home:add"
        Clock -> "clock"
        Calendar -> "calendar"
        SwipeLeft -> "swipe_left"
        SwipeRight -> "swipe_right"
    }

    companion object {
        fun decode(value: String?): Slot? = when {
            value == null -> null
            value == "home:add" -> AddHome
            value.startsWith("home:") -> value.removePrefix("home:").toIntOrNull()?.let { Home(it) }
            value == "clock" -> Clock
            value == "calendar" -> Calendar
            value == "swipe_left" -> SwipeLeft
            value == "swipe_right" -> SwipeRight
            else -> null
        }
    }
}
