package io.github.pyrocklast.mot.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import io.github.pyrocklast.mot.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

/**
 * Every app and pinned shortcut, in every profile, sorted by name. The list is loaded once when
 * Mot starts and again only when Android reports a change (an app installed, updated or removed,
 * shortcuts pinned or removed, a profile switched on or off), or when you rename an app.
 */
class AppRepository private constructor(private val context: Context) {

    /** Private Space, when the phone has one: its profile, and whether it's locked. */
    data class PrivateSpace(val user: UserHandle, val locked: Boolean)

    private val prefs = Prefs(context)
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val nameCache = context.getSharedPreferences("mot_name_cache", Context.MODE_PRIVATE)
    private val scope = MainScope()
    private var loadJob: Job? = null

    /** What Android last told us, before your names are applied. */
    private data class Found(
        val target: AppTarget,
        val ownLabel: String,
        val user: UserHandle,
        val profile: Profile,
        val isNew: Boolean,
    )

    private var found: List<Found> = emptyList()
    private var byKey: Map<String, AppEntry> = emptyMap()

    private val _apps = MutableStateFlow<List<AppEntry>?>(null)

    /** All apps and shortcuts, or null until the first load finishes. */
    val apps: StateFlow<List<AppEntry>?> = _apps.asStateFlow()

    private val _privateSpace = MutableStateFlow<PrivateSpace?>(null)
    val privateSpace: StateFlow<PrivateSpace?> = _privateSpace.asStateFlow()

    private fun start() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        val profileChanges = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_PROFILE_ADDED)
            addAction(Intent.ACTION_PROFILE_REMOVED)
            addAction(Intent.ACTION_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = reload(CHANGE_DELAY_MS)
        }
        ContextCompat.registerReceiver(context, receiver, profileChanges, ContextCompat.RECEIVER_NOT_EXPORTED)
        reload()
    }

    // App installs and updates send several callbacks in a row: wait a moment and load once
    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload(CHANGE_DELAY_MS)
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload(CHANGE_DELAY_MS)
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload(CHANGE_DELAY_MS)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            reload(CHANGE_DELAY_MS)

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            reload(CHANGE_DELAY_MS)

        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) =
            reload(CHANGE_DELAY_MS)
    }

    /** Asks Android for the apps again, after [delayMs]. A newer request replaces a pending one. */
    fun reload(delayMs: Long = 0) {
        loadJob?.cancel()
        loadJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            val (list, space) = withContext(Dispatchers.Default) { queryAndroid() }
            found = list
            _privateSpace.value = space
            publish()
        }
    }

    /** Your names changed: apply them without asking Android again. */
    fun namesChanged() {
        scope.launch { publish() }
    }

    private suspend fun publish() {
        val custom = prefs.customNames
        val current = found
        val entries = withContext(Dispatchers.Default) {
            val collator = Collator.getInstance()
            current.map { app ->
                AppEntry(
                    target = app.target,
                    label = custom[app.target.key] ?: app.ownLabel,
                    ownLabel = app.ownLabel,
                    user = app.user,
                    profile = app.profile,
                    isNew = app.isNew,
                )
            }.sortedWith { a, b -> collator.compare(a.label, b.label) }
        }
        byKey = entries.associateBy { it.key }
        _apps.value = entries
        rememberHomeNames()
    }

    private fun queryAndroid(): Pair<List<Found>, PrivateSpace?> {
        val result = ArrayList<Found>()
        var space: PrivateSpace? = null
        val now = System.currentTimeMillis()
        val canReadShortcuts = runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

        for (user in launcherApps.profiles) {
            try {
                val serial = userManager.getSerialNumberForUser(user)
                val isPrivate = launcherApps.getLauncherUserInfo(user)?.userType == UserManager.USER_TYPE_PROFILE_PRIVATE
                val profile = when {
                    isPrivate -> Profile.PRIVATE
                    user == Process.myUserHandle() -> Profile.PERSONAL
                    else -> Profile.WORK
                }
                if (isPrivate) {
                    val locked = userManager.isQuietModeEnabled(user)
                    space = PrivateSpace(user, locked)
                    // A locked Private Space shows only its header in the drawer
                    if (locked) continue
                }

                for (activity in launcherApps.getActivityList(null, user)) {
                    val packageName = activity.applicationInfo.packageName
                    if (packageName == BuildConfig.APPLICATION_ID) continue
                    result += Found(
                        target = AppTarget(packageName, activity.componentName.className, null, serial),
                        ownLabel = activity.label.toString(),
                        user = user,
                        profile = profile,
                        isNew = now - activity.firstInstallTime < NEW_APP_MS,
                    )
                }

                if (canReadShortcuts) {
                    val query = LauncherApps.ShortcutQuery().setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
                    launcherApps.getShortcuts(query, user)?.forEach { shortcut ->
                        val label = (shortcut.shortLabel ?: shortcut.longLabel)?.toString().orEmpty()
                        if (label.isEmpty()) return@forEach
                        result += Found(
                            target = AppTarget(shortcut.`package`, null, shortcut.id, serial),
                            ownLabel = label,
                            user = user,
                            profile = profile,
                            isNew = false,
                        )
                    }
                }
            } catch (e: Exception) {
                // One profile failing (e.g. switched off mid-query) shouldn't empty the whole list
                e.printStackTrace()
            }
        }
        return result to space
    }

    /** The app or shortcut a target points to, if it's installed and its profile is on. */
    fun find(target: AppTarget): AppEntry? {
        byKey[target.key]?.let { return it }
        if (target.isShortcut) return null
        // The app was updated and its main activity renamed: any activity of the same app will do
        return _apps.value?.firstOrNull {
            !it.target.isShortcut && it.target.packageName == target.packageName && it.target.userSerial == target.userSerial
        }
    }

    /**
     * The name to show for a target on the home screen or in settings. Before the first load, or
     * while its profile is locked or off, the last name seen is shown instead. An app that's gone
     * has no name, so its home spot shows as empty.
     */
    fun nameFor(target: AppTarget): String? {
        find(target)?.let { return it.label }
        val profileAway = _apps.value == null || isProfileAway(target.userSerial)
        return if (profileAway) nameCache.getString(target.key, null) else null
    }

    private fun isProfileAway(serial: Long): Boolean {
        val user = userManager.getUserForSerialNumber(serial) ?: return false
        return runCatching { userManager.isQuietModeEnabled(user) }.getOrDefault(false)
    }

    // Keeps the names of the apps in use, so the home screen can show them before the list loads
    private fun rememberHomeNames() {
        val targets = prefs.homeApps.filterNotNull() + listOfNotNull(prefs.swipeLeftApp, prefs.swipeRightApp)
        val names = targets.mapNotNull { target -> find(target)?.let { target.key to it.label } }
        if (names.all { (key, label) -> nameCache.getString(key, null) == label }) return
        nameCache.edit { names.forEach { (key, label) -> putString(key, label) } }
    }

    /** Locks or unlocks Private Space. Unlocking asks for your PIN or fingerprint. */
    fun setPrivateSpaceLocked(locked: Boolean) {
        val space = _privateSpace.value ?: return
        runCatching { userManager.requestQuietModeEnabled(locked, space.user) }
    }

    companion object {
        private const val CHANGE_DELAY_MS = 500L
        private const val NEW_APP_MS = 60 * 60 * 1000L

        @Volatile
        private var instance: AppRepository? = null

        fun get(context: Context): AppRepository = instance ?: synchronized(this) {
            instance ?: AppRepository(context.applicationContext).also {
                instance = it
                it.start()
            }
        }
    }
}
