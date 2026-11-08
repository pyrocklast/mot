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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppNameSizePreferenceTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val raw get() = context.getSharedPreferences("mot", Context.MODE_PRIVATE)

    @Before fun clear() { raw.edit().clear().commit() }

    @Test fun defaultIsOneHundredPercent() {
        assertEquals(1.0f, Prefs(context).appNameScale, 0.001f)
    }

    @Test fun existingInRangeValuesStayUnchanged() {
        raw.edit().putFloat("app_name_scale", 1.5f).commit()
        assertEquals(1.5f, Prefs(context).appNameScale, 0.001f)
    }

    @Test fun oldExtremeSizesAreConstrainedToTheNewSupportedRange() {
        raw.edit().putFloat("app_name_scale", 0.5f).commit()
        assertEquals(0.8f, Prefs(context).appNameScale, 0.001f)
        raw.edit().putFloat("app_name_scale", 2.2f).commit()
        assertEquals(1.8f, Prefs(context).appNameScale, 0.001f)
    }

    @Test fun newValuesCannotBeStoredOutsideEightyToOneEightyPercent() {
        val prefs = Prefs(context)
        prefs.appNameScale = 0.2f
        assertEquals(0.8f, raw.getFloat("app_name_scale", -1f), 0.001f)
        prefs.appNameScale = 2.1f
        assertEquals(1.8f, raw.getFloat("app_name_scale", -1f), 0.001f)
    }
}
