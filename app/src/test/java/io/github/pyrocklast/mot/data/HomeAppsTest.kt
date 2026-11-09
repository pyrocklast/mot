package io.github.pyrocklast.mot.data

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The home screen's apps: one list, in order, at most ten, each app once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HomeAppsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var prefs: Prefs

    @Before fun startEmpty() {
        context.getSharedPreferences("mot", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(context)
    }

    private fun app(name: String) = AppTarget("org.example.$name", "Main", null, 0L)
    private val maps = app("maps")
    private val mail = app("mail")
    private val music = app("music")

    @Test fun `none to start with`() {
        assertEquals(emptyList<AppTarget>(), prefs.homeApps)
    }

    @Test fun `added apps go at the end, and are still there next time`() {
        assertTrue(prefs.addHomeApp(maps))
        assertTrue(prefs.addHomeApp(mail))
        assertEquals(listOf(maps, mail), Prefs(context).homeApps)
    }

    @Test fun `an app is only on the home screen once`() {
        prefs.addHomeApp(maps)
        assertFalse(prefs.addHomeApp(maps))
        assertEquals(listOf(maps), prefs.homeApps)
    }

    @Test fun `ten at most`() {
        repeat(Prefs.MAX_HOME_APPS) { assertTrue(prefs.addHomeApp(app("app$it"))) }
        assertFalse(prefs.addHomeApp(maps))
        assertEquals(Prefs.MAX_HOME_APPS, prefs.homeApps.size)
    }

    @Test fun `moving one makes room for it`() {
        listOf(maps, mail, music).forEach { prefs.addHomeApp(it) }
        prefs.moveHomeApp(from = 2, to = 0)
        assertEquals(listOf(music, maps, mail), prefs.homeApps)
        prefs.moveHomeApp(from = 0, to = 2)
        assertEquals(listOf(maps, mail, music), prefs.homeApps)
    }

    @Test fun `moving to or from nowhere changes nothing`() {
        listOf(maps, mail).forEach { prefs.addHomeApp(it) }
        prefs.moveHomeApp(from = 0, to = 5)
        prefs.moveHomeApp(from = -1, to = 0)
        assertEquals(listOf(maps, mail), prefs.homeApps)
    }

    @Test fun `changing one keeps its place`() {
        listOf(maps, mail).forEach { prefs.addHomeApp(it) }
        prefs.replaceHomeApp(maps, music)
        assertEquals(listOf(music, mail), prefs.homeApps)
    }

    @Test fun `changing one that has gone adds the new one at the end`() {
        prefs.addHomeApp(mail)
        prefs.replaceHomeApp(maps, music)
        assertEquals(listOf(mail, music), prefs.homeApps)
    }

    @Test fun `removing one closes the gap`() {
        listOf(maps, mail, music).forEach { prefs.addHomeApp(it) }
        prefs.removeHomeApp(mail)
        assertEquals(listOf(maps, music), prefs.homeApps)
        prefs.replaceHomeApp(maps, null)
        assertEquals(listOf(music), prefs.homeApps)
    }

    @Test fun `shortcuts and other profiles are told apart`() {
        val shortcut = AppTarget("org.example.maps", null, "home", 0L)
        val work = AppTarget("org.example.maps", "Main", null, 10L)
        listOf(maps, shortcut, work).forEach { assertTrue(prefs.addHomeApp(it)) }
        assertEquals(listOf(maps, shortcut, work), Prefs(context).homeApps)
    }
}
