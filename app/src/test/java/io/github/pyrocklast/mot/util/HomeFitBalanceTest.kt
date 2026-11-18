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
        assertEquals(HomeFit.ClockPlace(50, 0, 142, 118), place(showDate = false))
    }

    @Test fun `without the time, the date's capitals start where the time would`() {
        assertEquals(HomeFit.ClockPlace(64, 0, 115, 87), place(showTime = false))
    }

    @Test fun `without either, the names may start right under the status bar's gap`() {
        assertEquals(HomeFit.ClockPlace(72, 0, 72, null), place(showTime = false, showDate = false))
    }

    // The drawer's search box, on the home screen's first line. A 34 dp name has its baseline
    // 32 below its view's top, capitals 25 tall, and takes 42; its box is 48, to stay worth
    // tapping, so the text sits 3 below the box's top.

    private fun search(
        headerBaseline: Int? = 118, baseline: Int = 32, capitals: Int = 25,
        textHeight: Int = 42, boxHeight: Int = 48,
    ) = HomeFit.searchTop(headerBaseline, inkTop = 72, baseline, capitals, textHeight, boxHeight)

    @Test fun `the search box sits on the time's baseline`() {
        // The time's baseline is at 118: the text goes at 86, the box 3 above it
        assertEquals(83, search())
    }

    @Test fun `it follows the date when the time is hidden`() {
        assertEquals(52, search(headerBaseline = 87))
    }

    @Test fun `with neither, its capitals start where the clock's figures would have`() {
        val top = search(headerBaseline = null)
        assertEquals(62, top)
        assertEquals(72, top + 3 + 32 - 25)
    }

    @Test fun `names too big for a 48 dp box keep the same line`() {
        // A 48 dp name: baseline 45 below the view's top, capitals 35 tall, taking 60
        assertEquals(73, search(baseline = 45, capitals = 35, textHeight = 60, boxHeight = 60))
    }

    // Where a column sits in the room it has

    @Test fun `centred in the room between the two`() {
        assertEquals(400, HomeFit.centre(top = 120, bottom = 920, height = 240))
        assertEquals(456, HomeFit.centre(top = 360, bottom = 752, height = 200))
        assertEquals(250, HomeFit.centre(top = 200, bottom = 700, height = 400))
    }

    @Test fun `taller than the room, so it starts at the top of it`() {
        assertEquals(200, HomeFit.centre(top = 200, bottom = 352, height = 300))
    }

}
