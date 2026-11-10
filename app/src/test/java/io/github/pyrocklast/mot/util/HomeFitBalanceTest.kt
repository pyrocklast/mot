package io.github.pyrocklast.mot.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** The home screen's arithmetic, in pixels unless it says dp. */
class HomeFitBalanceTest {

    // Rows

    @Test fun `small names still get a 48 dp target and 8 dp to the next`() {
        // 24 dp text is about 32 dp tall: 8 dp above and below makes 48, and rows stay 56 apart
        assertEquals(56, HomeFit.rowHeight(nameHeight = 32, minRow = 56, padding = 8))
    }

    @Test fun `bigger names get 8 dp above and below`() {
        assertEquals(61, HomeFit.rowHeight(nameHeight = 45, minRow = 56, padding = 8))
    }

    @Test fun `rows that fit, keeping a little spare`() {
        assertEquals(9, HomeFit.slots(room = 560, row = 61, safety = 2))
        assertEquals(10, HomeFit.slots(room = 562, row = 56, safety = 2))
        assertEquals(9, HomeFit.slots(room = 561, row = 56, safety = 2))
    }

    @Test fun `no room, or no rows, fit nothing`() {
        assertEquals(0, HomeFit.slots(room = 0, row = 56, safety = 2))
        assertEquals(0, HomeFit.slots(room = -40, row = 56, safety = 2))
        assertEquals(0, HomeFit.slots(room = 500, row = 0, safety = 2))
    }

    // The time and date grow with the screen's width

    @Test fun `a 360 dp screen is the starting point`() {
        assertEquals(1f, HomeFit.growth(360), 0.001f)
    }

    @Test fun `wider screens get them bigger, up to a quarter`() {
        assertEquals(1.144f, HomeFit.growth(412), 0.001f)
        assertEquals(1.244f, HomeFit.growth(448), 0.001f)
        assertEquals(1.25f, HomeFit.growth(600), 0.001f)
    }

    @Test fun `narrower screens don't make them smaller`() {
        assertEquals(1f, HomeFit.growth(320), 0.001f)
    }

    // More names than rows

    @Test fun `all the names when they fit`() {
        assertEquals(HomeFit.VisibleRows(9, false), HomeFit.visibleRows(count = 9, slots = 9))
        assertEquals(HomeFit.VisibleRows(0, false), HomeFit.visibleRows(count = 0, slots = 9))
    }

    @Test fun `too many, and the last row is All apps`() {
        assertEquals(HomeFit.VisibleRows(8, true), HomeFit.visibleRows(count = 11, slots = 9))
        assertEquals(HomeFit.VisibleRows(0, true), HomeFit.visibleRows(count = 3, slots = 1))
    }

    @Test fun `no rows at all, and swiping up is still there`() {
        assertEquals(HomeFit.VisibleRows(0, false), HomeFit.visibleRows(count = 3, slots = 0))
    }

    // The time and date, placed by what you see. A 360 dp phone at one pixel to the dp: the
    // time's view has its baseline 68 below its top and ends 18 under it, its figures 46 tall;
    // the date's baseline is 23 below its view's top, its capitals 15 tall, descenders 4 deep.

    private fun place(showTime: Boolean = true, showDate: Boolean = true) = HomeFit.placeClock(
        inkTop = 72, showTime = showTime, showDate = showDate,
        timeBaseline = 68, timeBottom = 18, figures = 46,
        dateBaseline = 23, capitals = 15, descenders = 4,
        gap = 16, underClock = 24,
    )

    @Test fun `the figures start where the time should, and the date's capitals 16 under them`() {
        val place = place()
        assertEquals(50, place.top)                   // 50 + 68 - 46: the figures' top is at 72
        assertEquals(-10, place.dateMargin)           // the date's baseline at 118 + 16 + 15
        assertEquals(177, place.namesTop)             // 24 under the date's descenders
    }

    @Test fun `without the date, the names start 24 under the figures`() {
        assertEquals(HomeFit.ClockPlace(50, 0, 142), place(showDate = false))
    }

    @Test fun `without the time, the date's capitals start where the time would`() {
        assertEquals(HomeFit.ClockPlace(64, 0, 115), place(showTime = false))
    }

    @Test fun `without either, the names may start right under the status bar's gap`() {
        assertEquals(HomeFit.ClockPlace(72, 0, 72), place(showTime = false, showDate = false))
    }

    // Where the names start

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
