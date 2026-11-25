// AI-generated with Claude Code, 2026-11-26, reviewed by Jaewan Park
package com.lastpenguin.pix.ui.guide

/**
 * Where each list of a grid was left, by its key [K], as a place [S], for a grid whose lists change as tabs do (the
 * library's tabs and situations, #297, #320). The grid shows the list its adapter last committed, which a fast change
 * of tab can leave behind the list last asked for: where the grid is is kept under the list it shows, never under one
 * asked for and not shown yet.
 */
internal class ListPlaces<K : Any, S : Any> {
    /** A list the grid turned to, now committed: shown at [place], or at its top when it has none yet. */
    class Arrival<S>(val place: S?)

    private val left = mutableMapOf<K, S?>()
    private var asked: K? = null
    private var turning = false

    /** The list the grid shows: the one its adapter last committed; null before the first. */
    var shown: K? = null
        private set

    /**
     * [list] is asked for, with the grid now at [here]: true when it is another list than the last asked for, so the
     * grid turns to it. [here] is kept under the list the grid shows.
     */
    fun ask(list: K, here: () -> S?): Boolean {
        val turns = asked != null && asked != list
        asked = list
        if (turns) {
            shown?.let { left[it] = here() }
            turning = true
        }
        return turns
    }

    /**
     * The adapter committed [list], the newest asked for, since an adapter commits only its newest: the place to show it
     * at when the grid turned to it, or null when it did not.
     */
    fun committed(list: K): Arrival<S>? {
        shown = list
        if (!turning) return null
        turning = false
        return Arrival(left[list])
    }

    /** Forgets every list and place, for a grid made anew. */
    fun clear() {
        left.clear()
        asked = null
        shown = null
        turning = false
    }
}
