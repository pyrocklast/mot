package io.github.pyrocklast.mot.util

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import io.github.pyrocklast.mot.R
import io.github.pyrocklast.mot.data.AppTarget

/** Android's own apps, used when you haven't chosen one. */
enum class DefaultApp { CLOCK, CALENDAR, CAMERA, PHONE }

/** Opening apps, shortcuts and Android's screens. */
object Apps {

    fun userFor(context: Context, serial: Long): UserHandle? =
        context.getSystemService(UserManager::class.java).getUserForSerialNumber(serial)

    /** Opens an app or shortcut, growing out of [from] when given. Returns false if it's gone. */
    fun open(context: Context, target: AppTarget, from: View? = null): Boolean {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val user = userFor(context, target.userSerial)
        if (user == null) {
            toast(context, R.string.app_not_found)
            return false
        }
        val bounds = from?.let { screenRect(it) }
        val options = from?.let { ActivityOptions.makeScaleUpAnimation(it, 0, 0, it.width, it.height).toBundle() }
        return try {
            val shortcutId = target.shortcutId
            if (shortcutId != null) {
                launcherApps.startShortcut(target.packageName, shortcutId, bounds, options, user)
            } else {
                val component = componentFor(context, target, user)
                if (component == null) {
                    toast(context, R.string.app_not_found)
                    return false
                }
                launcherApps.startMainActivity(component, user, bounds, options)
            }
            true
        } catch (e: Exception) {
            // Not found, profile off, or not the default launcher (needed for shortcuts)
            e.printStackTrace()
            toast(context, R.string.cant_open)
            false
        }
    }

    /** Opens Android's default app of a kind: alarms, calendar, camera or dialer. */
    fun openDefault(context: Context, app: DefaultApp) {
        val intent = when (app) {
            DefaultApp.CLOCK -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            DefaultApp.CALENDAR -> Intent(
                Intent.ACTION_VIEW,
                CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
            )
            DefaultApp.CAMERA -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            DefaultApp.PHONE -> Intent(Intent.ACTION_DIAL)
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            if (app == DefaultApp.CALENDAR) {
                // Some calendars don't handle the "today" link, only being opened
                val calendar = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
                runCatching { context.startActivity(calendar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { toast(context, R.string.cant_open) }
            } else toast(context, R.string.cant_open)
        }
    }

    /** Android's App info screen for the app (or the shortcut's app). */
    fun openAppInfo(context: Context, target: AppTarget) {
        val user = userFor(context, target.userSerial) ?: return toast(context, R.string.app_not_found)
        val component = componentFor(context, target.copy(shortcutId = null), user)
            ?: return toast(context, R.string.app_not_found)
        runCatching {
            context.getSystemService(LauncherApps::class.java).startAppDetailsActivity(component, user, null, null)
        }.onFailure { toast(context, R.string.cant_open) }
    }

    /**
     * Asks Android to uninstall an app. Apps in another profile are uninstalled from their App
     * info screen, since Android only lets Mot ask for its own profile.
     */
    fun uninstall(context: Context, target: AppTarget) {
        val user = userFor(context, target.userSerial)
        if (user != Process.myUserHandle()) return openAppInfo(context, target)
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", target.packageName, null))
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { openAppInfo(context, target) }
    }

    /** Removes a pinned shortcut from the drawer (and from wherever else it's pinned). */
    fun unpin(context: Context, target: AppTarget) {
        val id = target.shortcutId ?: return
        val user = userFor(context, target.userSerial) ?: return
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        runCatching {
            val query = LauncherApps.ShortcutQuery()
                .setPackage(target.packageName)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            val stillPinned = launcherApps.getShortcuts(query, user).orEmpty().map { it.id }.filter { it != id }
            launcherApps.pinShortcuts(target.packageName, stillPinned, user)
        }.onFailure { toast(context, R.string.cant_open) }
    }

    /** Pulls down the notification shade. Android has no public way, so this calls its hidden one. */
    @SuppressLint("WrongConstant", "PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi")
    fun expandNotifications(context: Context) {
        runCatching {
            val service = context.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
        }
    }

    fun isDefaultLauncher(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)

    /** Android's own dialog for making Mot the Home app, or null when it can't be shown. */
    fun homeRoleRequest(context: Context): Intent? {
        val roles = context.getSystemService(RoleManager::class.java)
        if (!roles.isRoleAvailable(RoleManager.ROLE_HOME) || roles.isRoleHeld(RoleManager.ROLE_HOME)) return null
        return roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }

    /** Android's Default apps > Home app screen. */
    fun openHomeSettings(context: Context) {
        runCatching {
            context.startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { toast(context, R.string.cant_open) }
    }

    /** Private Space's own settings (to set it up, or manage it), or else Security settings, where it lives. */
    fun openPrivateSpaceSettings(context: Context) {
        val screens = listOf("android.settings.PRIVATE_SPACE_SETTINGS", android.provider.Settings.ACTION_SECURITY_SETTINGS)
        val opened = screens.any { action ->
            runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
        if (!opened) toast(context, R.string.cant_open)
    }

    private fun componentFor(context: Context, target: AppTarget, user: UserHandle): ComponentName? {
        val activities = context.getSystemService(LauncherApps::class.java).getActivityList(target.packageName, user)
        val activity = activities.firstOrNull { it.componentName.className == target.className } ?: activities.firstOrNull()
        return activity?.componentName
    }

    private fun screenRect(view: View): Rect {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    fun toast(context: Context, text: Int) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
