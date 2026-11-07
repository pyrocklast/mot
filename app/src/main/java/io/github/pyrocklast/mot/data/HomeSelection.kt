package io.github.pyrocklast.mot.data

/**
 * The home is a list of assigned apps, not a fixed number of blank slots.
 * These pure operations preserve the saved order, including legacy gaps between slots.
 */
internal object HomeSelection {
    fun <T : Any> assigned(slots: List<T?>): List<T> = slots.filterNotNull()

    internal data class LegacySplit<T : Any>(
        val visible: List<T>,
        val archived: List<Pair<Int, T>>,
    )

    /**
     * The old slider made only slots below [visibleCount] visible. Never promote assignments
     * outside that range automatically: they may have been deliberately kept out of sight.
     */
    internal fun <T : Any> splitLegacy(slots: List<T?>, visibleCount: Int): LegacySplit<T> {
        val boundary = visibleCount.coerceIn(0, slots.size)
        return LegacySplit(
            visible = slots.take(boundary).filterNotNull(),
            archived = slots.withIndex().drop(boundary).mapNotNull { (index, target) ->
                target?.let { index to it }
            },
        )
    }

    internal data class Recovery<T : Any>(
        val visible: List<T>,
        val restoredIndices: List<Int>,
    )

    /** Explicit restoration never overwrites current apps and keeps unrecovered archives. */
    internal fun <T : Any> recover(
        visible: List<T>, archives: List<Pair<Int, T>>, maximum: Int,
    ): Recovery<T> {
        val restored = archives.take((maximum - visible.size).coerceAtLeast(0))
        return Recovery(
            visible = visible + restored.map { it.second },
            restoredIndices = restored.map { it.first },
        )
    }

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
