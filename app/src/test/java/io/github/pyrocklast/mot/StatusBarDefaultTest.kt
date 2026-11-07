package io.github.pyrocklast.mot

import io.github.pyrocklast.mot.data.Prefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Only a first installation gets the new visible status-bar default.
 * Upgrade detection happens before legacy preference migration writes any keys.
 */
class StatusBarDefaultTest {
    @Test fun freshInstallDefaultsToVisible() {
        assertTrue(Prefs.defaultStatusBarForInstall(hadExistingData = false))
    }

    @Test fun upgradeWithoutExplicitChoiceRetainsHiddenDefault() {
        assertFalse(Prefs.defaultStatusBarForInstall(hadExistingData = true))
    }
}
