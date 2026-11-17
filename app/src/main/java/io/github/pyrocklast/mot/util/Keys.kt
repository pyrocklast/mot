package io.github.pyrocklast.mot.util

import android.view.KeyEvent

/**
 * What a key or a controller's button does in the launcher. Touch has gestures; a keyboard has
 * these, and a controller sends the same key codes, so both are the one map.
 */
enum class KeyAction {
    /** Enter, D-pad centre, A: open what's focused. */
    OPEN,

    /** Menu, X: the menu a long press gives. */
    MENU,

    /** Search, Y: the app drawer, as swiping up does. */
    DRAWER,

    /** Escape, B: back out of the drawer. */
    CLOSE,

    NONE,
}

object Keys {

    fun action(keyCode: Int): KeyAction = when (keyCode) {
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A -> KeyAction.OPEN

        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_X -> KeyAction.MENU
        KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_BUTTON_Y -> KeyAction.DRAWER
        KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BUTTON_B -> KeyAction.CLOSE
        else -> KeyAction.NONE
    }

    /**
     * The letter a key would type on the home screen, which starts a search for it, or null
     * when it types nothing: a key held with Ctrl, Alt or the meta key is a shortcut for the
     * system to deal with, and the arrows, space and the rest type nothing at all.
     */
    fun typed(event: KeyEvent): Char? =
        typed(event.unicodeChar, event.isCtrlPressed, event.isAltPressed, event.isMetaPressed)

    internal fun typed(unicodeChar: Int, ctrl: Boolean, alt: Boolean, meta: Boolean): Char? {
        if (ctrl || alt || meta || unicodeChar == 0) return null
        val char = unicodeChar.toChar()
        return char.takeIf { !it.isISOControl() && !it.isWhitespace() }
    }

    private fun Char.isISOControl() = this.code < 0x20 || this.code in 0x7F..0x9F
}
