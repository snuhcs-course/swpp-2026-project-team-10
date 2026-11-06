// AI-generated with Claude Code, 2026-10-29, reviewed by Joonhyung Han and Dongje Park
package com.lastpenguin.pix.camera

import android.content.Context
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Where a photo goes (FR-1.5): a JPEG named `Pix_<date>_<time>_<uuid>.jpg` under Pictures/Pix in the shared
 * images collection, through the app's content resolver; two photos never share a name; a front camera's photo is
 * saved flipped left to right, as its preview shows it, and a rear camera's is not (FR-1.14).
 */
@Suppress("RestrictedApi")
@RunWith(AndroidJUnit4::class)
class PhotoSaverTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val saver = PhotoSaver(context)

    @Test
    fun aJpegUnderPicturesPix_inTheImagesCollection() {
        val options = saver.outputOptions()
        val values = options.contentValues!!

        val name = values.getAsString(MediaStore.Images.Media.DISPLAY_NAME)
        assertTrue(name, Regex("""Pix_\d{8}_\d{6}_\d{3}_[0-9a-f-]{36}\.jpg""").matches(name))
        assertEquals("image/jpeg", values.getAsString(MediaStore.Images.Media.MIME_TYPE))
        assertEquals("Pictures/Pix", values.getAsString(MediaStore.Images.Media.RELATIVE_PATH))
        assertEquals(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, options.saveCollection)
        assertSame(context.contentResolver, options.contentResolver)
    }

    @Test
    fun twoPhotos_neverShareAName() {
        val first = saver.outputOptions().contentValues!!.getAsString(MediaStore.Images.Media.DISPLAY_NAME)
        val second = saver.outputOptions().contentValues!!.getAsString(MediaStore.Images.Media.DISPLAY_NAME)

        assertNotEquals(first, second)
    }

    @Test
    fun aFrontCameraPhoto_isSavedMirrored_andARearCameraPhotoIsNot() {
        assertTrue(saver.outputOptions(mirrored = true).metadata.isReversedHorizontal)
        assertFalse(saver.outputOptions().metadata.isReversedHorizontal)
    }
}
