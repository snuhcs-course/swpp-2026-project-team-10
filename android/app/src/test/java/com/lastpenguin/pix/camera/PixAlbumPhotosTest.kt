// AI-generated with Claude Code, 2026-11-18, reviewed by Jaewan Park
package com.lastpenguin.pix.camera

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/*
 * The photos in Pix's album (FR-1.6, Design 2.6.5), through a small provider in place of MediaStore.
 * The query (state-based): Pix's own album, published photos only, asked newest first and kept oldest first, so the
 * newest is the last; an empty album has none.
 * Changes (state-based): a change anywhere in MediaStore reads it again, so a new photo joins at the end and a deleted
 * one leaves, the newest too.
 * Failures (fault injection): a query that throws gives none, not a crash, and the next change reads again.
 * Delete (state-based, fault injection): the photo's row goes from MediaStore, which then tells of the change; a photo
 * already gone counts as deleted; one MediaStore refuses is not, with no crash.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PixAlbumPhotosTest {

    /** Answers each query with [rows], the ids as MediaStore would sort them, and records what was asked. */
    class FakeMediaStore : ContentProvider() {
        class Query(val uri: Uri, val selection: String?, val args: List<String>, val sortOrder: String?)

        var rows: List<Long> = emptyList()
        var fails = false
        var refusesDelete = false
        val queries = mutableListOf<Query>()
        val deleted = mutableListOf<Uri>()

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            args: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            if (fails) throw SecurityException("Not this app's photos")
            queries += Query(uri, selection, args.orEmpty().toList(), sortOrder)
            return MatrixCursor(arrayOf(MediaStore.Images.Media._ID)).apply { rows.forEach { addRow(arrayOf(it)) } }
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0

        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int {
            if (refusesDelete) throw SecurityException("Another app's photo")
            val id = uri.lastPathSegment!!.toLong()
            if (id !in rows) return 0
            deleted += uri
            rows = rows - id
            return 1
        }

        override fun getType(uri: Uri): String? = null
    }

    private lateinit var store: FakeMediaStore
    private val resolver: ContentResolver get() = ApplicationProvider.getApplicationContext<Context>().contentResolver

    @Before
    fun setup() {
        store = Robolectric.setupContentProvider(FakeMediaStore::class.java, MediaStore.AUTHORITY)
    }

    private fun TestScope.album(): PixAlbumPhotos {
        val album = PixAlbumPhotos(resolver, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        return album
    }

    private fun photo(id: Long): Uri = Uri.parse("content://media/external/images/media/$id")

    /** What MediaStore tells an app when one of its rows changes. */
    private fun TestScope.changed(id: Long) {
        resolver.notifyChange(photo(id), null)
        runCurrent()
    }

    @Test
    fun `the photos are Pix's published ones, oldest first, the newest last`() = runTest {
        store.rows = listOf(12, 11, 10)

        val album = album()

        assertEquals(listOf(photo(10), photo(11), photo(12)), album.photos.value)
        val query = store.queries.last()
        assertEquals(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, query.uri)
        assertTrue(query.selection, query.selection!!.contains(MediaStore.Images.Media.RELATIVE_PATH))
        assertTrue(query.args.toString(), "Pictures/Pix/" in query.args)
        // Only a published photo: one still pending has no bytes to show yet.
        assertTrue(query.selection, query.selection!!.contains("${MediaStore.MediaColumns.IS_PENDING} = 0"))
        assertTrue(query.sortOrder, query.sortOrder!!.startsWith("${MediaStore.Images.Media.DATE_ADDED} DESC"))
    }

    @Test
    fun `an empty album has none`() = runTest {
        assertEquals(emptyList<Uri>(), album().photos.value)
    }

    @Test
    fun `a change reads the album again, for a new photo and for one deleted`() = runTest {
        store.rows = listOf(11, 10)
        val album = album()

        store.rows = listOf(12, 11, 10)
        changed(12)
        assertEquals(listOf(photo(10), photo(11), photo(12)), album.photos.value)

        store.rows = listOf(12, 10)
        changed(11)
        assertEquals(listOf(photo(10), photo(12)), album.photos.value)

        store.rows = emptyList()
        changed(10)
        assertEquals(emptyList<Uri>(), album.photos.value)
    }

    @Test
    fun `a query that fails gives none, and the next change reads again`() = runTest {
        store.fails = true
        val album = album()
        assertEquals(emptyList<Uri>(), album.photos.value)

        store.fails = false
        store.rows = listOf(11)
        changed(11)

        assertEquals(listOf(photo(11)), album.photos.value)
    }

    @Test
    fun `a photo deleted goes from MediaStore, and the album loses it at the change MediaStore tells of`() = runTest {
        store.rows = listOf(11, 10)
        val album = album()

        assertTrue(album.delete(photo(11)))
        assertEquals(listOf(photo(11)), store.deleted)
        changed(11)

        assertEquals(listOf(photo(10)), album.photos.value)
    }

    @Test
    fun `a photo already gone counts as deleted`() = runTest {
        store.rows = listOf(10)
        val album = album()

        assertTrue(album.delete(photo(11)))
    }

    @Test
    fun `a delete that MediaStore refuses is not done, and does not crash`() = runTest {
        store.rows = listOf(10)
        val album = album()
        store.refusesDelete = true

        assertFalse(album.delete(photo(10)))
        assertEquals(listOf(photo(10)), album.photos.value)
    }
}
