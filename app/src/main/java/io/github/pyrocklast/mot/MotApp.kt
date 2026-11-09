package io.github.pyrocklast.mot

import android.app.Application
import io.github.pyrocklast.mot.data.AppRepository
import io.github.pyrocklast.mot.data.Prefs

class MotApp : Application() {

    /**
     * Counts crashes before letting Android deal with them as usual, so a launcher that can't
     * draw itself can put its look back to the defaults rather than crashing for ever.
     */
    private fun countCrashes(prefs: Prefs) {
        val android = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { prefs.recordCrash() }
            android?.uncaughtException(thread, error)
        }
    }

    override fun onCreate() {
        super.onCreate()
        countCrashes(Prefs(this))
        // Start loading apps right away, so the drawer is ready by the time it's opened
        AppRepository.get(this)
    }
}
