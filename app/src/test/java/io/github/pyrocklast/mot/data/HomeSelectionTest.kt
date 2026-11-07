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

    @Test fun emptyHomeIsSupported() {
        assertEquals(emptyList<String>(), HomeSelection.assigned(listOf(null, null)))
        assertEquals(listOf("A"), HomeSelection.append(listOf(null, null), "A", 10))
    }
}
