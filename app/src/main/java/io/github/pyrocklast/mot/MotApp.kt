package io.github.pyrocklast.mot

import android.app.Application
import com.google.android.material.color.DynamicColors
import io.github.pyrocklast.mot.data.AppRepository
import io.github.pyrocklast.mot.data.Prefs

class MotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Material You colours for settings, menus and dialogs
        DynamicColors.applyToActivitiesIfAvailable(this)
        Prefs(this).migrate()
        // Start loading apps right away, so the drawer is ready by the time it's opened
        AppRepository.get(this)
    }
}
