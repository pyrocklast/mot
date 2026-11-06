package io.github.pyrocklast.mot.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFitBalanceTest {
    @Test fun oddTextHeightStillMeetsTouchTarget() {
        val padding = HomeFit.namePadding(
            count = 5, nameHeight = 23f, room = 500f,
            usual = 8, touchTarget = 48, safety = 2,
        )
        assertEquals(13, padding)
    }


    @Test fun allRowsFitAtExactBoundaryWithoutOverflow() {
        assertEquals(HomeFit.VisibleRows(10, false), HomeFit.visibleRows(List(10) { 48 }, 480, 48))
    }

    @Test fun overflowingRowsReserveAFullDrawerShortcut() {
        assertEquals(HomeFit.VisibleRows(8, true), HomeFit.visibleRows(List(10) { 48 }, 450, 48))
    }

    @Test fun aVeryShortViewportShowsOnlyTheDrawerShortcut() {
        assertEquals(HomeFit.VisibleRows(0, true), HomeFit.visibleRows(List(10) { 48 }, 48, 48))
    }

    @Test fun noRoomNeverRendersAClippedDrawerShortcut() {
        assertEquals(HomeFit.VisibleRows(0, false), HomeFit.visibleRows(List(10) { 48 }, 30, 48))
    }

    @Test fun tallerLabelsDoNotAllowLaterSlotsToJumpAhead() {
        assertEquals(HomeFit.VisibleRows(1, true), HomeFit.visibleRows(listOf(48, 200, 48), 150, 48))
    }

    @Test fun noAssignedAppsNeedNoDrawerShortcut() {
        assertEquals(HomeFit.VisibleRows(0, false), HomeFit.visibleRows(emptyList(), 300, 48))
    }

    @Test fun exactSpaceForRowAndShortcutKeepsFirstRow() {
        assertEquals(HomeFit.VisibleRows(1, true), HomeFit.visibleRows(listOf(64, 64, 64), 112, 48))
    }

    @Test fun sizeArithmeticCannotOverflowIntoFalseFit() {
        assertEquals(HomeFit.VisibleRows(0, false), HomeFit.visibleRows(
            listOf(Int.MAX_VALUE, Int.MAX_VALUE), 30, 48,
        ))
    }

    @Test fun centersGroupInsideUsableInsets() {
        assertEquals(360, HomeFit.balancedTop(1000, 40, 80, 120, 240))
    }

    @Test fun avoidsOverlappingClockAndDate() {
        assertEquals(360, HomeFit.balancedTop(800, 24, 48, 360, 200))
    }

    @Test fun keepsGroupAboveNavigationWhenPossible() {
        assertEquals(200, HomeFit.balancedTop(800, 24, 100, 200, 400))
    }

    @Test fun respectsMinimumTopWhenScreenIsTooSmall() {
        assertEquals(200, HomeFit.balancedTop(400, 24, 48, 200, 300))
    }
}
