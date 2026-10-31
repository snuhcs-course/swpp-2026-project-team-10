// AI-generated with Claude Code, 2026-10-31, reviewed by Joonhyung Han
package com.lastpenguin.pix.camera

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/*
 * The subject's "Pix" album (FR-6.14, Design 2.6.5), through a small provider in place of MediaStore.
 * The row (state-based): inserted pending, in Pictures/Pix, named after `takenAt` and the photo, with `DATE_TAKEN`, then
 * the bytes, then no longer pending.
 * Failures (fault injection): no row, no stream, and a refused insert are failures, not crashes, and leave no row.
 */
@RunWith(AndroidJUnit4::class)
class PhotoAlbumTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** Records what the album does, with its rows as files. */
    private class FakeMediaStore : ContentProvider() {
        val inserted = mutableListOf<ContentValues>()
        val updates = mutableListOf<ContentValues>()
        val deleted = mutableListOf<Uri>()
        var out: File? = null
        var refuseRow = false
        var throwOnInsert = false

        override fun onCreate() = true

        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            if (throwOnInsert) throw IllegalStateException("No space for the row")
            if (refuseRow) return null
            inserted += ContentValues(values)
            return Uri.withAppendedPath(uri, "1")
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
            val file = out ?: return null
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
        }

        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int {
            updates += ContentValues(values)
            return 1
        }

        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int {
            deleted += uri
            return 1
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            args: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null

        override fun getType(uri: Uri): String? = null
    }

    private lateinit var store: FakeMediaStore
    private lateinit var photo: File
    private val bytes = ByteArray(2_000) { it.toByte() }

    @Before
    fun setup() {
        store = Robolectric.setupContentProvider(FakeMediaStore::class.java, MediaStore.AUTHORITY)
        photo = folder.newFile("photo.part").apply { writeBytes(bytes) }
    }

    private fun album() = PhotoSaver(ApplicationProvider.getApplicationContext())

    @Test
    fun `a photo goes into a pending row in the Pix album and is then published`() {
        val out = folder.newFile("row.jpg")
        store.out = out

        val result = album().save(photo, "p1", takenAt = 1_000)

        assertTrue(result.isSuccess)
        val row = store.inserted.single()
        assertEquals("${Environment.DIRECTORY_PICTURES}/Pix", row.getAsString(MediaStore.Images.Media.RELATIVE_PATH))
        assertEquals("image/jpeg", row.getAsString(MediaStore.Images.Media.MIME_TYPE))
        assertEquals(1_000L, row.getAsLong(MediaStore.Images.Media.DATE_TAKEN))
        assertEquals(1, row.getAsInteger(MediaStore.Images.Media.IS_PENDING))
        val name = row.getAsString(MediaStore.Images.Media.DISPLAY_NAME)
        assertTrue(name, name.startsWith("Pix_") && name.endsWith("_p1.jpg"))
        assertEquals(0, store.updates.single().getAsInteger(MediaStore.Images.Media.IS_PENDING))
        assertArrayEquals(bytes, out.readBytes())
        assertTrue(store.deleted.isEmpty())
    }

    @Test
    fun `no row is a failure`() {
        store.refuseRow = true

        val result = album().save(photo, "p1", takenAt = 1)

        assertTrue(result.exceptionOrNull() is IOException)
        assertTrue(store.deleted.isEmpty())
    }

    @Test
    fun `a refused insert is a failure and not a crash`() {
        store.throwOnInsert = true

        val result = album().save(photo, "p1", takenAt = 1)

        assertFalse(result.isSuccess)
    }

    @Test
    fun `no stream removes the row`() {
        store.out = null

        val result = album().save(photo, "p1", takenAt = 1)

        assertFalse(result.isSuccess)
        assertEquals(1, store.deleted.size)
        assertNull(store.updates.firstOrNull())
    }
}
