package io.github.pyrocklast.mot.data

import android.os.UserHandle
import io.github.pyrocklast.mot.util.Search

/** Which profile an app belongs to. Work covers any profile other than yours and Private Space. */
enum class Profile { PERSONAL, WORK, PRIVATE }

/** An app or pinned shortcut as the drawer and home screen show it. */
data class AppEntry(
    val target: AppTarget,
    /** The name shown: the one you chose, or else the app's own. */
    val label: String,
    /** The app's own name. */
    val ownLabel: String,
    val user: UserHandle,
    val profile: Profile,
    /** When the app was installed (0 for shortcuts); the drawer marks it for a day. */
    val installedAt: Long,
) {
    /** Installed in the last 24 hours. */
    fun isNew(now: Long): Boolean = installedAt > 0 && now - installedAt in 0 until NEW_FOR_MS

    val key: String get() = target.key

    /** For your name for it and hiding it; see [AppTarget.appKey]. */
    val appKey: String get() = target.appKey

    /** The name prepared for search: lower case, no accents, no separators. */
    val searchText: String = Search.normalize(label)

    /** Its words, prepared the same way, so search can put names whose words start with the query first. */
    val searchWords: List<String> = Search.words(label)

    private companion object {
        const val NEW_FOR_MS = 24 * 60 * 60 * 1000L
    }
}
