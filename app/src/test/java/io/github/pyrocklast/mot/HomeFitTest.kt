package io.github.pyrocklast.mot

import io.github.pyrocklast.mot.util.HomeFit
import org.junit.Assert.assertEquals
import org.junit.Test

/** The gap above and below a home app name, in pixels. Sizes here are a phone's: 2.75 pixels to the dp. */
class HomeFitTest {

    private val usual = 22        // 8dp
    private val touchTarget = 132 // 48dp
    private val safety = 5        // 2dp
    private val room = 1700f      // the screen under the clock

    private fun gap(count: Int, nameHeight: Float, room: Float = this.room) =
        HomeFit.namePadding(count, nameHeight, room, usual, touchTarget, safety)

    @Test
    fun `with room to spare, the usual gap`() {
        assertEquals(usual, gap(count = 4, nameHeight = 109f))
        assertEquals(usual, gap(count = 1, nameHeight = 109f))
    }

    @Test
    fun `tight, the gap closes up by just enough`() {
        // 12 names of 109px need 1308px; the rest is 16px above and below each
        assertEquals(16, gap(count = 12, nameHeight = 109f))
    }

    @Test
    fun `tighter than the names fit, the gap stops at a row you can still tap`() {
        assertEquals(12, gap(count = 20, nameHeight = 109f))  // round up: 109 + 2 x 12 >= 132px
        assertEquals(0, gap(count = 20, nameHeight = 164f))   // taller than 48dp on its own
    }

    @Test
    fun `small names are padded out to a row you can tap, even past the usual gap`() {
        assertEquals(39, gap(count = 4, nameHeight = 54f))    // 54 + 2 x 39 = 48dp
        assertEquals(39, gap(count = 40, nameHeight = 54f))   // and no tighter, however many there are
        assertEquals(23, gap(count = 4, nameHeight = 87f))
    }

    @Test
    fun `no names, or names of no height, ask nothing of the screen`() {
        assertEquals(usual, gap(count = 0, nameHeight = 109f))
        assertEquals(usual, gap(count = 4, nameHeight = 0f))
    }

    @Test
    fun `before the screen is measured, the gap is as tight as it goes`() {
        assertEquals(12, gap(count = 4, nameHeight = 109f, room = 0f))
    }
}
