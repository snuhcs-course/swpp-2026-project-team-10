// AI-generated with Claude Code, 2026-10-21, reviewed by Jaewan Park
package com.lastpenguin.pix.generation

import android.graphics.Bitmap

/**
 * The JVM test stubs of android.jar cannot construct a Bitmap, and the code under test only passes bitmaps along,
 * so this allocates one without running a constructor. Every call gives a different instance.
 */
internal fun testBitmap(): Bitmap {
    val unsafeClass = Class.forName("sun.misc.Unsafe")
    val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
    return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, Bitmap::class.java) as Bitmap
}

/** Four poses as the server suggests them, in its order. */
internal val SUGGESTED = listOf(
    "Lean on the door and smile",
    "Walk toward the camera",
    "Sit on the step, hugging the knees",
    "Wave and laugh",
)

/** The set's slots for [texts], as RemotePoseGenerator names them. */
internal fun planned(texts: List<String>) = texts.mapIndexed { index, text -> PlannedPose("pose-${index + 1}", text) }
