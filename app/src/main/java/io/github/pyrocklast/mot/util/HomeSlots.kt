package io.github.pyrocklast.mot.util

/**
 * Pure home layout migration and insertion policy. Null legacy slots become no rows.
 * Dormant assignments are archived, never silently made visible.
 */
object HomeSlots {
    internal data class Compacted<T : Any>(
        val active: List<T>,
        val dormant: List<Pair<Int, T>>,
    )

    /** Preserve the visible order and the original indices of dormant assignments. */
    internal fun <T : Any> compact(slots: List<T?>, visibleCount: Int): Compacted<T> {
        val count = visibleCount.coerceIn(0, slots.size)
        return Compacted(
            active = slots.take(count).filterNotNull(),
            dormant = slots.withIndex().drop(count).mapNotNull { (i, target) ->
                target?.let { i to it }
            },
        )
    }

    /**
     * Compact lists have no empty slots. A duplicate is never added through the drawer,
     * and a full list stays unchanged. Archive contents never affect insertion.
     */
    internal fun <T : Any> nextAddSlot(slots: List<T?>, activeCount: Int, target: T): Int? {
        val count = activeCount.coerceIn(0, slots.size)
        if (slots.take(count).contains(target)) return null
        return count.takeIf { it < slots.size }
    }

    /** One-place removal, with the later rows shifted towards the top. */
    internal fun <T : Any> removeAt(active: List<T>, index: Int): List<T> =
        if (index in active.indices) active.toMutableList().also { it.removeAt(index) }
        else active
}
