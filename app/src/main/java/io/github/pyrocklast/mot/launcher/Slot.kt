package io.github.pyrocklast.mot.launcher

import io.github.pyrocklast.mot.data.AppTarget
import io.github.pyrocklast.mot.data.Prefs

/** A place you choose an app for: a home spot, a swipe, or what tapping the time or date opens. */
sealed interface Slot {
    data class Home(val index: Int) : Slot
    data object AddHome : Slot
    data object Clock : Slot
    data object Calendar : Slot
    data object SwipeLeft : Slot
    data object SwipeRight : Slot

    /** False only when adding a favorite is impossible; other actions always succeed. */
    fun save(prefs: Prefs, target: AppTarget?): Boolean = when (this) {
        is Home -> { prefs.setHomeApp(index, target); true }
        AddHome -> target != null && prefs.appendHomeApp(target)
        Clock -> { prefs.clockApp = target; true }
        Calendar -> { prefs.calendarApp = target; true }
        SwipeLeft -> { prefs.swipeLeftApp = target; true }
        SwipeRight -> { prefs.swipeRightApp = target; true }
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
