package io.github.pyrocklast.mot.util

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.View
import android.view.WindowInsets

/**
 * Expands Android's notification shade when the platform allows it, with a public-API fallback.
 *
 * Android has no public SDK method for opening the shade from a launcher. The direct request
 * therefore uses StatusBarManager's hidden method. If that becomes unavailable, showing the
 * status bar gives the user a native place to start the drag instead.
 */
object NotificationShade {
    private const val TAG = "NotificationShade"

    @Volatile
    private var directExpansionUnavailable = false

    /**
     * Returns true when Android accepted the direct request or the status-bar fallback was issued.
     */
    @SuppressLint("WrongConstant", "PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi")
    fun expand(context: Context, fallbackView: View): Boolean {
        if (!directExpansionUnavailable) {
            try {
                val service = context.getSystemService("statusbar")
                    ?: error("Status bar service unavailable")
                Class.forName("android.app.StatusBarManager")
                    .getMethod("expandNotificationsPanel")
                    .invoke(service)
                return true
            } catch (exception: Exception) {
                directExpansionUnavailable = true
                Log.w(TAG, "Direct notification-shade expansion is unavailable; revealing the status bar instead", exception)
            }
        }

        val controller = fallbackView.windowInsetsController ?: return false
        return runCatching {
            controller.show(WindowInsets.Type.statusBars())
            true
        }.onFailure {
            Log.w(TAG, "Unable to reveal the status bar as a notification-shade fallback", it)
        }.getOrDefault(false)
    }
}
