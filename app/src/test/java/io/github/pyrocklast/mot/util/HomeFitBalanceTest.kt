package io.github.pyrocklast.mot.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFitBalanceTest {
    @Test fun centersGroupInsideUsableInsets() {
        assertEquals(420, HomeFit.balancedTop(1000, 40, 80, 120, 240))
    }

    @Test fun avoidsOverlappingClockAndDate() {
        assertEquals(360, HomeFit.balancedTop(800, 24, 48, 360, 200))
    }

    @Test fun keepsGroupAboveNavigationWhenPossible() {
        assertEquals(300, HomeFit.balancedTop(800, 24, 100, 200, 400))
    }

    @Test fun respectsMinimumTopWhenScreenIsTooSmall() {
        assertEquals(200, HomeFit.balancedTop(400, 24, 48, 200, 300))
    }
}
