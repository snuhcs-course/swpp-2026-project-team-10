// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Where each of a grid's lists was left (`ListPlaces`, #297, #320), on the plain JVM, with places as strings.
 * Partitions (state-based): the first list (no turn, no place); a turn to a list not shown before (its top) and back
 * (where it was left); a change within the shown list (no turn); a fast turn away and back before the first commits,
 * which keeps where the grid is under the list it shows and leaves the other list's place as it was; a grid made anew
 * (nothing kept).
 */
class ListPlacesTest {
    private val places = ListPlaces<String, String>()

    @Test
    fun aListLeft_comesBackWhereItWasLeft_andANewOneAtItsTop() {
        assertFalse(places.ask("all") { "all at its top" })
        assertNull(places.committed("all"))
        assertEquals("all", places.shown)

        assertTrue(places.ask("favorites") { "all at 9" })
        assertNull(checkNotNull(places.committed("favorites")).place)

        assertTrue(places.ask("all") { "favorites at 3" })
        assertEquals("all at 9", checkNotNull(places.committed("all")).place)

        // A change within the list shown: no turn.
        assertFalse(places.ask("all") { "all at 9" })
        assertNull(places.committed("all"))

        assertTrue(places.ask("favorites") { "all at 9" })
        assertEquals("favorites at 3", checkNotNull(places.committed("favorites")).place)
    }

    @Test
    fun aFastTurnAwayAndBack_keepsTheShownListsPlaceUnderIt_andTheOthersAsItWas() {
        places.ask("all") { null }
        places.committed("all")
        places.ask("favorites") { "all at 9" }
        places.committed("favorites")
        places.ask("all") { "favorites at 3" }
        places.committed("all")

        // To Favorites and back before Favorites' list commits: the grid still shows All, at 12.
        assertTrue(places.ask("favorites") { "all at 12" })
        assertTrue(places.ask("all") { "all at 12" })
        assertEquals("all", places.shown)
        assertEquals("all at 12", checkNotNull(places.committed("all")).place)

        // Favorites is where it was left, not where All was.
        places.ask("favorites") { "all at 12" }
        assertEquals("favorites at 3", checkNotNull(places.committed("favorites")).place)
    }

    @Test
    fun aGridMadeAnew_keepsNothing() {
        places.ask("all") { null }
        places.committed("all")
        places.ask("favorites") { "all at 9" }
        places.committed("favorites")

        places.clear()

        assertNull(places.shown)
        assertFalse(places.ask("all") { "favorites at 3" })
        assertNull(places.committed("all"))
        assertTrue(places.ask("favorites") { "all at 0" })
        assertNull(checkNotNull(places.committed("favorites")).place)
    }
}
