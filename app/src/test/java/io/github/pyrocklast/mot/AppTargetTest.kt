package io.github.pyrocklast.mot

import io.github.pyrocklast.mot.data.AppTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppTargetTest {

    private val app = AppTarget("org.example.app", "org.example.app.MainActivity", null, 0)
    private val shortcut = AppTarget("org.example.browser", null, "site:https://example.org/a?b=1", 10)

    @Test
    fun `saved targets read back unchanged`() {
        assertEquals(app, AppTarget.decode(app.encode()))
        assertEquals(shortcut, AppTarget.decode(shortcut.encode()))
    }

    @Test
    fun `apps and shortcuts have distinct keys`() {
        assertNotEquals(app.key, app.copy(userSerial = 10).key)
        assertNotEquals(app.key, app.copy(className = "org.example.app.Other").key)
        assertNotEquals(shortcut.key, shortcut.copy(shortcutId = "other").key)
    }

    @Test
    fun `damaged or missing values read as nothing`() {
        assertNull(AppTarget.decode(null))
        assertNull(AppTarget.decode(""))
        assertNull(AppTarget.decode("org.example.app"))
        assertNull(AppTarget.decode(app.encode().replace("\u001F0", "\u001Fx")))
    }
}
