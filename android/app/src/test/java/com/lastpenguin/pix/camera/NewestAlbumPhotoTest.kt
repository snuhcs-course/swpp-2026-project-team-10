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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/*
 * The newest photo in Pix's album (FR-1.6, Design 2.6.5; Iter4-A2), through a small provider in place of MediaStore.
 * The query (state-based): Pix's own album, newest first; the first row is the newest, and an empty album has none.
 * Changes (state-based): a change anywhere in MediaStore reads it again, so a new photo is the newest and a deleted
 * newest gives the one before, or none.
 * Failures (fault injection): a query that throws gives none, not a crash, and the next change reads again.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class NewestAlbumPhotoTest {

    /** Answers each query with [rows], the ids as MediaStore would sort them, and records what was asked. */
    class FakeMediaStore : ContentProvider() {
        class Query(val uri: Uri, val selection: String?, val args: List<String>, val sortOrder: String?)

        var rows: List<Long> = emptyList()
        var fails = false
        val queries = mutableListOf<Query>()

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

        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0

        override fun getType(uri: Uri): String? = null
    }

    private lateinit var store: FakeMediaStore
    private val resolver: ContentResolver get() = ApplicationProvider.getApplicationContext<Context>().contentResolver

    @Before
    fun setup() {
        store = Robolectric.setupContentProvider(FakeMediaStore::class.java, MediaStore.AUTHORITY)
    }

    private fun TestScope.album(): NewestAlbumPhoto {
        val album = NewestAlbumPhoto(resolver, backgroundScope, StandardTestDispatcher(testScheduler))
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
    fun `the newest is the first photo of Pix's album, newest first`() = runTest {
        store.rows = listOf(12, 11)

        val album = album()

        assertEquals(photo(12), album.newest.value)
        val query = store.queries.last()
        assertEquals(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, query.uri)
        assertTrue(query.selection, query.selection!!.contains(MediaStore.Images.Media.RELATIVE_PATH))
        assertTrue(query.args.toString(), "Pictures/Pix/" in query.args)
        assertTrue(query.sortOrder, query.sortOrder!!.startsWith("${MediaStore.Images.Media.DATE_ADDED} DESC"))
    }

    @Test
    fun `an empty album has none`() = runTest {
        assertNull(album().newest.value)
    }

    @Test
    fun `a change reads the album again, for a new photo and for the newest deleted`() = runTest {
        store.rows = listOf(11)
        val album = album()

        store.rows = listOf(12, 11)
        changed(12)
        assertEquals(photo(12), album.newest.value)

        store.rows = listOf(11)
        changed(12)
        assertEquals(photo(11), album.newest.value)

        store.rows = emptyList()
        changed(11)
        assertNull(album.newest.value)
    }

    @Test
    fun `a query that fails gives none, and the next change reads again`() = runTest {
        store.fails = true
        val album = album()
        assertNull(album.newest.value)

        store.fails = false
        store.rows = listOf(11)
        changed(11)

        assertEquals(photo(11), album.newest.value)
    }
}
