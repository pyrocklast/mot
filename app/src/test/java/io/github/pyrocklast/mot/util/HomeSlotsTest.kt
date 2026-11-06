package io.github.pyrocklast.mot.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeSlotsTest {
    @Test fun fillsVisibleBlankSlotBeforeExtending() {
        assertEquals(1, HomeSlots.nextAddSlot(listOf("phone", null, "camera", null), 3, "maps"))
    }

    @Test fun extendsIntoAnEmptyDormantSlot() {
        assertEquals(2, HomeSlots.nextAddSlot(listOf("phone", "camera", null), 2, "maps"))
    }

    @Test fun neverOverwritesDormantAssignment() {
        assertNull(HomeSlots.nextAddSlot(listOf("phone", "camera", "dormant"), 2, "maps"))
    }

    @Test fun doesNotDuplicateAlreadyVisibleTarget() {
        assertNull(HomeSlots.nextAddSlot(listOf("phone", null, "camera"), 2, "phone"))
    }

    @Test fun supportsEmptyHome() {
        assertEquals(0, HomeSlots.nextAddSlot(listOf(null, null), 0, "phone"))
    }

    @Test fun fullHomeReturnsNoSlot() {
        assertNull(HomeSlots.nextAddSlot(listOf("a", "b"), 2, "c"))
    }
}
