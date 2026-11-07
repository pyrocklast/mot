package io.github.pyrocklast.mot.data

import android.app.Application
import android.content.Context
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HomeVisibilityMigrationTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val raw get() = context.getSharedPreferences("mot", Context.MODE_PRIVATE)

    @Before fun clearPrefs() {
        raw.edit().clear().commit()
        context.getSharedPreferences("mot_names", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun target(name: String) = AppTarget("org.example.$name", "Main", null, 1L)

    @Test fun oldHiddenSlotsNeverBecomeVisibleOnFirstOrRepeatedLaunch() {
        val visible = target("visible")
        val secret = target("secret")
        raw.edit().putInt("home_app_count", 2)
            .putString("home_app_0", visible.encode())
            .putString("home_app_7", secret.encode()).commit()
        val prefs = Prefs(context)
        prefs.migrate()
        assertEquals(listOf(visible), prefs.assignedHomeApps)
        assertEquals(1, prefs.archivedHomeAppCount)
        assertNull(prefs.homeApp(7))
        assertEquals(secret.encode(), raw.getString("archived_home_app_7", null))
        prefs.migrate()
        val reopened = Prefs(context)
        reopened.migrate()
        assertEquals(listOf(visible), reopened.assignedHomeApps)
        assertEquals(1, reopened.archivedHomeAppCount)
    }

    @Test fun formerZeroVisibleSlotsArchiveEveryAssignment() {
        raw.edit().putInt("home_app_count", 0)
            .putString("home_app_0", target("private").encode())
            .putString("home_app_5", target("work").encode()).commit()
        val prefs = Prefs(context)
        prefs.migrate()
        assertTrue(prefs.assignedHomeApps.isEmpty())
        assertEquals(2, prefs.archivedHomeAppCount)
    }

    @Test fun restorationRequiresAnExplicitCallAndDoesNotUnhideDrawerApps() {
        val kept = target("kept")
        val hidden = target("hidden")
        raw.edit().putInt("home_app_count", 1)
            .putString("home_app_0", kept.encode())
            .putString("home_app_8", hidden.encode())
            .putStringSet("hidden_apps", setOf(hidden.appKey)).commit()
        val prefs = Prefs(context)
        prefs.migrate()
        assertEquals(listOf(kept), prefs.assignedHomeApps)
        assertEquals(1, prefs.restoreArchivedHomeApps())
        assertEquals(listOf(kept, hidden), prefs.assignedHomeApps)
        assertEquals(0, prefs.archivedHomeAppCount)
        assertEquals(0, prefs.restoreArchivedHomeApps())
        assertTrue(hidden.appKey in prefs.hiddenApps)
    }

    @Test fun fullHomeRetainsArchivesUntilCapacityIsAvailable() {
        raw.edit().putInt("home_app_count", 0)
            .putString("home_app_7", target("dormant").encode()).commit()
        val prefs = Prefs(context)
        prefs.migrate()
        (0 until 10).forEach { assertTrue(prefs.appendHomeApp(target("app$it"))) }
        assertEquals(0, prefs.restoreArchivedHomeApps())
        assertEquals(1, prefs.archivedHomeAppCount)
        prefs.setHomeApp(9, null)
        assertEquals(1, prefs.restoreArchivedHomeApps())
        assertEquals(10, prefs.assignedHomeApps.size)
        assertEquals(0, prefs.archivedHomeAppCount)
    }

    @Test fun newlyAssignedAppsRemainVisibleAfterMigration() {
        val prefs = Prefs(context)
        prefs.migrate()
        (0 until 10).forEach { assertTrue(prefs.appendHomeApp(target("app$it"))) }
        prefs.migrate()
        assertEquals(10, Prefs(context).assignedHomeApps.size)
        assertEquals(0, prefs.archivedHomeAppCount)
        assertEquals(10, prefs.homeAppCount)
    }

    @Test fun priorExperimentalMigrationIsNotAppliedTwice() {
        val active = target("active")
        val archived = target("archived")
        raw.edit().putBoolean("home_compacted_v2", true)
            .putInt("home_app_count", 1)
            .putString("home_app_0", active.encode())
            .putString("archived_home_app_7", archived.encode()).commit()
        val prefs = Prefs(context)
        prefs.migrate()
        assertEquals(listOf(active), prefs.assignedHomeApps)
        assertEquals(1, prefs.archivedHomeAppCount)
        assertTrue(raw.getBoolean("home_visibility_migrated_v3", false))
        assertFalse(prefs.assignedHomeApps.contains(archived))
    }
}
