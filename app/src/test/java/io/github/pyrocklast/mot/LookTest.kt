package io.github.pyrocklast.mot

import io.github.pyrocklast.mot.util.Look
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a wallpaper's colours come to one brightness, which decides the text colour for the
 * wallpapers that send Android no hint about themselves.
 */
class LookTest {

    private val BLACK = 0f
    private val WHITE = 1f

    private fun brightness(primary: Float, secondary: Float? = null, tertiary: Float? = null) =
        Look.brightness(primary, secondary, tertiary)

    @Test
    fun `one colour is its own brightness`() {
        assertEquals(0.8f, brightness(0.8f), 0.001f)
        assertEquals(0f, brightness(0f), 0.001f)
    }

    @Test
    fun `the most common colour counts for most`() {
        // Light wallpaper, darker trim: still light
        assertEquals(0.667f, brightness(1f, 0.5f, 0f), 0.001f)
        // The other way round
        assertEquals(0.333f, brightness(0f, 0.5f, 1f), 0.001f)
    }

    @Test
    fun `dark text wins on anything but a dark wallpaper`() {
        // A mid orange wallpaper: dark text stands out three times as well as light text
        val orange = 0.35f
        assertTrue(Look.contrast(orange, BLACK) > Look.contrast(orange, WHITE))
        // A dark wallpaper: the other way round
        val night = 0.05f
        assertTrue(Look.contrast(night, WHITE) > Look.contrast(night, BLACK))
        // They are even where the eye says they are, not at the half-way point
        assertEquals(Look.contrast(0.179f, BLACK), Look.contrast(0.179f, WHITE), 0.01f)
    }

    @Test
    fun `colours that aren't there don't count`() {
        assertEquals(brightness(0.6f), brightness(0.6f, null, null), 0.001f)
        assertEquals(0.4f, brightness(0.2f, 0.7f), 0.001f)
    }
}
