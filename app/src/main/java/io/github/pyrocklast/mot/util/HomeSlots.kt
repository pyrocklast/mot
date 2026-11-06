package io.github.pyrocklast.mot.util

/**
 * A cautious insertion policy for legacy fixed home slots. Reuse an empty visible slot first;
 * otherwise extend the active list only into a truly empty stored slot. Never erase a dormant
 * assignment left behind when the user previously reduced their visible slot count.
 */
object HomeSlots {
    internal fun <T : Any> nextAddSlot(slots: List<T?>, activeCount: Int, target: T): Int? {
        val count = activeCount.coerceIn(0, slots.size)
        if (slots.take(count).contains(target)) return null
        for (index in 0 until count) {
            if (slots[index] == null) return index
        }
        return count.takeIf { it < slots.size && slots[it] == null }
    }
}
