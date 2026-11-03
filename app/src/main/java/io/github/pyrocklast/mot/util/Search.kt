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
     * Automatic launch is opt-in and requires at least two committed characters and one result.
     */
    fun shouldAutoOpenUniqueResult(
        query: CharSequence,
        resultCount: Int,
        enabled: Boolean,
        composing: Boolean,
    ): Boolean {
        val normalized = normalize(query)
        return enabled &&
            normalized.length >= 2 &&
            resultCount == 1 &&
            query.firstOrNull()?.isWhitespace() != true &&
            !composing
    }
}
