package io.github.pyrocklast.mot.util

import java.text.Normalizer
import java.util.Locale

/**
 * App search: case-insensitive, ignores accents and separators, so "cafe" finds "Café" and
 * "googlemaps" finds "Google Maps". Names are normalised once when the app list loads, and the
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

    /** Whether a name, already passed through [normalize], matches a query that also was. */
    fun matches(normalizedName: String, normalizedQuery: String): Boolean =
        normalizedQuery.isEmpty() || normalizedName.contains(normalizedQuery)
}
