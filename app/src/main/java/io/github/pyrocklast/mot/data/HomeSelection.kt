package io.github.pyrocklast.mot.data

/**
 * The home is a list of assigned apps, not a fixed number of blank slots.
 * These pure operations preserve the saved order, including legacy gaps between slots.
 */
internal object HomeSelection {
    fun <T : Any> assigned(slots: List<T?>): List<T> = slots.filterNotNull()

    fun <T : Any> append(slots: List<T?>, target: T, maximum: Int): List<T>? {
        val items = assigned(slots)
        if (items.size >= maximum || target in items) return null
        return items + target
    }

    /** Move by original saved slot, not view position, so legacy gaps never move the wrong app. */
    fun <T : Any> moveBySlot(slots: List<T?>, fromSlot: Int, toSlot: Int): List<T>? {
        val storedSlots = slots.indices.filter { slots[it] != null }
        return move(slots, storedSlots.indexOf(fromSlot), storedSlots.indexOf(toSlot))
    }

    fun <T : Any> move(slots: List<T?>, from: Int, to: Int): List<T>? {
        val items = assigned(slots).toMutableList()
        if (from !in items.indices || to !in items.indices || from == to) return null
        items.add(to, items.removeAt(from))
        return items
    }
}
