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
        prefs.homeCapacity = 2
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
        prefs.homeCapacity = 10
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
        prefs.homeCapacity = 10
        prefs.migrate()
        (0 until 10).forEach { assertTrue(prefs.appendHomeApp(target("app$it"))) }
        prefs.migrate()
        assertEquals(10, Prefs(context).assignedHomeApps.size)
        assertEquals(0, prefs.archivedHomeAppCount)
        assertEquals(10, prefs.homeAppCount)
    }

    @Test fun compactHomeBuiltBeforeTheMigrationCanRecoverHigherSlotAssignments() {
        // Older compact-home builds displayed every assigned slot without recording a marker.
        // We cannot tell whether a higher slot was formerly hidden. Prefer the private choice
        // and preserve all data so the user can restore it explicitly.
        val kept = target("kept")
        val added = target("added")
        raw.edit().putInt("home_app_count", 1)
            .putString("home_app_0", kept.encode())
            .putString("home_app_3", added.encode()).commit()
        val prefs = Prefs(context)
        prefs.homeCapacity = 2
        prefs.migrate()
        assertEquals(listOf(kept), prefs.assignedHomeApps)
        assertEquals(1, prefs.archivedHomeAppCount)
        assertEquals(1, prefs.restoreArchivedHomeApps())
        assertEquals(listOf(kept, added), prefs.assignedHomeApps)
        assertEquals(0, prefs.archivedHomeAppCount)
    }

    @Test fun screenMeasuredCapacityCanExceedTenWithoutLosingFavorites() {
        val prefs = Prefs(context)
        prefs.migrate()
        prefs.homeCapacity = 13
        (0 until 13).forEach { assertTrue(prefs.appendHomeApp(target("app$it"))) }
        assertFalse(prefs.appendHomeApp(target("extra")))
        assertEquals(13, prefs.assignedHomeApps.size)
        assertEquals(13, prefs.homeAppCount)
        assertEquals(target("app12"), prefs.homeApp(12))
        // Android may subsequently increase font size or restore the clock.
        // Existing assignments survive, but no new app can be added while they don't fit.
        prefs.homeCapacity = 8
        assertFalse(prefs.appendHomeApp(target("extra")))
        assertEquals(13, Prefs(context).assignedHomeApps.size)
        assertEquals(13, Prefs(context).homeAppCount)
    }

    @Test fun recoveredLegacyFavoritesCanUseNewlyFreedSpaceBeyondTen() {
        val prefs = Prefs(context)
        prefs.migrate()
        prefs.homeCapacity = 12
        (0 until 11).forEach { assertTrue(prefs.appendHomeApp(target("app$it"))) }
        raw.edit().putString("archived_home_app_9", target("archived").encode()).commit()
        assertEquals(1, prefs.restoreArchivedHomeApps())
        assertEquals(12, prefs.assignedHomeApps.size)
        assertEquals(0, prefs.archivedHomeAppCount)
    }

    @Test fun addingFavoritesRequiresARealMeasuredViewportAndStopsAtCapacity() {
        val prefs = Prefs(context)
        prefs.migrate()
        assertEquals(-1, prefs.homeCapacity)
        assertFalse(prefs.appendHomeApp(target("beforeMeasure")))
        prefs.homeCapacity = 2
        assertTrue(prefs.appendHomeApp(target("one")))
        assertTrue(prefs.appendHomeApp(target("two")))
        assertFalse(prefs.appendHomeApp(target("three")))
        assertEquals(listOf(target("one"), target("two")), prefs.assignedHomeApps)
    }

    @Test fun displayChangesInvalidateCapacityWithoutDeletingAnySavedAssignments() {
        val prefs = Prefs(context)
        prefs.migrate()
        prefs.homeCapacity = 11
        (0 until 11).forEach { assertTrue(prefs.appendHomeApp(target("app$it"))) }
        prefs.showTime = false
        assertEquals(-1, prefs.homeCapacity)
        assertEquals(11, prefs.assignedHomeApps.size)
        prefs.homeCapacity = 13
        prefs.showDate = false
        assertEquals(-1, prefs.homeCapacity)
        prefs.homeCapacity = 13
        prefs.appNameScale = 1.5f
        assertEquals(-1, prefs.homeCapacity)
        prefs.homeCapacity = 13
        prefs.showStatusBar = false
        assertEquals(-1, prefs.homeCapacity)
        assertEquals(11, prefs.assignedHomeApps.size)
    }

    @Test fun crashRecoveryDoesNotEraseTheFormerHomeVisibilityBoundary() {
        val active = target("active")
        val dormant = target("dormant")
        raw.edit().putInt("home_app_count", 1)
            .putString("home_app_0", active.encode())
            .putString("home_app_8", dormant.encode()).commit()
        val prefs = Prefs(context)
        prefs.migrate()
        raw.edit().putInt("crashes", 3).commit()
        assertTrue(prefs.resetLookAfterCrashes())
        assertEquals(1, prefs.homeAppCount)
        assertEquals(listOf(active), prefs.assignedHomeApps)
        assertEquals(1, prefs.archivedHomeAppCount)
        prefs.migrate()
        assertEquals(listOf(active), prefs.assignedHomeApps)
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
