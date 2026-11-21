package io.github.pyrocklast.mot.data

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Settings sections stay collapsed or open as the person left them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CollapsedGroupsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var prefs: Prefs

    @Before fun startOpen() {
        context.getSharedPreferences("mot", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(context)
    }

    @Test fun nothingIsCollapsedAtFirst() {
        assertTrue(prefs.collapsedGroups.isEmpty())
    }

    @Test fun collapsingAndOpeningOneSectionLeavesTheOthersAlone() {
        prefs.setGroupCollapsed("gestures", true)
        prefs.setGroupCollapsed("about", true)
        prefs.setGroupCollapsed("gestures", false)
        assertEquals(setOf("about"), prefs.collapsedGroups)
    }

    @Test fun theStateSurvivesANewPrefs() {
        prefs.setGroupCollapsed("home_screen", true)
        assertEquals(setOf("home_screen"), Prefs(context).collapsedGroups)
    }
}
