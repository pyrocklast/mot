package io.github.pyrocklast.mot

import io.github.pyrocklast.mot.util.Search
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {

    private fun matches(name: String, query: String) = Search.matches(Search.normalize(name), Search.normalize(query))

    @Test
    fun `ignores case and accents`() {
        assertTrue(matches("Café", "cafe"))
        assertTrue(matches("Éléments", "ELEM"))
    }

    @Test
    fun `ignores spaces and separators`() {
        assertTrue(matches("Google Maps", "googlemaps"))
        assertTrue(matches("Google Maps", "google ma"))
        assertTrue(matches("Wi-Fi Analyzer", "wifi"))
        assertTrue(matches("Lyon's Bakery", "lyons"))
    }

    @Test
    fun `finds text anywhere in the name`() {
        assertTrue(matches("Calculator", "cul"))
        assertFalse(matches("Calculator", "clock"))
    }

    @Test
    fun `an empty search shows everything`() {
        assertTrue(matches("Anything", ""))
        assertTrue(matches("Anything", "   "))
    }

    @Test
    fun `normalises to plain lower case`() {
        assertEquals("cafecreme", Search.normalize("Café Crème"))
    }

    @Test
    fun `splits names into prepared words`() {
        assertEquals(listOf("google", "maps"), Search.words("Google Maps"))
        assertEquals(listOf("wi", "fi", "analyzer"), Search.words("Wi-Fi  Analyzer"))
        assertEquals(listOf("cafe", "creme"), Search.words("Café Crème"))
    }

    private fun ranked(query: String, vararg names: String): List<String> {
        val q = Search.normalize(query)
        return names.filter { matches(it, query) }
            .sortedBy { Search.rank(Search.normalize(it), Search.words(it), q) }
    }

    @Test
    fun `names starting with the search come first, then words starting with it`() {
        // Alphabetical to begin with, as the drawer is
        assertEquals(
            listOf("Maps", "Google Maps", "Gmail"),
            ranked("ma", "Gmail", "Google Maps", "Maps", "Messages"),
        )
        assertEquals(listOf("Calendar", "Calculator", "Pascal"), ranked("cal", "Calendar", "Calculator", "Pascal"))
        assertEquals(listOf("Google Maps"), ranked("google ma", "Gmail", "Google Maps", "Maps"))
    }
}
