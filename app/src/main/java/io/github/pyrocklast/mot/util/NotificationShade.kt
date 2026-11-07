package io.github.pyrocklast.mot.util

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.View
import android.view.WindowInsets

/**
 * Android exposes no public API for a launcher to expand the notification shade.
 * Try the existing hidden StatusBarManager method, then offer a public, native fallback.
 */
internal object NotificationShade {
    @Volatile private var directUnavailable = false

    @SuppressLint("WrongConstant", "PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi")
    fun expand(context: Context, fallbackView: View) {
        if (!directUnavailable) {
            try {
                val service = context.getSystemService("statusbar") ?: error("No status bar service")
                Class.forName("android.app.StatusBarManager")
                    .getMethod("expandNotificationsPanel")
                    .invoke(service)
                return
            } catch (e: Exception) {
                // Reflection restrictions and OEM implementations differ. Log only once and
                // avoid the same failing lookup on every subsequent swipe.
                directUnavailable = true
                Log.w("MotNotificationShade", "Direct shade expansion unavailable; showing status bar instead", e)
            }
        }
        // Showing system bars is supported. The user can use Android's native top-edge swipe,
        // but this cannot promise to expand the whole notification shade.
        runCatching {
            fallbackView.windowInsetsController?.show(WindowInsets.Type.statusBars())
        }
    }
}
