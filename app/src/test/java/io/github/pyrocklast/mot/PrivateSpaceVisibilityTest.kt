package io.github.pyrocklast.mot

import io.github.pyrocklast.mot.data.isPrivateSpaceEntryPointVisible
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateSpaceVisibilityTest {
    @Test fun lockedPrivateSpaceWithHiddenEntryIsNotVisible() {
        assertFalse(isPrivateSpaceEntryPointVisible(locked = true, entrypointHidden = true))
    }

    @Test fun lockedPrivateSpaceWithVisibleEntryRemainsAccessible() {
        assertTrue(isPrivateSpaceEntryPointVisible(locked = true, entrypointHidden = false))
    }

    @Test fun unlockedPrivateSpaceIsVisibleRegardlessOfHiddenPreference() {
        assertTrue(isPrivateSpaceEntryPointVisible(locked = false, entrypointHidden = false))
        assertTrue(isPrivateSpaceEntryPointVisible(locked = false, entrypointHidden = true))
    }
}
