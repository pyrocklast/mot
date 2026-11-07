package io.github.pyrocklast.mot.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.LauncherUserInfo
import android.content.pm.ShortcutInfo
import android.os.Build
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

/** The entry point is hidden only when Android requests it and the Private Space is locked. */
internal fun isPrivateSpaceEntryPointVisible(locked: Boolean, entrypointHidden: Boolean): Boolean =
    !locked || !entrypointHidden

/**
 * Every app and pinned shortcut, in every profile, sorted by name. The list is loaded once when
 * mot launcher starts and again only when Android reports a change (an app installed, updated or removed,
 * shortcuts pinned or removed, a profile switched on or off), or when you rename an app.
 */
class AppRepository private constructor(private val context: Context) {

    /** Private Space, including Android's preference to hide its entry point while locked. */
    data class PrivateSpace(
        val user: UserHandle,
        val serial: Long,
        val locked: Boolean,
        val entrypointHidden: Boolean,
    ) {
        val entrypointVisible: Boolean
            get() = isPrivateSpaceEntryPointVisible(locked, entrypointHidden)
    }

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
        val installedAt: Long,
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

        // Android 16 reports changes such as "hide Private Space when locked" here.
        override fun onUserConfigChanged(launcherUserInfo: LauncherUserInfo) = reload()
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
                    label = custom[app.target.appKey] ?: app.ownLabel,
                    ownLabel = app.ownLabel,
                    user = app.user,
                    profile = app.profile,
                    installedAt = app.installedAt,
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
        val canReadShortcuts = runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

        for (user in launcherApps.profiles) {
            try {
                val serial = userManager.getSerialNumberForUser(user)
                val launcherUserInfo = launcherApps.getLauncherUserInfo(user)
                val isPrivate = launcherUserInfo?.userType == UserManager.USER_TYPE_PROFILE_PRIVATE
                val profile = when {
                    isPrivate -> Profile.PRIVATE
                    user == Process.myUserHandle() -> Profile.PERSONAL
                    else -> Profile.WORK
                }
                if (isPrivate) {
                    val locked = userManager.isQuietModeEnabled(user)
                    val entrypointHidden = if (Build.VERSION.SDK_INT >= 36) {
                        runCatching {
                            launcherUserInfo?.userConfig?.getBoolean(
                                LauncherUserInfo.PRIVATE_SPACE_ENTRYPOINT_HIDDEN, false
                            ) ?: false
                        }.getOrDefault(true)
                    } else false
                    space = PrivateSpace(user, serial, locked, entrypointHidden)
                    // A locked Private Space has no app entries, whether its entry point is shown or hidden
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
                        installedAt = activity.firstInstallTime,
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
                            installedAt = 0,
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
     * while a work profile is paused, the last name seen is shown instead. An app that's gone has
     * no name, so its home spot shows as empty, and so does one from a locked Private Space:
     * its names are never kept or shown while it's locked.
     */
    fun nameFor(target: AppTarget): String? {
        find(target)?.let { return it.label }
        if (isLockedPrivateSpace(target.userSerial)) return null
        val profileAway = _apps.value == null || isProfileAway(target.userSerial)
        return if (profileAway) nameCache.getString(target.key, null) else null
    }

    /** Whether the target's profile is paused (work) or locked (Private Space), so it can't open until it's back. */
    fun isProfileAway(serial: Long): Boolean {
        val user = userManager.getUserForSerialNumber(serial) ?: return false
        return runCatching { userManager.isQuietModeEnabled(user) }.getOrDefault(false)
    }

    /** Whether the target is in Private Space while it's locked. */
    fun isLockedPrivateSpace(serial: Long): Boolean = _privateSpace.value?.let { it.locked && it.serial == serial } == true

    // Keeps the names of the apps in use, so the home screen can show them before the list loads
    // and while a work profile is paused. Private Space's names are never kept.
    private fun rememberHomeNames() {
        val targets = prefs.homeApps.filterNotNull() + listOfNotNull(prefs.swipeLeftApp, prefs.swipeRightApp)
        val names = HashMap<String, String>()
        for (target in targets) {
            val entry = find(target)
            when {
                entry != null -> if (entry.profile != Profile.PRIVATE) names[target.key] = entry.label
                isLockedPrivateSpace(target.userSerial) -> Unit
                // Paused: keep the name from before
                isProfileAway(target.userSerial) -> nameCache.getString(target.key, null)?.let { names[target.key] = it }
            }
        }
        if (nameCache.all == names) return
        nameCache.edit {
            clear()
            names.forEach { (key, label) -> putString(key, label) }
        }
    }

    /** Locks or unlocks Private Space. Unlocking asks for your PIN or fingerprint. */
    fun setPrivateSpaceLocked(locked: Boolean) {
        val space = _privateSpace.value ?: return
        runCatching { userManager.requestQuietModeEnabled(locked, space.user) }
    }

    companion object {
        private const val CHANGE_DELAY_MS = 500L

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
