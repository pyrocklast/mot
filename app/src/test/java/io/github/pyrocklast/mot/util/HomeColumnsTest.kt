package io.github.pyrocklast.mot.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The names in columns, in pixels at one pixel to the dp. A Fairphone 4 on its side is 936 by
 * 432, with 28 of status bar at the top and 32 kept at the bottom; upright it is 432 by 936.
 */
class HomeColumnsTest {

    // How many to a column

    @Test fun `names that fit are spread evenly, not packed into the first column`() {
        assertEquals(listOf(5, 5), HomeFit.arrange(count = 10, perColumn = 6, maxColumns = 3).counts)
        assertEquals(listOf(3, 2), HomeFit.arrange(count = 5, perColumn = 3, maxColumns = 3).counts)
        assertEquals(listOf(3, 3, 2), HomeFit.arrange(count = 8, perColumn = 3, maxColumns = 3).counts)
    }

    @Test fun `one column while they fit in one`() {
        assertEquals(listOf(5), HomeFit.arrange(count = 5, perColumn = 6, maxColumns = 2).counts)
        assertEquals(listOf(6), HomeFit.arrange(count = 6, perColumn = 6, maxColumns = 2).counts)
    }

    @Test fun `too many to fit: the columns fill up and the last row says All apps`() {
        val spread = HomeFit.arrange(count = 10, perColumn = 3, maxColumns = 2)
        assertEquals(listOf(3, 2), spread.counts)
        assertTrue(spread.overflow)
        assertEquals(5, spread.shown)
    }

    @Test fun `room for one row and more names than that: All apps is all there is`() {
        val spread = HomeFit.arrange(count = 4, perColumn = 1, maxColumns = 1)
        assertEquals(listOf(0), spread.counts)
        assertTrue(spread.overflow)
    }

    @Test fun `nothing to place, or nowhere to place it`() {
        assertEquals(emptyList<Int>(), HomeFit.arrange(count = 0, perColumn = 6, maxColumns = 2).counts)
        assertEquals(emptyList<Int>(), HomeFit.arrange(count = 5, perColumn = 0, maxColumns = 2).counts)
        assertEquals(emptyList<Int>(), HomeFit.arrange(count = 5, perColumn = 6, maxColumns = 0).counts)
    }

    // Beside the time, or under it

    /** A screen [width] by [height], names [row] tall, the time and date [clockWidth] wide. */
    private fun fit(row: Int = 59, namesTop: Int = 165, inkTop: Int = 60) = HomeFit.Layout(
        timeSize = 64f, dateSize = 21f, nameSize = 34f,
        clockTop = 14, dateMargin = -10, markerGap = 16, colonLift = 0f,
        inkTop = inkTop, namesTop = namesTop, columnGap = 56, safety = 2,
        searchTop = 75, searchHeight = 48, bottom = 32, row = row, capacity = 0,
    )

    private fun names(
        count: Int, width: Int, height: Int, row: Int = 59, nameWidth: Int = 220,
        namesTop: Int = 165, clockWidth: Int = 190,
    ) = HomeFit.names(
        fit(row = row, namesTop = namesTop), count = count, nameWidth = nameWidth,
        clockWidth = clockWidth, left = 24, right = width - 24, bottom = height - 32,
    )

    @Test fun `upright, the names stay under the time in one column`() {
        val names = names(count = 5, width = 432, height = 936)
        assertEquals(listOf(5), names.counts)
        assertEquals(24, names.left)
        assertFalse(names.overflow)
    }

    @Test fun `upright with more names than the screen holds, the last row says All apps`() {
        // 936 - 165 - 32 leaves room for twelve rows of 59, one of which says "All apps"
        val names = names(count = 14, width = 432, height = 936)
        assertEquals(listOf(11), names.counts)
        assertTrue(names.overflow)
        // Upright, there is no room for a whole name beside the time: squeezing fourteen cut-off
        // names into the corner is not "showing more"
        assertEquals(24, names.left)
    }

    @Test fun `on its side, five names go beside the time rather than two columns under it`() {
        val names = names(count = 5, width = 936, height = 432)
        assertEquals(listOf(5), names.counts)
        assertEquals(24 + 190 + 56, names.left)
    }

    @Test fun `on its side, ten names are two columns of five beside the time`() {
        val names = names(count = 10, width = 936, height = 432)
        assertEquals(listOf(5, 5), names.counts)
        assertEquals(24 + 190 + 56, names.left)
        assertFalse(names.overflow)
    }

    @Test fun `big names on its side: under the time shows more than beside it`() {
        // 76 tall and 300 wide: one column of that width fits beside the time, two fit under it
        val names = names(count = 10, width = 936, height = 432, row = 76, nameWidth = 300)
        assertEquals(24, names.left)
        assertEquals(5, names.shown)
        assertTrue(names.overflow)
    }

    @Test fun `columns share a top, so the names line up across them`() {
        val names = names(count = 9, width = 936, height = 432)
        assertEquals(listOf(5, 4), names.counts)
        // The taller column, five rows of 59, centred between the time's line and the bottom
        assertEquals(60 + (400 - 60 - 5 * 59) / 2, names.top)
    }

    @Test fun `a name wider than the screen still gets its column`() {
        val names = names(count = 3, width = 432, height = 936, nameWidth = 2000)
        assertEquals(listOf(3), names.counts)
        assertEquals(432 - 48, names.columnWidth)
    }
}
