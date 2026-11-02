package io.github.pyrocklast.mot.launcher

import android.app.Activity
import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.Toast
import io.github.pyrocklast.mot.R

/**
 * Accepts "Add to home screen" shortcuts from other apps (a website from the browser, a contact,
 * a chat). They appear in the drawer, and can be put on the home screen like apps.
 * Widgets aren't supported, so they aren't offered here.
 */
class PinShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = getSystemService(LauncherApps::class.java).getPinItemRequest(intent)
        val added = request != null &&
                request.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT &&
                request.isValid &&
                runCatching { request.accept() }.getOrDefault(false)
        Toast.makeText(this, if (added) R.string.shortcut_added else R.string.shortcut_not_added, Toast.LENGTH_SHORT).show()
        finish()
    }
}
