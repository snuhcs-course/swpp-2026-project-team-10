// AI-generated with Claude Code, 2026-11-24, reviewed by Dongje Park
package com.lastpenguin.pix.generation

/**
 * An option of Generate poses (FR-4.15) in the description's text. Choosing an option writes its words into the
 * description, after a comma when there is text already, and choosing it again takes them out, so the description
 * alone says what was chosen: it is what both phones show, what the filter reads, and what the history keeps, as if
 * typed. An option is in the description when one of its parts, cut at the commas, is the option's words and nothing
 * else, in any letter case; the words inside a longer word or a sentence are the user's own text.
 */
object PoseOptions {

    /** Whether one of [description]'s parts is [option]. */
    fun has(
        description: String,
        option: String,
    ): Boolean = parts(description).any { it.equals(option, ignoreCase = true) }

    /**
     * [description] with [option] taken out, when it has it, or added at its end. Null when adding it would pass [max]
     * characters, so an option that does not fit cannot be chosen.
     */
    fun toggle(description: String, option: String, max: Int = DESCRIPTION_MAX_LENGTH): String? {
        if (has(description, option)) {
            return parts(description).filterNot { it.equals(option, ignoreCase = true) }.joinToString(SEPARATOR)
        }
        // What was typed stays as typed, but for a comma left at its end.
        val typed = description.trimEnd().trimEnd(',').trimEnd()
        val added = if (typed.isBlank()) option else typed + SEPARATOR + option
        return added.takeIf { it.length <= max }
    }

    private fun parts(description: String): List<String> =
        description.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private const val SEPARATOR = ", "
}
