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
    /** Installed in the last hour: the drawer marks it. */
    val isNew: Boolean,
) {
    val key: String get() = target.key

    /** The name prepared for search: lower case, no accents, no separators. */
    val searchText: String = Search.normalize(label)
}
