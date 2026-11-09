package io.github.pyrocklast.mot.data

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** App names are 80 to 180 % of their usual size, whatever was saved before. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppNameSizeTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val saved get() = context.getSharedPreferences("mot", Context.MODE_PRIVATE)

    @Before fun startEmpty() {
        saved.edit().clear().commit()
    }

    @Test fun `the usual size to start with`() {
        assertEquals(1f, Prefs(context).appNameScale, 0.001f)
    }

    @Test fun `a size saved by an earlier version is brought into the range`() {
        saved.edit().putFloat("app_name_scale", 0.5f).commit()
        assertEquals(0.8f, Prefs(context).appNameScale, 0.001f)
        saved.edit().putFloat("app_name_scale", 1.5f).commit()
        assertEquals(1.5f, Prefs(context).appNameScale, 0.001f)
    }

    @Test fun `nothing outside the range is saved`() {
        val prefs = Prefs(context)
        prefs.appNameScale = 0.2f
        assertEquals(0.8f, saved.getFloat("app_name_scale", -1f), 0.001f)
        prefs.appNameScale = 2.5f
        assertEquals(1.8f, saved.getFloat("app_name_scale", -1f), 0.001f)
    }
}
