package dev.omni.launcher.preset

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchTest {
    @Test
    fun emptyQueryMatchesEveryApp() {
        assertTrue(matchesAppSearch("Calendar", "com.example.calendar", ""))
        assertTrue(matchesAppSearch("Calendar", "com.example.calendar", "   "))
    }

    @Test
    fun searchesLabelsWithoutCaseSensitivity() {
        assertTrue(matchesAppSearch("Google Calendar", "com.google.calendar", "google"))
        assertTrue(matchesAppSearch("Google Calendar", "com.google.calendar", "CALENDAR"))
    }

    @Test
    fun searchesPackageNames() {
        assertTrue(matchesAppSearch("Maps", "com.google.android.apps.maps", "android.apps"))
    }

    @Test
    fun rejectsNonMatchingQueries() {
        assertFalse(matchesAppSearch("Maps", "com.google.android.apps.maps", "music"))
    }
}
