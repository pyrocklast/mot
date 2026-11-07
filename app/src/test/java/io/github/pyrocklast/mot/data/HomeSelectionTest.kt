package io.github.pyrocklast.mot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeSelectionTest {
    @Test fun leavesOnlyAssignedAppsVisibleInTheirSavedOrder() {
        assertEquals(listOf("Aegis", "Balatro", "Camera"),
            HomeSelection.assigned(listOf(null, "Aegis", null, "Balatro", "Camera", null)))
    }

    @Test fun addsAtTheEndAfterLegacyGapsWithoutDeletingSelections() {
        val old = listOf<String?>(null, "Aegis", null, "Balatro", null)
        val snapshot = old.toList()
        assertEquals(listOf("Aegis", "Balatro", "Camera"), HomeSelection.append(old, "Camera", 10))
        // Reading a list never rewrites the user's saved preferences.
        assertEquals(snapshot, old)
    }

    @Test fun addingDoesNotAllowDuplicatesOrMoreThanTenApps() {
        assertNull(HomeSelection.append(listOf("Aegis", "Balatro"), "Aegis", 10))
        assertNull(HomeSelection.append((1..10).map { it.toString() }, "11", 10))
        assertEquals(10, HomeSelection.append((1..9).map { it.toString() }, "10", 10)?.size)
    }

    @Test fun reorderWorksAcrossPreviouslyEmptySlots() {
        val gaps = listOf<String?>("A", null, "B", null, "C")
        assertEquals(listOf("C", "A", "B"), HomeSelection.move(gaps, 2, 0))
        assertEquals(listOf("B", "C", "A"), HomeSelection.move(gaps, 0, 2))
    }

    @Test fun movingByStorageSlotPreservesLegacyHolesAndVisibleOrder() {
        val slots = listOf<String?>("A", null, "B", null, "C")
        assertEquals(listOf("C", "A", "B"), HomeSelection.moveBySlot(slots, 4, 0))
        assertEquals(listOf("B", "C", "A"), HomeSelection.moveBySlot(slots, 0, 4))
        assertNull(HomeSelection.moveBySlot(slots, 1, 2))
        assertNull(HomeSelection.moveBySlot(slots, 0, 3))
    }

    @Test fun invalidMovesHaveNoEffectOnSavedApps() {
        assertNull(HomeSelection.move(listOf("A", null, "B"), -1, 0))
        assertNull(HomeSelection.move(listOf("A", null, "B"), 0, 2))
        assertNull(HomeSelection.move(listOf("A", null, "B"), 0, 0))
    }

    @Test fun legacyInactiveSlotsAreArchivedWithoutBeingShown() {
        val input = listOf<String?>("A", null, "B", null, "secret", "old")
        val split = HomeSelection.splitLegacy(input, 3)
        assertEquals(listOf("A", "B"), split.visible)
        assertEquals(listOf(4 to "secret", 5 to "old"), split.archived)
        assertEquals("secret", input[4]) // never mutate the source
    }

    @Test fun zeroLegacyVisibleSlotsKeepsAllAssignmentsDormant() {
        val split = HomeSelection.splitLegacy(listOf("secret", null, "camera"), 0)
        assertEquals(emptyList<String>(), split.visible)
        assertEquals(listOf(0 to "secret", 2 to "camera"), split.archived)
    }

    @Test fun legacyDuplicateSelectionsRemainIntact() {
        val split = HomeSelection.splitLegacy(listOf("A", null, "A", "B"), 3)
        assertEquals(listOf("A", "A"), split.visible)
        assertEquals(listOf(3 to "B"), split.archived)
    }

    @Test fun explicitRecoveryIsCapacityLimitedAndKeepsItsArchiveIndices() {
        val recovery = HomeSelection.recover(
            listOf("A", "B"), listOf(7 to "secret", 9 to "camera"), 3,
        )
        assertEquals(listOf("A", "B", "secret"), recovery.visible)
        assertEquals(listOf(7), recovery.restoredIndices)
        assertEquals(emptyList<Int>(), HomeSelection.recover(
            listOf("A", "B", "C"), listOf(8 to "hidden"), 3,
        ).restoredIndices)
    }

    @Test fun emptyHomeIsSupported() {
        assertEquals(emptyList<String>(), HomeSelection.assigned(listOf(null, null)))
        assertEquals(listOf("A"), HomeSelection.append(listOf(null, null), "A", 10))
    }
}
