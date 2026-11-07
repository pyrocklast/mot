package io.github.pyrocklast.mot

import io.github.pyrocklast.mot.data.isPrivateSpaceEntryPointVisible
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateSpaceVisibilityTest {

    @Test
    fun `visible setting keeps locked entry point visible`() {
        assertTrue(isPrivateSpaceEntryPointVisible(locked = true, entrypointHidden = false))
    }

    @Test
    fun `hidden setting hides entry point only while locked`() {
        assertFalse(isPrivateSpaceEntryPointVisible(locked = true, entrypointHidden = true))
        assertTrue(isPrivateSpaceEntryPointVisible(locked = false, entrypointHidden = true))
    }

    @Test
    fun `unlocked entry point remains visible when hidden setting is off`() {
        assertTrue(isPrivateSpaceEntryPointVisible(locked = false, entrypointHidden = false))
    }
}
