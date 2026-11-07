package io.github.pyrocklast.mot

import android.app.Application
import android.content.Context
import io.github.pyrocklast.mot.data.AppTarget
import io.github.pyrocklast.mot.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real SharedPreferences integration tests for non-destructive home migration. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HomeMigrationIntegrationTest {
    private lateinit var context: Context

    private val phone = AppTarget("example.phone", "PhoneActivity", null, 0)
    private val camera = AppTarget("example.camera", "CameraActivity", null, 0)
    private val secret = AppTarget("example.secret", "SecretActivity", null, 10)

    @Before fun resetPrefs() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mot", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("mot_names", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun store() = context.getSharedPreferences("mot", Context.MODE_PRIVATE)

    @Test fun compactionKeepsOnlyVisibleAssignmentsAndArchivesDormantTargets() {
        store().edit()
            .putInt("home_app_count", 3)
            .putString("home_app_0", phone.encode())
            .putString("home_app_2", camera.encode())
            .putString("home_app_4", secret.encode())
            .commit()

        val prefs = Prefs(context)
        prefs.migrate()
        assertEquals(2, prefs.homeAppCount)
        assertEquals(phone, prefs.homeApp(0))
        assertEquals(camera, prefs.homeApp(1))
        assertNull(prefs.homeApp(2))
        assertEquals(1, prefs.archivedHomeAppCount)
        assertEquals(secret.encode(), store().getString("archived_home_app_4", null))
        assertFalse(store().contains("home_app_4"))

        // The second launch must not promote, duplicate or rewrite archived assignments.
        prefs.migrate()
        assertEquals(2, prefs.homeAppCount)
        assertEquals(1, prefs.archivedHomeAppCount)
    }

    @Test fun zeroVisibleSlotsNeverRevealLegacyAssignments() {
        store().edit().putInt("home_app_count", 0)
            .putString("home_app_0", secret.encode()).commit()
        val prefs = Prefs(context)
        prefs.migrate()
        assertEquals(0, prefs.homeAppCount)
        assertNull(prefs.homeApp(0))
        assertEquals(1, prefs.archivedHomeAppCount)
    }

    @Test fun restoringArchivesRequiresExplicitCallAndPreservesDrawerHideState() {
        store().edit().putInt("home_app_count", 1)
            .putString("home_app_0", phone.encode())
            .putString("home_app_4", secret.encode())
            .putStringSet("hidden_apps", setOf(secret.appKey)).commit()
        val prefs = Prefs(context)
        prefs.migrate()
        assertEquals(1, prefs.homeAppCount)
        assertEquals(1, prefs.archivedHomeAppCount)
        assertEquals(1, prefs.restoreArchivedHomeApps())
        assertEquals(secret, prefs.homeApp(1))
        assertEquals(0, prefs.archivedHomeAppCount)
        assertTrue(secret.appKey in prefs.hiddenApps)
    }

    @Test fun removingAnAppClosesGapWithoutTouchingArchives() {
        store().edit().putInt("home_app_count", 2)
            .putString("home_app_0", phone.encode())
            .putString("home_app_1", camera.encode())
            .putString("home_app_7", secret.encode()).commit()
        val prefs = Prefs(context)
        prefs.migrate()
        prefs.setHomeApp(0, null)
        assertEquals(1, prefs.homeAppCount)
        assertEquals(camera, prefs.homeApp(0))
        assertNull(prefs.homeApp(1))
        assertEquals(1, prefs.archivedHomeAppCount)
    }

    @Test fun crashLookResetNeverErasesCompactHomeCount() {
        store().edit().putInt("home_app_count", 1)
            .putString("home_app_0", phone.encode()).commit()
        val prefs = Prefs(context)
        prefs.migrate()
        repeat(3) { prefs.recordCrash() }
        assertTrue(prefs.resetLookAfterCrashes())
        assertEquals(1, prefs.homeAppCount)
        assertEquals(phone, prefs.homeApp(0))
    }
}
