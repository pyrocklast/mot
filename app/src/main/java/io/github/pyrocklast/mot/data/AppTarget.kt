package io.github.pyrocklast.mot.data

/**
 * Something Mot can open: an app's activity, or a pinned shortcut. Home apps, swipe apps and the
 * clock and calendar apps are all stored as one of these, so one piece of code saves, finds and
 * opens all of them.
 *
 * [userSerial] identifies the profile (personal, work, Private Space) and stays the same across
 * reboots, unlike the UserHandle itself.
 */
data class AppTarget(
    val packageName: String,
    val className: String?,
    val shortcutId: String?,
    val userSerial: Long,
) {
    val isShortcut: Boolean get() = shortcutId != null

    /**
     * Identifies the app for your name for it and for hiding it: the app itself rather than its
     * activity, which some apps change in updates (e.g. when switching icons). For a shortcut, the
     * shortcut.
     */
    val appKey: String
        get() = if (shortcutId != null) key else "p:$packageName:$userSerial"

    /** Identifies this exact target: which activity, or which shortcut. */
    val key: String
        get() = if (shortcutId != null) "s:$packageName:$userSerial:$shortcutId"
        else "a:$packageName/$className:$userSerial"

    /** For saving in preferences; [decode] reads it back. */
    fun encode(): String =
        listOf(packageName, className.orEmpty(), shortcutId.orEmpty(), userSerial.toString()).joinToString(SEPARATOR)

    companion object {
        // A control character, so it can't appear in package names, class names or shortcut ids
        private const val SEPARATOR = "\u001F"

        fun decode(value: String?): AppTarget? {
            val parts = value?.split(SEPARATOR) ?: return null
            if (parts.size != 4 || parts[0].isEmpty()) return null
            val serial = parts[3].toLongOrNull() ?: return null
            return AppTarget(
                packageName = parts[0],
                className = parts[1].ifEmpty { null },
                shortcutId = parts[2].ifEmpty { null },
                userSerial = serial,
            )
        }
    }
}
