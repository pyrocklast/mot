package io.github.pyrocklast.mot.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeSlotsTest {
    @Test fun legacyBlankRowsDisappearWithoutReorderingAssignedApps() {
        val result = HomeSlots.compact(listOf("phone", null, "camera", null, null), 5)
        assertEquals(listOf("phone", "camera"), result.active)
        assertEquals(emptyList<Pair<Int, String>>(), result.dormant)
    }

    @Test fun dormantAssignmentsKeepOriginalPositionsAndNeverAppearAutomatically() {
        val result = HomeSlots.compact(listOf("phone", null, "camera", null, "hidden"), 3)
        assertEquals(listOf("phone", "camera"), result.active)
        assertEquals(listOf(4 to "hidden"), result.dormant)
    }

    @Test fun zeroVisibleSlotsNeverPromoteDormantApps() {
        val result = HomeSlots.compact(listOf("secret", "camera"), 0)
        assertEquals(emptyList<String>(), result.active)
        assertEquals(listOf(0 to "secret", 1 to "camera"), result.dormant)
    }

    @Test fun legacyDuplicateAssignmentsArePreserved() {
        val result = HomeSlots.compact(listOf("camera", null, "camera"), 3)
        assertEquals(listOf("camera", "camera"), result.active)
    }

    @Test fun drawerAppendsOnlyToCompactHome() {
        assertEquals(2, HomeSlots.nextAddSlot(listOf("phone", "camera", null), 2, "maps"))
    }

    @Test fun drawerDoesNotDuplicateExistingApp() {
        assertNull(HomeSlots.nextAddSlot(listOf("phone", "camera", null), 2, "phone"))
    }

    @Test fun drawerCanFillEmptyHomeWithoutRestoringArchive() {
        assertEquals(0, HomeSlots.nextAddSlot(listOf(null, null), 0, "phone"))
    }

    @Test fun fullHomeRejectsNewApps() {
        assertNull(HomeSlots.nextAddSlot(listOf("a", "b"), 2, "c"))
    }

    @Test fun removalClosesGapWithoutAffectingOthers() {
        assertEquals(listOf("a", "c"), HomeSlots.removeAt(listOf("a", "b", "c"), 1))
        assertEquals(listOf("a", "b"), HomeSlots.removeAt(listOf("a", "b"), 5))
    }

    @Test fun explicitRecoveryIsCapacityLimitedAndPreservesRemainder() {
        val restored = HomeSlots.restore(
            listOf("a", "b"), listOf(5 to "secret", 7 to "camera"), 3,
        )
        assertEquals(listOf("a", "b", "secret"), restored.active)
        assertEquals(listOf(5), restored.restoredIndices)
        assertEquals(listOf(7 to "camera"), restored.remainingDormant)
    }

    @Test fun fullHomeDoesNotConsumeArchivedAssignments() {
        val restored = HomeSlots.restore(listOf("a", "b"), listOf(5 to "secret"), 2)
        assertEquals(emptyList<Int>(), restored.restoredIndices)
        assertEquals(listOf(5 to "secret"), restored.remainingDormant)
    }
}
