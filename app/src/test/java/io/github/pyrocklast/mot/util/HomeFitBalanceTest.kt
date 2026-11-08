package io.github.pyrocklast.mot.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFitBalanceTest {
    @Test fun oddTextHeightStillMeetsTouchTarget() {
        assertEquals(13, HomeFit.rowPadding(23f, usual = 8, touchTarget = 48))
    }

    @Test fun allRowsFitAtExactBoundary() {
        assertEquals(12, HomeFit.capacity(720, 60))
        assertEquals(12, HomeFit.fittingCount(List(12) { 60 }, 720))
    }

    @Test fun onePixelShortRefusesTheNextRow() {
        assertEquals(11, HomeFit.capacity(719, 60))
        assertEquals(11, HomeFit.fittingCount(List(12) { 60 }, 719))
    }

    @Test fun extraSpaceFromHiddenClockIncreasesCapacity() {
        assertEquals(8, HomeFit.capacity(400, 50))
        assertEquals(10, HomeFit.capacity(500, 50))
    }

    @Test fun largeTextReducesCapacityWithoutCompressingRows() {
        assertEquals(11, HomeFit.capacity(620, 55))
        assertEquals(7, HomeFit.capacity(620, 85))
    }

    @Test fun negativeRoomAndInvalidRowsFitNothing() {
        assertEquals(0, HomeFit.capacity(-1, 50))
        assertEquals(0, HomeFit.capacity(720, 0))
        assertEquals(0, HomeFit.fittingCount(listOf(48, 48), -1))
        assertEquals(0, HomeFit.fittingCount(listOf(0, 48), 200))
    }

    @Test fun preservesPrefixOfVariableHeightRows() {
        assertEquals(1, HomeFit.fittingCount(listOf(48, 200, 48), 150))
    }

    @Test fun preventsOverflowInArithmetic() {
        assertEquals(0, HomeFit.fittingCount(listOf(Int.MAX_VALUE, Int.MAX_VALUE), 30))
    }

    @Test fun centersGroupInsideUsableInsets() {
        assertEquals(400, HomeFit.balancedTop(1000, 40, 80, 120, 240))
    }

    @Test fun avoidsOverlappingClockAndDate() {
        assertEquals(456, HomeFit.balancedTop(800, 24, 48, 360, 200))
    }

    @Test fun keepsGroupAboveNavigationWhenPossible() {
        assertEquals(250, HomeFit.balancedTop(800, 24, 100, 200, 400))
    }

    @Test fun respectsMinimumTopWhenScreenIsTooSmall() {
        assertEquals(200, HomeFit.balancedTop(400, 24, 48, 200, 300))
    }
}
