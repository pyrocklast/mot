package io.github.pyrocklast.mot

import android.app.Application
import android.content.Context
import io.github.pyrocklast.mot.data.Prefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Exercises the actual persistent preference fallback, not only its helper function. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StatusBarMigrationIntegrationTest {
    private lateinit var context: Context

    @Before fun resetState() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mot", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("mot_names", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun freshInstallShowsStatusBar() {
        assertTrue(Prefs(context).showStatusBar)
    }

    @Test fun existingInstallWithNoExplicitSettingKeepsOldHiddenDefault() {
        context.getSharedPreferences("mot", Context.MODE_PRIVATE).edit()
            .putBoolean("settings_opened", true).commit()
        assertFalse(Prefs(context).showStatusBar)
    }

    @Test fun explicitlyHiddenStatusBarRemainsHiddenOnUpgrade() {
        context.getSharedPreferences("mot", Context.MODE_PRIVATE).edit()
            .putBoolean("show_status_bar", false).commit()
        assertFalse(Prefs(context).showStatusBar)
    }

    @Test fun explicitlyVisibleStatusBarRemainsVisibleOnUpgrade() {
        context.getSharedPreferences("mot", Context.MODE_PRIVATE).edit()
            .putBoolean("show_status_bar", true).commit()
        assertTrue(Prefs(context).showStatusBar)
    }

    @Test fun installDefaultIsStableAfterOtherPreferencesAreSaved() {
        assertTrue(Prefs(context).showStatusBar)
        context.getSharedPreferences("mot", Context.MODE_PRIVATE).edit()
            .putBoolean("settings_opened", true).commit()
        assertTrue(Prefs(context).showStatusBar)
    }

    @Test fun upgradeDefaultRemainsHiddenAfterMorePreferencesAreSaved() {
        context.getSharedPreferences("mot", Context.MODE_PRIVATE).edit()
            .putBoolean("settings_opened", true).commit()
        assertFalse(Prefs(context).showStatusBar)
        context.getSharedPreferences("mot", Context.MODE_PRIVATE).edit()
            .putInt("home_app_count", 3).commit()
        assertFalse(Prefs(context).showStatusBar)
    }
}
