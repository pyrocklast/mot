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

/** App names are 24 to 48 dp. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppNameSizeTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val saved get() = context.getSharedPreferences("mot", Context.MODE_PRIVATE)

    @Before fun startEmpty() {
        saved.edit().clear().commit()
    }

    @Test fun `34 to start with`() {
        assertEquals(34, Prefs(context).appNameSize)
    }

    @Test fun `nothing outside 24 to 48 is saved or read`() {
        val prefs = Prefs(context)
        prefs.appNameSize = 12
        assertEquals(24, saved.getInt("app_name_size", -1))
        prefs.appNameSize = 60
        assertEquals(48, saved.getInt("app_name_size", -1))
        saved.edit().putInt("app_name_size", 8).commit()
        assertEquals(24, Prefs(context).appNameSize)
    }
}
