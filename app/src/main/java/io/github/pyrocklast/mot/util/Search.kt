package io.github.pyrocklast.mot.util

import java.text.Normalizer
import java.util.Locale

/**
 * App search: case-insensitive, ignores accents and separators, so "cafe" finds "Café" and
 * "googlemaps" finds "Google Maps". Names are prepared once when the app list loads, and the
 * query once per keystroke.
 */
object Search {
    private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val separators = Regex("[-_+,.`'’\\s\\p{Z}]")

    fun normalize(text: CharSequence): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(diacritics, "")
            .replace(separators, "")
            .lowercase(Locale.ROOT)

    /** Each word of a name, normalised: "Google Maps" gives "google" and "maps". */
    fun words(text: CharSequence): List<String> =
        text.split(separators).map(::normalize).filter { it.isNotEmpty() }

    /** Whether a name, already passed through [normalize], matches a query that also was. */
    fun matches(normalizedName: String, normalizedQuery: String): Boolean =
        normalizedQuery.isEmpty() || normalizedName.contains(normalizedQuery)

    /**
     * How well a name matches, best first: 0 when the name starts with the query ("ma" in Maps),
     * 1 when one of its words does ("ma" in Google Maps), 2 for anywhere else ("ma" in Gmail).
     * Matches are sorted by this, and alphabetically within each rank.
     */
    fun rank(normalizedName: String, words: List<String>, normalizedQuery: String): Int = when {
        normalizedName.startsWith(normalizedQuery) -> 0
        words.any { it.startsWith(normalizedQuery) } -> 1
        else -> 2
    }

    /**
     * Whether typing has left one app to open. It takes two letters, so a single one can't launch
     * something you didn't mean, and a search that starts with a space never does. [composing] is
     * a word a Chinese, Japanese or Korean keyboard hasn't finished yet.
     */
    fun opensOnlyMatch(query: CharSequence, matches: Int, composing: Boolean): Boolean =
        matches == 1 &&
            !composing &&
            query.firstOrNull()?.isWhitespace() != true &&
            normalize(query).length >= 2

    /** The auto-open behaviour is an explicit opt-in; matching itself always works without it. */
    fun shouldAutoOpenOnlyMatch(
        enabled: Boolean,
        query: CharSequence,
        matches: Int,
        composing: Boolean,
    ): Boolean = enabled && opensOnlyMatch(query, matches, composing)
}
