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

internal val FOUR_TEMPLATES = listOf(
    PoseTemplate("hands_on_hips", "Hands on hips"),
    PoseTemplate("wave", "Wave"),
    PoseTemplate("walking", "Walking"),
    PoseTemplate("arms_crossed", "Arms crossed"),
)
