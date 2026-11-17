package io.github.pyrocklast.mot.util

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The one map a keyboard and a controller share. */
class KeysTest {

    @Test fun `enter, the D-pad's centre and A all open`() {
        assertEquals(KeyAction.OPEN, Keys.action(KeyEvent.KEYCODE_ENTER))
        assertEquals(KeyAction.OPEN, Keys.action(KeyEvent.KEYCODE_NUMPAD_ENTER))
        assertEquals(KeyAction.OPEN, Keys.action(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(KeyAction.OPEN, Keys.action(KeyEvent.KEYCODE_BUTTON_A))
    }

    @Test fun `the menu a long press gives, the drawer, and backing out`() {
        assertEquals(KeyAction.MENU, Keys.action(KeyEvent.KEYCODE_MENU))
        assertEquals(KeyAction.MENU, Keys.action(KeyEvent.KEYCODE_BUTTON_X))
        assertEquals(KeyAction.DRAWER, Keys.action(KeyEvent.KEYCODE_SEARCH))
        assertEquals(KeyAction.DRAWER, Keys.action(KeyEvent.KEYCODE_BUTTON_Y))
        assertEquals(KeyAction.CLOSE, Keys.action(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(KeyAction.CLOSE, Keys.action(KeyEvent.KEYCODE_BUTTON_B))
    }

    @Test fun `the arrows move focus, so they're nothing of ours`() {
        assertEquals(KeyAction.NONE, Keys.action(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(KeyAction.NONE, Keys.action(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(KeyAction.NONE, Keys.action(KeyEvent.KEYCODE_TAB))
        assertEquals(KeyAction.NONE, Keys.action(KeyEvent.KEYCODE_A))
    }

    // Typing on the home screen

    @Test fun `a letter or a figure starts a search`() {
        assertEquals('m', Keys.typed('m'.code, ctrl = false, alt = false, meta = false))
        assertEquals('7', Keys.typed('7'.code, ctrl = false, alt = false, meta = false))
        assertEquals('é', Keys.typed('é'.code, ctrl = false, alt = false, meta = false))
    }

    @Test fun `a shortcut belongs to the system, not to us`() {
        assertNull(Keys.typed('c'.code, ctrl = true, alt = false, meta = false))
        assertNull(Keys.typed('c'.code, ctrl = false, alt = true, meta = false))
        assertNull(Keys.typed('l'.code, ctrl = false, alt = false, meta = true))
    }

    @Test fun `keys that type nothing start nothing`() {
        assertNull(Keys.typed(0, ctrl = false, alt = false, meta = false))
        assertNull(Keys.typed(' '.code, ctrl = false, alt = false, meta = false))
        assertNull(Keys.typed('\n'.code, ctrl = false, alt = false, meta = false))
        assertNull(Keys.typed(27, ctrl = false, alt = false, meta = false))
    }
}
